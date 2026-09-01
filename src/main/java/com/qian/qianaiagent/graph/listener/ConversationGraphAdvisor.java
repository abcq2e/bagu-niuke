package com.qian.qianaiagent.graph.listener;

import com.qian.qianaiagent.graph.service.ConversationAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.Executor;

/**
 * 对话图谱同步 Advisor
 * <p>
 * 拦截每一轮对话的请求和响应，实时同步到 Neo4j 图数据库：
 * - 创建/更新 Conversation 节点
 * - 创建 Message 节点（用户消息 + AI 回复）
 * - 建立 REPLIES_TO（回复关系）和 NEXT（顺序关系）
 * - 提取话题关键词，归类到 Topic 节点
 * <p>
 * 仅在 Neo4j Driver 可用时启用，无需 Neo4j 时自动降级不生效
 * <p>
 * 这是对话知识图谱分析的数据入口
 *
 * @author yupi
 */
@Component
@ConditionalOnClass(Driver.class)
@Slf4j
public class ConversationGraphAdvisor implements CallAdvisor, StreamAdvisor {

    private final ConversationAnalysisService analysisService;

    /** Neo4j 写入专用线程池（异步、不阻塞 LLM 调用） */
    private final Executor neo4jExecutor;

    public ConversationGraphAdvisor(ConversationAnalysisService analysisService) {
        this.analysisService = analysisService;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("neo4j-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();
        this.neo4jExecutor = executor;
    }

    @Override
    public String getName() {
        return "conversationGraphAdvisor";
    }

    @Override
    public int getOrder() {
        // 在 MessageChatMemoryAdvisor（order=0）之后执行
        return 1;
    }

    /**
     * 同步对话：拦截请求，记录用户消息到 Neo4j 图谱
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain chain) {
        // 从上下文获取 conversationId
        String conversationId = extractConversationId(chatClientRequest);
        String userMessage = extractUserMessage(chatClientRequest);

        // 🔴 异步记录用户消息到 Neo4j（不阻塞 LLM 调用）
        if (conversationId != null && userMessage != null) {
            neo4jExecutor.execute(() -> {
                try {
                    analysisService.recordUserMessage(conversationId, userMessage);
                } catch (Exception e) {
                    log.warn("Neo4j 异步写入用户消息失败: {}", e.getMessage());
                }
            });
        }

        // 执行后续链（包括调用 AI）
        ChatClientResponse response = chain.nextCall(chatClientRequest);

        // 🔴 异步记录 AI 回复到 Neo4j
        if (conversationId != null && response.chatResponse() != null) {
            String aiReply = extractAiReply(response.chatResponse());
            if (aiReply != null) {
                neo4jExecutor.execute(() -> {
                    try {
                        analysisService.recordAssistantMessage(conversationId, aiReply);
                    } catch (Exception e) {
                        log.warn("Neo4j 异步写入AI回复失败: {}", e.getMessage());
                    }
                });
            }
        }

        return response;
    }

    /**
     * 流式同步对话
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain chain) {
        String conversationId = extractConversationId(chatClientRequest);
        String userMessage = extractUserMessage(chatClientRequest);

        // 🔴 异步记录用户消息到 Neo4j（不阻塞 LLM 流式调用）
        if (conversationId != null && userMessage != null) {
            neo4jExecutor.execute(() -> {
                try {
                    analysisService.recordUserMessage(conversationId, userMessage);
                } catch (Exception e) {
                    log.warn("Neo4j 异步写入用户消息失败: {}", e.getMessage());
                }
            });
        }

        // 流式收集 AI 回复
        StringBuilder aiReplyBuffer = new StringBuilder();
        return chain.nextStream(chatClientRequest)
                .doOnNext(chatClientResponse -> {
                    if (chatClientResponse.chatResponse() != null) {
                        String chunk = extractAiReply(chatClientResponse.chatResponse());
                        if (chunk != null) {
                            aiReplyBuffer.append(chunk);
                        }
                    }
                })
                .doOnComplete(() -> {
                    if (conversationId != null && aiReplyBuffer.length() > 0) {
                        neo4jExecutor.execute(() -> {
                            try {
                                analysisService.recordAssistantMessage(conversationId, aiReplyBuffer.toString());
                            } catch (Exception e) {
                                log.warn("Neo4j 异步写入AI回复失败: {}", e.getMessage());
                            }
                        });
                    }
                });
    }

    // ========== 辅助方法 ==========

    private String extractConversationId(ChatClientRequest request) {
        try {
            return request.context().get("chat_memory_conversation_id").toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserMessage(ChatClientRequest request) {
        try {
            return request.prompt().getUserMessage().getText();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractAiReply(org.springframework.ai.chat.model.ChatResponse response) {
        try {
            if (response != null && response.getResult() != null) {
                return response.getResult().getOutput().getText();
            }
        } catch (Exception e) {
            // 流式场景可能没有完整 text
        }
        return null;
    }
}
