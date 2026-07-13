package com.qian.qianaiagent.tools;

import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

/**
 * 网页搜索工具（基于 Tavily Search API）
 * <p>
 * Tavily 是专为 AI Agent 设计的搜索引擎 API，返回结果直接适配 LLM 消费。
 */
@Slf4j
public class WebSearchTool {

    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = """
            使用 Tavily 搜索引擎获取实时网页信息。
            使用时机：需要最新资讯、新闻、事实核查、或超出你知识截止日期（2024年）的信息时。
            不使用时机：纯常识问题、逻辑推理、数学计算、或已通过本地知识库 RagSearchTool 获取足够信息时无需搜索。
            注意事项：可能因 API 超时或限流失败，此时应尝试简化搜索关键词重试 1 次，若仍失败则降级使用已有知识回答。""")
    public String searchWeb(
            @ToolParam(description = "搜索关键词或自然语言问题，例如：'Java 21 虚拟线程最新实践'、'2025年AI Agent框架对比'") String query) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("max_results", 5);
        body.put("include_answer", true);

        try {
            HttpResponse response = HttpRequest.post(TAVILY_API_URL)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSONUtil.toJsonStr(body))
                    .execute();

            // 先检查 HTTP 状态码（hutool 不会对 4xx/5xx 抛异常，需要手动检查）
            int status = response.getStatus();
            if (status == 401 || status == 403) {
                log.error("Tavily API 认证失败，HTTP {}", status);
                return "❌ 搜索失败：API Key 无效或已过期。"
                        + "Agent 建议：暂停搜索类任务，提示用户检查 Tavily API Key 配置。";
            }
            if (status == 429) {
                log.warn("Tavily API 请求频率超限");
                return "⚠️ 搜索请求被限流（HTTP 429）。"
                        + "Agent 建议：等待 30 秒后重试，或改用更精简的搜索关键词。";
            }
            if (status >= 500) {
                log.error("Tavily API 服务端异常，HTTP {}", status);
                return "⚠️ Tavily 搜索服务暂时不可用（HTTP " + status + "）。"
                        + "Agent 建议：稍后重试，当前可先用内置知识库回答用户。";
            }

            String responseBody = response.body();
            JSONObject jsonObject = JSONUtil.parseObj(responseBody);

            StringBuilder result = new StringBuilder();

            // 优先输出 AI 生成的答案摘要（Tavily 特色）
            if (jsonObject.containsKey("answer") && jsonObject.getStr("answer") != null) {
                result.append("Answer: ").append(jsonObject.getStr("answer")).append("\n\n");
            }

            // 解析搜索结果列表
            JSONArray results = jsonObject.getJSONArray("results");
            if (results != null && !results.isEmpty()) {
                result.append("Search Results:\n");
                for (int i = 0; i < results.size(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    result.append(i + 1).append(". ").append(item.getStr("title")).append("\n");
                    result.append("   URL: ").append(item.getStr("url")).append("\n");
                    result.append("   ").append(item.getStr("content")).append("\n\n");
                }
            }

            return result.toString();
        } catch (HttpException e) {
            // hutool 把所有 HTTP/网络异常包装成 HttpException（RuntimeException）
            // 通过 getCause() 区分具体原因，给 Agent 不同建议
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                log.warn("Tavily API 请求超时", e);
                return "⚠️ 搜索请求超时。Agent 建议：① 简化搜索关键词后重试 ② 检查网络连接是否正常。";
            }
            if (cause instanceof ConnectException) {
                log.warn("Tavily API 连接失败", e);
                return "⚠️ 无法连接到搜索服务。Agent 建议：① 检查网络/代理配置 ② 稍后重试 ③ 暂时用内置知识回答用户。";
            }
            // 其他 HTTP 异常（SSL 错误、连接重置等）
            log.error("Tavily API HTTP 异常", e);
            return "❌ 搜索请求异常：" + e.getMessage()
                    + "。Agent 建议：稍后重试，若持续失败则降级使用内置知识。";
        } catch (Exception e) {
            // 不可恢复 —— 未知错误（JSON 解析失败等），Agent 应该放弃搜索、降级处理
            log.error("搜索过程发生未知异常", e);
            return "❌ 搜索过程发生意外错误：" + e.getMessage()
                    + "。Agent 建议：放弃本次搜索，使用已有知识回答或提示用户稍后重试。";
        }
    }
}
