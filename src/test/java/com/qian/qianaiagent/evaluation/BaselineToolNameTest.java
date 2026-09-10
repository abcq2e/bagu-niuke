package com.qian.qianaiagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qian.qianaiagent.tools.FileOperationTool;
import com.qian.qianaiagent.tools.PDFGenerationTool;
import com.qian.qianaiagent.tools.RagSearchTool;
import com.qian.qianaiagent.tools.ResourceDownloadTool;
import com.qian.qianaiagent.tools.TerminateTool;
import com.qian.qianaiagent.tools.TerminalOperationTool;
import com.qian.qianaiagent.tools.WebScrapingTool;
import com.qian.qianaiagent.tools.WebSearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基线用例工具名守卫 —— 防止"基线里写了不存在的工具名"这类静默失败。
 *
 * <p>Spring AI 以 <b>方法名</b> 作为工具名。历史上基线写的是 webSearch/fileRead/ragSearch，
 * 而实际注册的是 searchWeb/readFile/searchKnowledgeBase，导致三个用例永久失败且毫无提示。
 *
 * <p>断言源是 {@link ToolCallbacks#from} 的真实注册结果（与 {@code ToolRegistration.allTools()} 同一路径），
 * 不是手抄的字符串列表 —— 任何工具改名都会让本测试立刻变红。
 */
class BaselineToolNameTest {

    private static final Path BASELINES_DIR = Paths.get("evaluation", "baselines");

    /** 与 ToolRegistration.allTools() 完全一致的装配；无参构造器可安全传占位值 */
    private static Set<String> registeredToolNames() {
        ToolCallback[] tools = ToolCallbacks.from(
                new FileOperationTool(),
                new WebSearchTool("dummy-key-for-name-scan"),
                new WebScrapingTool(),
                new ResourceDownloadTool(),
                new TerminalOperationTool(),
                new PDFGenerationTool(),
                new TerminateTool(),
                new RagSearchTool(null));
        return Arrays.stream(tools)
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toSet());
    }

    private static List<BaselineManager.Baseline> loadBaselineFiles() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        try (Stream<Path> files = Files.list(BASELINES_DIR)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> {
                        try {
                            return mapper.readValue(p.toFile(), BaselineManager.Baseline.class);
                        } catch (Exception e) {
                            throw new RuntimeException("解析基线文件失败: " + p, e);
                        }
                    })
                    .toList();
        }
    }

    @Test
    void everyExpectedToolNameIsActuallyRegistered() throws Exception {
        Set<String> registered = registeredToolNames();
        List<BaselineManager.Baseline> cases = loadBaselineFiles();

        assertFalse(cases.isEmpty(), "evaluation/baselines/ 下没有找到任何用例文件");

        for (BaselineManager.Baseline c : cases) {
            ExpectedBehavior expected = c.getExpectedBehavior();
            if (expected == null || expected.getExpectedToolCalls() == null) {
                continue;
            }
            for (ExpectedBehavior.ToolCallExpectation call : expected.getExpectedToolCalls()) {
                if (call.getToolName() == null) {
                    continue;
                }
                assertTrue(registered.contains(call.getToolName()),
                        "用例 [" + c.getCaseName() + "] 期望的工具 \"" + call.getToolName()
                                + "\" 未注册。实际注册的工具名: " + registered);
            }
        }
    }

    @Test
    void expectedToolCallsAreNotEmpty() throws Exception {
        List<BaselineManager.Baseline> cases = loadBaselineFiles();

        for (BaselineManager.Baseline c : cases) {
            assertTrue(c.getExpectedBehavior() != null
                            && c.getExpectedBehavior().getExpectedToolCalls() != null
                            && !c.getExpectedBehavior().getExpectedToolCalls().isEmpty(),
                    "用例 [" + c.getCaseName() + "] 没有声明期望的工具调用");
        }
    }

    @Test
    void expectedResponseKeywordsAreNotEmpty() throws Exception {
        List<BaselineManager.Baseline> cases = loadBaselineFiles();

        for (BaselineManager.Baseline c : cases) {
            assertTrue(c.getExpectedBehavior() != null
                            && c.getExpectedBehavior().getExpectedResponseKeywords() != null
                            && !c.getExpectedBehavior().getExpectedResponseKeywords().isEmpty(),
                    "用例 [" + c.getCaseName() + "] 没有声明期望的回答关键词");
        }
    }
}
