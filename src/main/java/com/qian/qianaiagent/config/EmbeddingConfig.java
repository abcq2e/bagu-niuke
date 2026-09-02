package com.qian.qianaiagent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Embedding 模型配置
 * <p>
 * Spring AI 自动配置已创建 {@link OpenAiEmbeddingModel} bean（bean 名: openAiEmbeddingModel），
 * 该类提供额外的便捷 Bean，方便在不同场景下注入使用。
 * </p>
 */

//创建一个嵌入模型
@Configuration
public class EmbeddingConfig {


    /**
     * 主 Embedding 模型
     * <p>
     * DeepSeek 不提供 Embedding API，改用阿里云 DashScope 做向量化。
     * Spring AI Alibaba 自动配置已创建 dashscopeEmbeddingModel bean。
     * 模型：text-embedding-v2（1536 维）
     * </p>
     */
    @Bean
    @Primary
    EmbeddingModel primaryEmbeddingModel(
            @Qualifier("dashscopeEmbeddingModel") EmbeddingModel dashscopeEmbeddingModel) {
        return dashscopeEmbeddingModel;
    }
}













