package com.qian.qianaiagent.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 接入守卫 —— 确认外部 MCP Server 暴露的工具真的并进了 Agent 的工具集。
 *
 * <p>这条链路跨进程（Spring AI → stdio → MCP Server 子进程），断掉时不会抛异常：
 * jar 没构建、profile 配错、日志污染 stdout 破坏 JSON-RPC，表现都只是工具静默消失，
 * Agent 照常启动、只是永远用不上那个工具。所以必须显式断言。
 *
 * <p>前置条件：{@code qian-image-search-mcp-server} 已执行过 {@code mvn package}。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class McpToolRegistrationTest {

    @Autowired
    private ToolCallback[] allTools;

    /**
     * Spring AI 会给 MCP 工具加 {@code {clientName}_{serverName}_{toolName}} 前缀（连字符转下划线），
     * 以避免与本地工具重名，所以这里按后缀匹配而非全等 —— 前缀随
     * {@code spring.ai.mcp.client.name} 变化，不该把测试焊死在某个具体名字上。
     */
    @Test
    void mcpServer暴露的searchImage应并入工具集() {
        List<String> names = Arrays.stream(allTools)
                .map(cb -> cb.getToolDefinition().name())
                .toList();
        assertTrue(names.stream().anyMatch(n -> n.endsWith("_searchImage")),
                "MCP 工具未接入，当前工具集: " + names);
    }

    /** 本地工具不能被 MCP 工具挤掉 —— 合并是新增，不是替换 */
    @Test
    void 本地工具应保持完整() {
        List<String> names = Arrays.stream(allTools)
                .map(cb -> cb.getToolDefinition().name())
                .toList();
        assertTrue(names.containsAll(List.of("searchWeb", "readFile", "searchKnowledgeBase")),
                "本地工具丢失，当前工具集: " + names);
    }
}
