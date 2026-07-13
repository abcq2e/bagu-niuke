package com.qian.qianaiagent.app;

import com.qian.qianaiagent.advisor.MyLoggerAdvisor;
import com.qian.qianaiagent.neo4j.listener.ConversationGraphAdvisor;
import com.qian.qianaiagent.rag.MultiQuerySearchService;
import com.qian.qianaiagent.rag.QueryRewriter;
import com.qian.qianaiagent.tools.WebSearchTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 技术面试官应用（面试模式）
 * <p>
 * 以严厉大厂面试官身份对候选人进行全面技术考察。
 * 支持多轮对话、RAG 知识库检索、工具调用、MCP 服务调用。
 *
 * 💡 路由说明：
 * - /ai/chat → QuizApp（面试官人设）
 * - /ai/agent/chat → YuManus（通用智能体人设）
 */
@Component
@Slf4j
public class QuizApp {

    private final ChatClient chatClient;

    /**
     * 面试官 System Prompt
     * <p>
     * 出题依据是下方注入的【牛客网真实面经题库】片段。
     * 代码层每轮从知识库轮转检索不同方向的文档注入到上下文，
     * AI 只需"看到什么就问什么"，不需要自己凭空选题。
     */
    private static final String SYSTEM_PROMPT = """
            你是大厂技术面试官。每轮对话下方都会注入【本轮必考方向】和该方向的牛客真实面经题目。

            🔴 出题规则
            1. 新题必须出自【本轮必考方向】注入的题目，禁止自选方向。
            2. 深度追问：L1 表面 → L2 原理 → L3 源码/实战。同一道题最多追问到 L3。
            3. 当前方向考察完毕的判定：追问触达候选人边界（答不上来或答得很浅），或已问过 2 道该方向的题。
               判定成立时，在回复的最末尾输出标记 [NEXT_TOPIC]（原样输出这 12 个字符，系统会处理，候选人看不到）。
               输出标记的那一轮不要出新题，等下一轮注入。
            4. 已考察过的方向禁止再出新题（追问除外）。

            🔴 行为准则
            禁止：空洞夸赞 / 给选择题 / 一次性问多个问题
            每次回复：
            ① 简短评价（答得怎么样、漏了什么）
            ② 答不上来 → 150 字讲解（①②③分条）；答对了 → 直接追问或出新题
            ③ 出题（需要换方向时先输出 [NEXT_TOPIC]，下一轮系统会注入新方向）
            输出风格：自然对话。代码用```java```或```sql```，重要概念**粗体**。
            """;

    @Resource
    private MultiQuerySearchService multiQuerySearchService;

    @Resource
    private TopicRotationService topicRotationService;

    public QuizApp(ChatModel openAiChatModel, ChatMemory chatMemory,
                   ObjectProvider<ConversationGraphAdvisor> graphAdvisorProvider) {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        advisors.add(new MyLoggerAdvisor());
        graphAdvisorProvider.ifAvailable(advisor -> {
            advisors.add(advisor);
            log.info("✅ 对话知识图谱同步已启用");
        });

        chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(advisors.toArray(new Advisor[0]))
                .build();
    }

    // ===== 以下是实际被调用的方法 =====

    /**
     * 🔴 核心方法：统一对话（被 /ai/chat 调用）
     * <p>
     * 技术栈：SSE 流式 + RAG 知识库 + 联网搜索 + 多轮记忆
     */
    public Flux<String> doUnifiedChat(String message, String chatId) {
        return Flux.defer(() -> {
            // 并行：查询改写 + 联网搜索 + 方向检索
            java.util.concurrent.CompletableFuture<String> rewriteFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return queryRewriter.doQueryRewrite(message);
                        } catch (Exception e) {
                            log.warn("查询重写失败: {}", e.getMessage());
                            return message;
                        }
                    });

