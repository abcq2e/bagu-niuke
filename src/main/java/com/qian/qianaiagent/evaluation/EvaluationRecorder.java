package com.qian.qianaiagent.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qian.qianaiagent.agent.trace.AgentTrace;
import com.qian.qianaiagent.rag.evaluation.RagasEvaluationResult;
import com.qian.qianaiagent.rag.evaluation.RagasEvaluator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import com.qian.qianaiagent.config.StorageProperties;
import com.qian.qianaiagent.util.ChatIdValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;

/**
 * 运行期评估记录器 —— 把两个评估模块（Agent 的 {@link RubricScorer}、RAG 的 {@link RagasEvaluator}）
 * 接到生产链路的统一异步入口。
 *
 * <p><b>定位：</b>回答结束后的"质量监控"，不阻塞、不改变用户请求。
 * 每次 Agent / 面试答题轮完整产出后，后台（scoringExecutor 池）异步算一次分，
 * 追加写入 data/evals/{chatId}.json（每会话一个 JSON 数组），失败只记日志。
 *
 * <p><b>线程隔离：</b>所有评估跑在评分专用池上，且用 {@link #gate}（Semaphore=2）
 * 节流并发 LLM 调用，避免评估挤占主流程或触发模型限流。
 */
@Slf4j
@Service
public class EvaluationRecorder {

    /** 单会话记录上限，超出裁最旧 */
    private static final int MAX_RECORDS_PER_CHAT = 500;

    /** answerExcerpt / traceText 的最大长度，防止文件无限膨胀 */
    private static final int EXCERPT_MAX = 2000;
    private static final int TRACE_MAX = 4000;

    @Resource(name = "scoringExecutor")
    private Executor scoringExecutor;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private RubricScorer rubricScorer;

    @Resource
    private RagasEvaluator ragasEvaluator;

    /** 并发节流：Rubric/Ragas 一次评估含多次 LLM/embedding 调用，限制同时进行的评估数 */
    private final Semaphore gate = new Semaphore(2);

    /** 每个会话一个写锁，防止并发 read-modify-write 丢记录 */
    private final ConcurrentHashMap<String, Object> chatLocks = new ConcurrentHashMap<>();

    /** 存储根目录配置（默认 root = user.dir，与改造前行为一致） */
    @Resource
    private StorageProperties storage;

    /** 评估结果目录 —— 由 {@link #init()} 从配置解析，不再硬编码 user.dir */
    private Path evalDir;

    @PostConstruct
    void init() {
        evalDir = storage.evalsPath();
        try {
            Files.createDirectories(evalDir);
        } catch (IOException e) {
            log.error("创建评估目录失败: {} —— 评估记录将无法落盘。"
                    + "容器部署时若该目录未挂 volume，会创建成 root 属主导致写入静默失败: {}",
                    evalDir, e.getMessage());
        }
    }

    // ============================================================
    // 提交入口（Agent 链路）
    // ============================================================

