package com.qian.qianaiagent.tools;

import com.qian.qianaiagent.rag.MultiQuerySearchService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中的工具注册类
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    /**
     * 注入 RAG 检索服务，供 RagSearchTool 和 QuizApp 共享同一套检索逻辑
     */
    private final MultiQuerySearchService multiQuerySearchService;

    public ToolRegistration(MultiQuerySearchService multiQuerySearchService) {
        this.multiQuerySearchService = multiQuerySearchService;
    }

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        // 知识库检索工具 —— Agent 可主动搜索本地技术文档
        RagSearchTool ragSearchTool = new RagSearchTool(multiQuerySearchService);
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                ragSearchTool
        );
    }
}
