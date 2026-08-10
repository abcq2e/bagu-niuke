package com.qian.qianaiagent.tools;

import com.qian.qianaiagent.rag.MultiQuerySearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 知识库检索工具 —— 让 Agent 能主动搜索本地向量知识库
 * <p>
 * 封装 MultiQuerySearchService（多查询变体 → 并行检索 → 去重排序），
 * 将 RAG 能力作为 Agent 工具箱中的一把"螺丝刀"，Agent 自主决定何时检索。
 */
@Slf4j
public class RagSearchTool {

    private final MultiQuerySearchService multiQuerySearchService;

    public RagSearchTool(MultiQuerySearchService multiQuerySearchService) {
        this.multiQuerySearchService = multiQuerySearchService;
    }

    @Tool(description = """
            搜索本地向量知识库（全库检索），获取与查询相关的技术文档片段。
            知识库覆盖四大分类：
            - 八股：Java 并发、Spring 框架、数据结构与算法、JVM 等面试题
            - 面渣逆袭：Java 基础、JVM、MySQL、Redis、Spring 等大厂面试问答集
            - Agent：AI Agent 设计、工具化、MCP、框架实现
            - 实践：落地经验、知识库构建方法

            使用时机：需要查阅技术原理、最佳实践、或面试准备资料时调用。
            不使用时机：纯常识问题、逻辑推理、个人观点问题时无需调用。
            提示：如果明确知道要查哪个分类，优先用 searchKnowledgeBaseByCategory。
            """)
    public String searchKnowledgeBase(
            @ToolParam(description = "搜索关键词或自然语言问题") String query) {
        log.info("RagSearchTool 收到查询: {}", query);
        try {
            List<Document> docs = multiQuerySearchService.multiQuerySearch(query, 3, 5, 0.3);
            if (docs.isEmpty()) {
                return "未检索到相关文档。建议：换用更通用的关键词，或改用 WebSearchTool 搜索互联网。";
            }
            StringBuilder result = new StringBuilder();
            result.append("从知识库检索到 ").append(docs.size()).append(" 条相关文档：\n\n");
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                result.append("--- 文档片段 ").append(i + 1)
                        .append("（相关度: ").append(String.format("%.2f", doc.getScore())).append("）---\n");
                result.append(doc.getText()).append("\n\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage());
            return "❌ 知识库检索异常：" + e.getMessage()
                    + "。Agent 建议：放弃本次检索，改用 WebSearchTool 或根据已有知识回答。";
        }
    }

    @Tool(description = """
            按分类搜索本地向量知识库，只检索指定分类下的文档，结果更精准。
            分类选项（四选一）：
            - "八股"：Java 并发、Spring 框架、数据结构与算法、JVM 等面试题
            - "面渣逆袭"：Java 基础、JVM、MySQL、Redis、Spring 等大厂面试问答详解
            - "Agent"：AI Agent 设计原理、工具化方案、MCP、框架实现
            - "实践"：项目落地经验、知识库构建方法

            使用时机：用户明确问了某个领域的知识点时优先使用，比全库检索更快更准。
            示例：用户问"Java 并发怎么学"→ category="八股"；问"JVM 内存结构"→ category="面渣逆袭"
            """)
    public String searchKnowledgeBaseByCategory(
            @ToolParam(description = "搜索关键词或自然语言问题") String query,
            @ToolParam(description = "知识分类，三选一：八股、Agent、实践") String category) {
        log.info("RagSearchTool 收到分类检索: category={}, query={}", category, query);
        try {
            List<Document> docs = multiQuerySearchService.multiQuerySearchWithCategory(
                    query, 3, 5, 0.3, category);
            if (docs.isEmpty()) {
                return "在【" + category + "】分类下未检索到相关文档。建议：换用全库检索 searchKnowledgeBase，或改用 WebSearchTool 搜索互联网。";
            }
            StringBuilder result = new StringBuilder();
            result.append("从【").append(category).append("】分类检索到 ")
                    .append(docs.size()).append(" 条相关文档：\n\n");
            for (int i = 0; i < docs.size(); i++) {
                Document doc = docs.get(i);
                result.append("--- 文档片段 ").append(i + 1)
                        .append("（相关度: ").append(String.format("%.2f", doc.getScore())).append("）---\n");
                result.append(doc.getText()).append("\n\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("分类检索失败: {}", e.getMessage());
            return "❌ 分类检索异常：" + e.getMessage()
                    + "。Agent 建议：降级使用全库检索 searchKnowledgeBase，或根据已有知识回答。";
        }
    }
}
