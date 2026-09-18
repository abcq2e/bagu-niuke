package com.qian.qianaiagent.tools;

import com.qian.qianaiagent.rag.retrieval.MultiQuerySearchService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * 外部 MCP Server 暴露的工具。
     * <p>用 {@link ObjectProvider} 而非直接注入：未开启 spring.ai.mcp.client 时该 Bean 不存在，
     * 直接注入会导致应用启动失败，而 MCP 属于可选能力，不该阻塞主链路。
     */
    private final ObjectProvider<ToolCallbackProvider> mcpToolCallbackProviders;

    public ToolRegistration(MultiQuerySearchService multiQuerySearchService,
                            ObjectProvider<ToolCallbackProvider> mcpToolCallbackProviders) {
        this.multiQuerySearchService = multiQuerySearchService;
        this.mcpToolCallbackProviders = mcpToolCallbackProviders;
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
        ToolCallback[] localTools = ToolCallbacks.from(   //工具调用的回调钩子
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                ragSearchTool
        );
        return mergeWithMcpTools(localTools);
    }

    /**
     * 把 MCP Server 暴露的工具并入本地工具集，让 Agent 无差别地调用两者。
     * <p>冲突时以本地工具为准：MCP 工具来自外部进程，命名不受本项目控制，
     * 让它覆盖同名本地工具会带来难以排查的行为漂移。
     */
    private ToolCallback[] mergeWithMcpTools(ToolCallback[] localTools) {
        Map<String, ToolCallback> merged = new LinkedHashMap<>();
        for (ToolCallback tool : localTools) {
            merged.put(tool.getToolDefinition().name(), tool);
        }
        mcpToolCallbackProviders.orderedStream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .forEach(tool -> merged.putIfAbsent(tool.getToolDefinition().name(), tool));
        return merged.values().toArray(new ToolCallback[0]);
    }
}