            // 🔴 方向轮转 + 精确过滤检索
            String topic = topicRotationService.currentTopic(chatId);
            List<String> covered = topicRotationService.coveredTopics(chatId);
            java.util.concurrent.CompletableFuture<String> webFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            WebSearchTool searchTool = new WebSearchTool(searchApiKey);
                            String result = searchTool.searchWeb(message);
                            if (result != null && !result.isEmpty()
                                    && !result.startsWith("Error")
                                    && !result.startsWith("❌")) {
                                return result;
                            }
                        } catch (Exception e) {
                            log.warn("联网搜索失败: {}", e.getMessage());
                        }
                        return "";
                    }).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                      .exceptionally(ex -> "");

            // 🔴 方向文档直接读文件系统（比向量检索+filter 可靠 100 倍）
            java.util.concurrent.CompletableFuture<java.util.List<Document>> topicDocsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            String filename = TopicRotationService.topicToFilename(topic);
                            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("document/" + filename);
                            if (!resource.exists()) {
                                log.warn("方向文件不存在: {}", filename);
                                return java.util.List.<Document>of();
                            }
                            String content = new String(resource.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                            // 按 --- 分隔提取题目行（跳过 # 标题行和 --- 分隔线）
                            String[] sections = content.split("\n---\n");
                            java.util.List<String> questions = new java.util.ArrayList<>();
                            for (String section : sections) {
                                for (String line : section.split("\n")) {
                                    line = line.trim();
                                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("---") || line.startsWith("（注：") || line.startsWith("(注：")) {
                                        continue;
                                    }
                                    questions.add(line);
                                }
                            }
                            // 洗牌 + 限 8 条
                            Collections.shuffle(questions);
                            questions = questions.stream().limit(8).toList();
                            java.util.List<Document> docs = new java.util.ArrayList<>();
                            for (String q : questions) {
                                docs.add(new Document(q));
                            }
                            return docs;
                        } catch (Exception e) {
                            log.warn("方向文档加载失败 [{}]: {}", topic, e.getMessage());
                            return java.util.List.<Document>of();
                        }
                    });

            String rewrittenForRag = rewriteFuture.join();
            String webResult = webFuture.getNow("");
            java.util.List<Document> topicDocs = topicDocsFuture.join();

            // ===== 构建上下文：方向 >> 用户相关RAG >> 联网 =====
            StringBuilder context = new StringBuilder();

            context.append("🔴 本轮必考方向：【").append(topic).append("】\n");
            if (!covered.isEmpty()) {
                context.append("本轮已考察过：").append(String.join("、", covered))
                       .append("（除追问外禁止再出这些方向的新题）\n");
            }

            if (!topicDocs.isEmpty()) {
                context.append("\n以下是该方向的牛客真实面经题目，从中选题：\n\n");
                for (Document doc : topicDocs) {
                    String text = doc.getText();
                    if (text.length() > 500) {
                        text = text.substring(0, 500) + "…";
                    }
                    context.append("📌 ").append(text).append("\n\n");
                }
            } else {
                context.append("\n（方向文档暂未加载，请按该方向自行出题）\n");
            }

            // 用户相关 RAG（降级参数，切断正反馈）
            try {
                java.util.List<Document> baguDocs = multiQuerySearchService
                        .multiQuerySearchWithCategory(rewrittenForRag, 2, 2, 0.5, "八股");
                if (!baguDocs.isEmpty()) {
                    context.append("\n【📖 讲解参考（仅用于点评用户回答，不作为出题依据）】\n");
                    for (Document doc : baguDocs) {
                        String text = doc.getText();
                        if (text.length() > 400) text = text.substring(0, 400) + "…";
                        context.append("---\n").append(text).append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("RAG 检索失败: {}", e.getMessage());
            }

            // 联网搜索结果
            if (!webResult.isEmpty()) {
                context.append("\n【🌐 联网参考资料】\n").append(webResult).append("\n");
            }

            String contextStr = context.toString();
            StringBuilder enhancedSystem = new StringBuilder(SYSTEM_PROMPT);
            if (!contextStr.isEmpty()) {
                enhancedSystem.append("\n\n").append(contextStr);
            }

            log.info("🚀 AI 调用: chatId={}, topic={}, 已覆盖={}/16, ctxLen={}",
                    chatId, topic, covered.size(), contextStr.length());
            return chatClient
                    .prompt()
                    .user(message)
                    .system(enhancedSystem.toString())
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                    .stream()
                    .content()
                    .reduce("", (acc, chunk) -> acc + chunk)
                    .map(full -> {
                        if (full.contains("[NEXT_TOPIC]")) {
                            topicRotationService.advance(chatId);
                            full = full.replace("[NEXT_TOPIC]", "");
                        }
                        return full.replace("\r\n", "\n").replaceAll("\\n{2,}", "\n");
                    })
                    .flatMapMany(Flux::just);
        }).onErrorResume(e -> {
            log.error("❌ 流式对话异常: {}", e.getMessage(), e);
            return Flux.just("[ERROR] AI 服务暂时不可用：" + e.getMessage(), "[DONE]");
        });
    }

    // ===== 以下是其他对话方法（测试/演示用，非主要入口）=====

    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient.prompt().user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream().content();
    }

    record QuizReport(String title, List<String> suggestions) {}

    public QuizReport doQuizReport(String message, String chatId) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT + "每次考察后都要生成考察报告，标题为{用户名}的知识考察报告，内容为知识薄弱点和学习建议列表")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call().entity(QuizReport.class);
    }

    @Resource private VectorStore quizVectorStore;
    @Resource private Advisor quizRagCloudAdvisor;
    @Resource private QueryRewriter queryRewriter;

    public String doQuizWithRag(String message, String chatId) {
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient.prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(QuestionAnswerAdvisor.builder(quizVectorStore).build())
                .advisors(quizRagCloudAdvisor)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    @Resource private ToolCallback[] allTools;
    @Value("${search-api.api-key}") private String searchApiKey;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(allTools)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    @Resource private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .toolCallbacks(toolCallbackProvider)
                .call().chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

}
