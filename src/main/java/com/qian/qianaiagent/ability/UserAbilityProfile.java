package com.qian.qianaiagent.ability;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 用户能力画像 —— 每个 chatId 维护一份，记录各方向掌握度和薄弱点。
 * <p>
 * 评分规则（V3 重设计）：
 * - 不再使用 EWMA 平滑公式制造"假精确"数字
 * - 原始评分 1-5 分全部保留，简单平均算出等级
 * - 掌握度（0-100）= 平均分 × 20（仅用于进度条可视化）
 * - 核心展示是等级标签，不是那个 0-100 数字
 * <p>
 * 等级划分：
 * - 1.0-1.4：薄弱（红色）
 * - 1.5-2.4：待加强（橙色）
 * - 2.5-3.4：掌握（黄色）
 * - 3.5-4.4：良好（绿色）
 * - 4.5-5.0：精通（蓝色）
 * <p>
 * 🔴 [P3] 支持 Redis 序列化（实现 Serializable）
 * 🔴 [P3] 薄弱点按频率排序，保留 top 8
 */
//数据模型
@Data
public class UserAbilityProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private String chatId;

    /** 每个方向的掌握度 */
    private Map<String, TopicScore> topicScores = new ConcurrentHashMap<>();

    /** 全局薄弱知识点（跨方向） */
    private List<String> weakPoints = new ArrayList<>();

    /** topic → 已覆盖的知识点维度集合（持久化，跨重启保留） */
    private Map<String, Set<String>> topicCoveredDimensions = new HashMap<>();

    /** 已提取的提问指纹集合（跨会话持久化，用于题目去重） */
    private Set<String> askedQuestionFingerprints = new HashSet<>();

    /** 已考知识点稳定 ID（硬选题主去重） */
    private Set<String> askedPointIds = new HashSet<>();

    /** 🔴 [Hotfix-DETAIL持久化] 方向 → 已考细分考点名称集合（跨重启保留） */
    private Map<String, Set<String>> topicDetailedRecords = new HashMap<>();

    /** 最后更新时间 */
    private long lastUpdatedAt;

    public UserAbilityProfile() {
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    public UserAbilityProfile(String chatId) {
        this.chatId = chatId;
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    /**
     * 更新指定方向的掌握度分数（带薄弱点和原题）
     */
    public void updateTopicScore(String topic, int newScore, List<String> newWeakPoints, String userAnswer, Map<String, String> weakPointDetails, String weakPointQuestion) {
        TopicScore ts = topicScores.computeIfAbsent(topic, TopicScore::new);
        ts.update(newScore, newWeakPoints, userAnswer, weakPointDetails, weakPointQuestion);
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    /** 向后兼容：无原题 */
    public void updateTopicScore(String topic, int newScore, List<String> newWeakPoints, String userAnswer, Map<String, String> weakPointDetails) {
        updateTopicScore(topic, newScore, newWeakPoints, userAnswer, weakPointDetails, null);
    }

    public void updateTopicScore(String topic, int newScore, List<String> newWeakPoints, String userAnswer) {
        updateTopicScore(topic, newScore, newWeakPoints, userAnswer, null, null);
    }

    public void updateTopicScore(String topic, int newScore, List<String> newWeakPoints) {
        updateTopicScore(topic, newScore, newWeakPoints, null, null, null);
    }

    /**
     * 🔴 [Bug修复-V2] 将路由过来的弱点评添加到指定方向下（不修改评分历史）。
     * <p>
     * 当评分 AI 给出的弱点评属于另一个方向时，通过 {@code routeWeakPoints} 路由到此方向，
     * 仅在目标方向的 TopicScore 中添加弱点评记录，不影响目标方向的评分。
     *
     * @param topic              目标方向名
     * @param weakPoints         要添加的弱点评列表
     * @param weakPointDetails   AI 的详细分析映射（可选）
     * @param weakPointQuestion  用户此轮的原始题目（可选）
     * @param userAnswer         用户回答摘要（可选）
     */
    public void addRoutedWeakPoints(String topic, List<String> weakPoints,
                                     Map<String, String> weakPointDetails,
                                     String weakPointQuestion, String userAnswer) {
        if (topic == null || weakPoints == null || weakPoints.isEmpty()) return;
        TopicScore ts = topicScores.computeIfAbsent(topic, TopicScore::new);
        synchronized (ts) {
            for (String wp : weakPoints) {
                if (wp == null || wp.isBlank()) continue;
                // 添加到 weakPoints（去重）
                if (ts.getWeakPoints() == null || !ts.getWeakPoints().contains(wp)) {
                    if (ts.getWeakPoints() == null) {
                        ts.setWeakPoints(new java.util.ArrayList<>());
                    }
                    ts.getWeakPoints().add(wp);
                    // 频率统计兼容
                    if (ts.getWeakPointFreq() != null) {
                        ts.getWeakPointFreq().merge(wp, 1, Integer::sum);
                    }
                }
                // 迁移详细分析
                if (weakPointDetails != null && weakPointDetails.containsKey(wp)) {
                    if (ts.getWeakPointDetails() == null) {
                        ts.setWeakPointDetails(new java.util.LinkedHashMap<>());
                    }
                    ts.getWeakPointDetails().putIfAbsent(wp, weakPointDetails.get(wp));
                }
                // 迁移原题
                if (weakPointQuestion != null && !weakPointQuestion.isBlank()) {
                    ts.recordWrongQuestion(wp, weakPointQuestion);
                }
                // 迁移用户回答摘要
                if (userAnswer != null && !userAnswer.isBlank()) {
                    if (ts.getWeakPointAnswers() == null) {
                        ts.setWeakPointAnswers(new java.util.LinkedHashMap<>());
                    }
                    ts.getWeakPointAnswers()
                            .computeIfAbsent(wp, k -> new java.util.ArrayList<>())
                            .add(userAnswer);
                }
            }
            // 重置频率排序
            if (ts.getWeakPoints() != null && ts.getWeakPointFreq() != null) {
                ts.setWeakPoints(ts.getWeakPointFreq().entrySet().stream()
                        .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(8)
                        .map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toList()));
            }
        }
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    /**
     * 获取指定方向的掌握度（0-100），0 表示无评分
     */
    public int getTopicScore(String topic) {
        TopicScore ts = topicScores.get(topic);
        return ts != null ? ts.getScore() : 0;
    }

    /**
     * 获取所有已评分的方向
     */
    @JsonIgnore
    public List<TopicScore> getAllTopicScores() {
        return List.copyOf(topicScores.values());
    }

    /**
     * 获取指定方向的薄弱点列表
     */
    public List<String> getWeakPoints(String topic) {
        TopicScore ts = topicScores.get(topic);
        return ts != null ? List.copyOf(ts.getWeakPoints()) : List.of();
    }

    /**
     * 获取全局掌握度评分（0-100），0 表示无数据
     */
    @JsonIgnore
    public int getOverallScore() {
        if (topicScores.isEmpty()) return 0;
        return (int) topicScores.values().stream()
                .mapToInt(TopicScore::getScore)
                .average()
                .orElse(0);
    }

    /**
     * 记录某个方向已覆盖的知识点维度（持久化）
     */
    public void addCoveredDimension(String topic, String dimension) {
        topicCoveredDimensions.computeIfAbsent(topic, k -> new HashSet<>()).add(dimension);
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    /**
     * 🔴 [全覆盖] 清除某个方向所有已覆盖的维度记录（旧数据迁移用）。
     * 用于启动时将旧"全覆盖"降级为"已出 1 题未饱和"。
     */
    public void clearCoveredDimensions(String topic) {
        if (topicCoveredDimensions != null) {
            topicCoveredDimensions.remove(topic);
        }
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    /**
     * 获取某个方向已覆盖的知识点维度集合
     */
    public Set<String> getCoveredDimensions(String topic) {
        return topicCoveredDimensions.getOrDefault(topic, Set.of());
    }

    /**
     * 单个方向的掌握度
     * <p>
     * 不再用 EWMA 平滑，改用原始评分历史 + 简单平均。
     */
    @Data
    public static class TopicScore implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 方向名 */
        private String topic;

        /** 🔴 [V3] 原始评分历史（1-5 分），每次作答追加一条 */
        private List<Integer> scoreHistory = new ArrayList<>();

        /** 该方向具体薄弱点（简短名称，如"Redis持久化RDB与AOF区别"） */
        private List<String> weakPoints = new ArrayList<>();

        /** 🔴 薄弱点 → 用户回答摘要列表（点击薄弱点可查看自己的回答） */
        private Map<String, List<String>> weakPointAnswers = new LinkedHashMap<>();

        /** 🔴 薄弱点 → 详细分析文本（AI评分的详细反馈，白板弹窗展示用） */
        private Map<String, String> weakPointDetails = new LinkedHashMap<>();

        /** 🔴 薄弱点 → 当时没答上的原题 */
        private Map<String, String> weakPointQuestions = new LinkedHashMap<>();

        /** 🔴 错题知识点 → 原题（错题本核心数据：只记录答错/不会的题，知识点名称→原题全文） */
        private Map<String, String> wrongQuestions = new LinkedHashMap<>();

        /** 🔴 [P3] 薄弱点出现频率统计（序列化忽略，程序内使用） */
        @JsonIgnore
        private transient final Map<String, Integer> weakPointFreq = new LinkedHashMap<>();

        public TopicScore() {
        }

        TopicScore(String topic) {
            this.topic = topic;
        }

        // ===== 以下 getter 由 @Data 生成，无需手动写 =====

        /**
         * 🔴 [V3] 掌握度 0-100 = 原始评分平均 × 20
         * <p>
         * 仅用于进度条可视化，<b>不是核心展示指标</b>。
         * 核心展示应该是 {@link #getScoreLevel()} 等级标签。
         * <p>
         * 例：评分 [2, 3, 2] → 平均 2.33 → 掌握度 47/100
         * 对应等级 "待加强"（2.0-2.9）
         */
        public int getScore() {
            List<Integer> history = getScoreHistory();
            if (history == null || history.isEmpty()) return 0;
            double avg = history.stream().mapToInt(Integer::intValue).average().orElse(0);
            return (int) Math.round(avg * 20);
        }

        /**
         * 🔴 [V3] 获取 1-5 原始分平均（精确到 0.01）
         */
        @JsonIgnore
        public double getAverageScore() {
            List<Integer> history = getScoreHistory();
            if (history == null || history.isEmpty()) return 0;
            return Math.round(history.stream().mapToInt(Integer::intValue).average().orElse(0) * 100.0) / 100.0;
        }

        /**
         * 🔴 [V3] 获取评分等级标签
         * <p>
         * 这是核心展示指标，替代原先的"41分"这种无意义数字。
         */
        @JsonIgnore
        public String getScoreLevel() {
            double avg = getAverageScore();
            if (avg >= 4.5) return "精通";
            if (avg >= 3.5) return "良好";
            if (avg >= 2.5) return "掌握";
            if (avg >= 1.5) return "待加强";
            if (avg > 0) return "薄弱";
            return "未评分";
        }

        /**
         * 🔴 [V3] 获取等级对应的表情符号
         */
        @JsonIgnore
        public String getScoreEmoji() {
            double avg = getAverageScore();
            if (avg >= 4.5) return "🔵";
            if (avg >= 3.5) return "🟢";
            if (avg >= 2.5) return "🟡";
            if (avg >= 1.5) return "🟠";
            if (avg > 0) return "🔴";
            return "⚪";
        }

        /**
         * 🔴 [V3] 获取最近 N 次评分
         */
        @JsonIgnore
        public List<Integer> getLastScores(int n) {
            List<Integer> history = getScoreHistory();
            if (history == null || history.isEmpty()) return List.of();
            int size = history.size();
            return history.subList(Math.max(0, size - n), size);
        }

        /**
         * 被考过多少题 = 评分历史长度
         */
        public int getQuestionCount() {
            List<Integer> history = getScoreHistory();
            return history != null ? history.size() : 0;
        }

        /**
         * 🔴 [V3] 更新评分：追加一条原始分 + 更新薄弱点 + 关联用户回答
         * <p>
         * 不再做 EWMA 平滑，只存原始分。
         * 掌握度 0-100 = average × 20，由 getScore() 计算。
         */
        synchronized void update(int newScore, List<String> newWeakPoints) {
            update(newScore, newWeakPoints, null, null, null);
        }

        synchronized void update(int newScore, List<String> newWeakPoints, String userAnswer) {
            update(newScore, newWeakPoints, userAnswer, null, null);
        }

        synchronized void update(int newScore, List<String> newWeakPoints, String userAnswer, Map<String, String> weakPointDetails) {
            update(newScore, newWeakPoints, userAnswer, weakPointDetails, null);
        }

        /**
         * 🔴 更新评分（带薄弱点详细分析和原题）
         */
        synchronized void update(int newScore, List<String> newWeakPoints, String userAnswer, Map<String, String> weakPointDetails, String weakPointQuestion) {
            if (scoreHistory == null) {
                scoreHistory = new ArrayList<>();
            }
            scoreHistory.add(Math.max(1, Math.min(5, newScore))); // clamp 1-5

            // 关联薄弱点与用户回答（截取前 200 字做摘要）
            String answerExcerpt = userAnswer != null && !userAnswer.isBlank()
                    ? (userAnswer.length() > 200 ? userAnswer.substring(0, 200) + "…" : userAnswer)
                    : "";

            if (newWeakPoints != null && !answerExcerpt.isEmpty()) {
                for (String wp : newWeakPoints) {
                    if (weakPointAnswers == null) {
                        weakPointAnswers = new LinkedHashMap<>();
                    }
                    weakPointAnswers.computeIfAbsent(wp, k -> new ArrayList<>()).add(answerExcerpt);
                }
            }

            // 存储薄弱点详细分析
            if (weakPointDetails != null && !weakPointDetails.isEmpty()) {
                if (this.weakPointDetails == null) {
                    this.weakPointDetails = new LinkedHashMap<>();
                }
                this.weakPointDetails.putAll(weakPointDetails);
            }

            // 存储薄弱点对应的原题
            if (weakPointQuestion != null && !weakPointQuestion.isBlank() && newWeakPoints != null) {
                if (this.weakPointQuestions == null) {
                    this.weakPointQuestions = new LinkedHashMap<>();
                }
                for (String wp : newWeakPoints) {
                    this.weakPointQuestions.putIfAbsent(wp, weakPointQuestion);
                }
            }

            // 薄弱点按频率排序，保留 top 8
            if (newWeakPoints != null) {
                for (String wp : newWeakPoints) {
                    weakPointFreq.merge(wp, 1, Integer::sum);
                }
                this.weakPoints = weakPointFreq.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .limit(8)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }
        }

        /**
         * 🔴 [P3] 反序列化后恢复 weakPointFreq
         */
        @JsonIgnore
        public void rebuildFreqAfterDeserialization() {
            if (weakPoints != null) {
                for (String wp : weakPoints) {
                    weakPointFreq.merge(wp, 1, Integer::sum);
                }
            }
        }

        /**
         * 🔴 错题本：记录一道错题的知识点→原题映射
         * 答得差（评分<4）时调用，每个知识点只记一次原题。
         */
        public void recordWrongQuestion(String knowledgePoint, String question) {
            if (wrongQuestions == null) {
                wrongQuestions = new LinkedHashMap<>();
            }
            if (knowledgePoint != null && !knowledgePoint.isBlank()
                    && question != null && !question.isBlank()) {
                // 同一道题只记录一次，不按知识点拆分多个条目
                if (wrongQuestions.containsValue(question)) return;
                wrongQuestions.putIfAbsent(knowledgePoint, question);
            }
        }

        /**
         * 错题复习：从错题本中移除指定知识点。
         */
        public void removeWrongQuestion(String knowledgePoint) {
            if (wrongQuestions != null) {
                wrongQuestions.remove(knowledgePoint);
            }
        }

        /**
         * 按原题文本删除（复习答对时优先用此方法；兼容入库时截断到 200 字）。
         */
        public boolean removeWrongQuestionByText(String questionText) {
            if (wrongQuestions == null || questionText == null || questionText.isBlank()) {
                return false;
            }
            String target = questionText.trim();
            String keyToRemove = null;
            for (Map.Entry<String, String> e : wrongQuestions.entrySet()) {
                String stored = e.getValue();
                if (stored == null) continue;
                if (target.equals(stored) || stored.startsWith(target) || target.startsWith(stored.replace("…", ""))) {
                    keyToRemove = e.getKey();
                    break;
                }
            }
            if (keyToRemove == null) return false;
            wrongQuestions.remove(keyToRemove);
            return true;
        }

        /**
         * 获取错题知识点→原题映射
         */
        public Map<String, String> getWrongQuestions() {
            return wrongQuestions != null ? wrongQuestions : Map.of();
        }
    }

    /**
     * 🔴 [终版-双链路] 评分结果（Jackson 解析用）
     * <p>
     * weakPoints 支持管道符格式：知识点|维度1,维度2|置信度
     * 解析后维度信息存储到 weakPointDimensions / weakPointConfidence。
     */
    @Data
    public static class ScoreResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private int score = 3;
        private String topic = "";
        private List<String> weakPoints = List.of();
        private Map<String, String> weakPointDetails = new LinkedHashMap<>();
        private String comment = "";

        /** 🔴 [终版] 每条弱点评的维度映射（key=知识点名, value=维度列表） */
        private Map<String, List<String>> weakPointDimensions = new LinkedHashMap<>();

        /** 🔴 [终版] 每条弱点评的自评置信度（key=知识点名, value=high/medium/low） */
        private Map<String, String> weakPointConfidence = new LinkedHashMap<>();

        /** 🔴 [Bug修复] AI原始输出的话题方向（在 topic 被 override 前的原始值，用于检测AI方向混乱） */
        private String originalTopic;

        /**
         * 解析管道符格式的 weakPoints，填充维度/置信度映射。
         * 格式：知识点|维度1,维度2|置信度
         * 向后兼容：无管道符的旧格式保持不变。
         */
        public void parsePipeFormat() {
            if (weakPoints == null || weakPoints.isEmpty()) return;
            List<String> cleaned = new java.util.ArrayList<>();
            for (String item : weakPoints) {
                String[] parts = item.split("\\|", 3);
                String name = parts[0].trim();
                if (name.isEmpty()) continue;
                cleaned.add(name);

                if (parts.length >= 2) {
                    String dimStr = parts[1].trim();
                    if (!dimStr.isEmpty() && !"无".equals(dimStr)) {
                        List<String> dims = java.util.Arrays.stream(dimStr.split(","))
                                .map(String::trim)
                                .filter(d -> !d.isEmpty())
                                .toList();
                        if (!dims.isEmpty()) {
                            weakPointDimensions.put(name, dims);
                        }
                    }
                }

                if (parts.length >= 3) {
                    String conf = parts[2].trim().toLowerCase();
                    if (conf.equals("high") || conf.equals("medium") || conf.equals("low")) {
                        weakPointConfidence.put(name, conf);
                    }
                }
            }
            this.weakPoints = cleaned;
        }
    }
}