    /**
     * Agent 链路：回答完整产出后（emitter.onCompletion）异步做 Rubric 评分。
     *
     * <p>trace 未封口（endTime==null，超时/异常路径）或没有步骤时静默跳过，
     * 只评估"完整产出"的轮次。
     */
    public CompletableFuture<Void> submitAgentEval(String chatId, String query, AgentTrace trace) {
        if (trace == null || trace.getEndTime() == null
                || trace.getSteps() == null || trace.getSteps().isEmpty()) {
            log.info("⏭ 跳过 Agent 评估（trace 未封口或为空，超时/异常路径）: chatId={}", chatId);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                gate.acquire();
                try {
                    RubricScorer.RubricResult r = rubricScorer.score(query, trace);
                    String traceText = truncate(rubricScorer.serializeTrace(trace), TRACE_MAX);
                    appendRecord(chatId, EvalRecord.builder()
                            .type("agent")
                            .chatId(chatId)
                            .question(truncate(query, EXCERPT_MAX))
                            .answerExcerpt(truncate(r.getTotalScore() > 0 ? lastResultSummary(trace) : null, EXCERPT_MAX))
                            .rubric(r)
                            .traceText(traceText)
                            .build());
                } finally {
                    gate.release();
                }
            } catch (Exception e) {
                log.warn("Agent 评估失败（不影响主流程）: chatId={}, err={}", chatId, e.getMessage());
            }
        }, scoringExecutor);
    }

    // ============================================================
    // 提交入口（面试/RAG 链路）
    // ============================================================

    /**
     * RAG 链路：面试点评轮结束后异步做 RAGAS 评估。
     *
     * <p>referenceAnswer 传 null（生产无 ground-truth），RagasEvaluator 内部会自动跳过
     * 需要参考答案的 contextRecall，算出 contextPrecision/faithfulness/answerRelevance。
     */
    public CompletableFuture<Void> submitRagEval(String chatId, String question,
                                                 List<Document> documents, String generatedAnswer) {
        if (question == null || question.isBlank()
                || generatedAnswer == null || generatedAnswer.isBlank()
                || documents == null || documents.isEmpty()) {
            log.info("⏭ 跳过 RAG 评估（题干/回答/检索文档缺失）: chatId={}", chatId);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                gate.acquire();
                try {
                    RagasEvaluationResult r = ragasEvaluator.evaluate(
                            question, documents, generatedAnswer, null);
                    appendRecord(chatId, EvalRecord.builder()
                            .type("rag")
                            .chatId(chatId)
                            .question(truncate(question, EXCERPT_MAX))
                            .answerExcerpt(truncate(generatedAnswer, EXCERPT_MAX))
                            .ragas(r)
                            .build());
                } finally {
                    gate.release();
                }
            } catch (Exception e) {
                log.warn("RAG 评估失败（不影响主流程）: chatId={}, err={}", chatId, e.getMessage());
            }
        }, scoringExecutor);
    }

    // ============================================================
    // 读取（只读查询接口用）
    // ============================================================

    /**
     * 返回某会话最近的评估记录，新→旧。文件缺失或读取失败返回空列表，绝不让上层 500。
     */
    public List<EvalRecord> listRecent(String chatId, int limit) {
        try {
            String name = sanitize(chatId);
            Path path = evalDir.resolve(name + ".json");
            if (!Files.exists(path)) {
                return List.of();
            }
            List<EvalRecord> all = objectMapper.readValue(
                    path.toFile(), new TypeReference<List<EvalRecord>>() {});
            if (all == null || all.isEmpty()) {
                return List.of();
            }
            List<EvalRecord> recent = new ArrayList<>(all);
            Collections.reverse(recent);
            return recent.size() > limit ? new ArrayList<>(recent.subList(0, limit)) : recent;
        } catch (Exception e) {
            log.warn("读取评估记录失败: chatId={}, err={}", chatId, e.getMessage());
            return List.of();
        }
    }

    // ============================================================
    // 汇总（跨会话）
    // ============================================================

    /**
     * 扫描 data/evals/ 下所有会话文件，产出跨会话汇总。
     *
     * <p>单会话的 {@link #listRecent} 只能看一个 chatId；这个方法是"整体情况怎么样"的答案。
     * 损坏的文件被跳过而不是让整个汇总失败。
     */
    public EvalSummary summarize() {
        return summarize(chatId -> true);
    }

    /**
     * 同上，但只统计通过 {@code chatIdFilter} 的会话。
     *
     * <p>🔴 接口层必须传当前用户的会话集合：不传过滤器的版本会扫描整个
     * {@code data/evals} 目录，等于把所有用户的评分聚合后返回给任何人。
     *
     * @param chatIdFilter 会话 ID 过滤器；传入的 chatId 保证非 null
     */
    public EvalSummary summarize(Predicate<String> chatIdFilter) {
        List<EvalRecord> all = readAllRecords().stream()
                .filter(r -> r.getChatId() != null && chatIdFilter.test(r.getChatId()))
                .toList();
        List<EvalRecord> agentRecords = all.stream().filter(r -> "agent".equals(r.getType())).toList();
        List<EvalRecord> ragRecords = all.stream().filter(r -> "rag".equals(r.getType())).toList();
        TypeSummary agent = TypeSummary.builder()
                .count(agentRecords.size())
                .avgRubricTotal(avg(agentRecords,
                        r -> r.getRubric() == null ? null : (double) r.getRubric().getTotalScore()))
                .build();
        TypeSummary rag = TypeSummary.builder()
                .count(ragRecords.size())
                .avgContextPrecision(avg(ragRecords,
                        r -> r.getRagas() == null ? null : r.getRagas().getContextPrecision()))
                .avgFaithfulness(avg(ragRecords,
                        r -> r.getRagas() == null ? null : r.getRagas().getFaithfulness()))
                .avgAnswerRelevance(avg(ragRecords,
                        r -> r.getRagas() == null ? null : r.getRagas().getAnswerRelevance()))
                .build();
        return EvalSummary.builder()
                .totalRecords(all.size())
                .sessions((int) all.stream()
                        .map(EvalRecord::getChatId)
                        .filter(Objects::nonNull)
                        .distinct().count())
                .earliest(all.stream().map(EvalRecord::getTimestamp)
                        .filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null))
                .latest(all.stream().map(EvalRecord::getTimestamp)
                        .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null))
                .agent(agent)
                .rag(rag)
                .build();
    }

    /** 扫描 evalDir 下所有 .json，解析并展平。损坏文件跳过。 */
    private List<EvalRecord> readAllRecords() {
        List<EvalRecord> all = new ArrayList<>();
        if (!Files.isDirectory(evalDir)) {
            return all;
        }
        try (var stream = Files.list(evalDir)) {
            for (Path p : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                try {
                    List<EvalRecord> records = objectMapper.readValue(
                            p.toFile(), new TypeReference<List<EvalRecord>>() {});
                    if (records != null) {
                        all.addAll(records);
                    }
                } catch (Exception e) {
                    log.warn("跳过损坏的评估文件: {}, err={}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描评估目录失败: {}", e.getMessage());
        }
        return all;
    }

    /** 对非空的取值求平均；全为 null 时返回 null（而非 0，避免误导） */
    private Double avg(List<EvalRecord> records, java.util.function.Function<EvalRecord, Double> extractor) {
        List<Double> values = records.stream().map(extractor).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // ============================================================
    // 内部：追加写入（原子）
    // ============================================================

    private void appendRecord(String chatId, EvalRecord record) {
        String name = sanitize(chatId);
        Path path = evalDir.resolve(name + ".json");
        Object lock = chatLocks.computeIfAbsent(name, k -> new Object());
        synchronized (lock) {
            try {
                List<EvalRecord> all = new ArrayList<>();
                if (Files.exists(path)) {
                    List<EvalRecord> existed = objectMapper.readValue(
                            path.toFile(), new TypeReference<List<EvalRecord>>() {});
                    if (existed != null) {
                        all.addAll(existed);
                    }
                }
                all.add(record);
                if (all.size() > MAX_RECORDS_PER_CHAT) {
                    all = new ArrayList<>(all.subList(all.size() - MAX_RECORDS_PER_CHAT, all.size()));
                }
                Path tmp = evalDir.resolve(name + ".json.tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), all);
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                log.info("📊 评估已落盘: chatId={}, type={}, 该会话累计 {} 条 → {}",
                        chatId, record.getType(), all.size(), path.getFileName());
            } catch (Exception e) {
                log.warn("写入评估记录失败: chatId={}, err={}", chatId, e.getMessage());
            }
        }
    }

    /** 取 trace 最后一步的结果摘要（作为 agent 回答摘录）；空 trace 返回 null */
    private String lastResultSummary(AgentTrace trace) {
        if (trace.getSteps().isEmpty()) {
            return null;
        }
        return trace.getSteps().get(trace.getSteps().size() - 1).getResultSummary();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** 防路径穿越：chatId 只保留安全字符（与复习游标共用同一份实现，避免规则漂移） */
    private String sanitize(String chatId) {
        return ChatIdValidator.sanitize(chatId);
    }

    // ============================================================
    // 汇总数据模型
    // ============================================================

    /** 跨会话评估汇总 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvalSummary {
        /** 全部记录数 */
        private int totalRecords;

        /** 涉及的会话数 */
        private int sessions;

        /** 最早/最近的评估时间 */
        private LocalDateTime earliest;
        private LocalDateTime latest;

        /** Agent 链路汇总 */
        private TypeSummary agent;

        /** RAG 链路汇总 */
        private TypeSummary rag;
    }

    /** 单一链路的汇总。未产出的指标为 null（而非 0），避免"没数据"被读成"分数很烂" */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeSummary {
        private int count;

        /** 仅 agent：Rubric 总分均值（0-100） */
        private Double avgRubricTotal;

        /** 仅 rag：以下三项均为 [0,1] */
        private Double avgContextPrecision;   //上下文准确度
        private Double avgFaithfulness;
        private Double avgAnswerRelevance;
    }
}
