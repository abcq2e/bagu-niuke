package com.qian.qianaiagent.rag.ingestion;

import com.qian.qianaiagent.agent.llm.ResilientChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 * <p>⚠️ 会逐条调 LLM 提取关键词，文档量大时非常耗时。
 * 如需启用，确保数据量和 LLM 并发配额允许。
 */
@Slf4j
@Component
public class MyKeywordEnricher {

    /**
     * 构造器注入而非 {@code @Resource} 字段注入。
     *
     * <p>原先字段名 {@code openAiChatModel}，{@code @Resource} <b>先按字段名匹配</b>，
     * 会直接命中同名裸 bean，{@code @Primary} 上的 {@code ResilientChatModel} 不生效 ——
     * 逐条文档的关键词提取会绕过超时/重试/熔断/降级（这是最容易被打爆的批量路径）。
     * 构造器注入按<b>类型</b>解析，{@code @Primary} 才会赢。
     */
    private final ChatModel chatModel;

    public MyKeywordEnricher(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<Document> enrichDocuments(List<Document> documents) {
        log.info("开始关键词增强，共 {} 条文档，逐条调 LLM 提取关键词...", documents.size());
        KeywordMetadataEnricher keywordMetadataEnricher =
                new KeywordMetadataEnricher(new FailFastOnUnavailableReply(chatModel), 5);
        List<Document> result = keywordMetadataEnricher.apply(documents);
        log.info("关键词增强完成");
        return result;
    }

    /**
     * 把「兜底话术」翻译成异常的小装饰器 —— 只包一层，不改变任何其它行为。
     *
     * <p><b>为什么必须在 ChatModel 这一层拦，而不能在 {@link #enrichDocuments} 里判：</b>
     * {@link KeywordMetadataEnricher} 是 Spring AI 的类，它在 {@code apply()} 内部
     * <b>逐条文档</b>调用 {@code chatModel.call(...)}，把返回文本当成关键词行去解析。
     * 它没有给我们任何「拿到返回值」的钩子，唯一能插手的位置就是传给它的那个
     * {@code ChatModel} 本身。
     *
     * <p>不拦会怎样：兜底话术会被当成关键词行，被 {@code split("\\s*,\\s*")} 之类的逻辑
     * 切碎后<b>写进向量库的 metadata</b>。那是持久化污染 —— 向量已入库，之后要修得重跑
     * ETL；而在检索侧它只是让召回变差，没有任何报错。
     *
     * <p><b>这是「恢复」而不是「新增」行为：</b>本次改动之前这里注入的是裸
     * {@code openAiChatModel}，模型挂掉时 Spring AI 直接抛异常，异常从 {@code apply()}
     * 冒到 {@code QuizVectorStoreConfig} 的 {@code quizVectorStore} 工厂方法，
     * 「摄入失败」是响亮的（在最坏的情况下就是启动失败）。韧性装饰器把那个异常
     * 换成了返回值，这里只是把它换回异常。
     */
    private static final class FailFastOnUnavailableReply implements ChatModel {

        private final ChatModel delegate;

        private FailFastOnUnavailableReply(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            ChatResponse response = delegate.call(prompt);
            String text = extractText(response);
            if (ResilientChatModel.isUnavailableReply(text)) {
                throw new ResilientChatModel.UnavailableReplyException(
                        "MyKeywordEnricher: LLM 返回兜底话术（主备全挂），"
                                + "拒绝把它当成关键词写进向量库元数据");
            }
            return response;
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            // 摄入路径是阻塞式逐条调用，永远不会走流式；原样委派，不在这里做判据。
            return delegate.stream(prompt);
        }

        @Override
        public ChatOptions getDefaultOptions() {
            ChatOptions options = delegate.getDefaultOptions();
            return options != null ? options : ChatModel.super.getDefaultOptions();
        }

        private static String extractText(ChatResponse response) {
            if (response == null || response.getResult() == null) {
                return null;
            }
            return response.getResult().getOutput().getText();
        }
    }
}
