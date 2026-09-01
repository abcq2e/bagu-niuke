package com.qian.qianaiagent.rag.ingestion;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 创建知识考察上下文查询增强器的工厂
 * <p>
 * 当检索不到相关知识时，拒绝回答非知识考察相关的问题
 */
public class QuizContextualQueryAugmenterFactory {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate(
                "你应该输出下面的内容：\n" +
                "抱歉，我只能回答知识考察相关的问题，其他问题不在我的职责范围内。\n" +
                "有问题可以联系编程导航客服 https://codefather.cn"
        );
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
