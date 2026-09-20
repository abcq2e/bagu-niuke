package com.qian.qianaiagent.tools;

import com.qian.qianaiagent.util.UrlSafetyValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.net.URI;

/**
 * 网页抓取工具
 *
 * <p>⚠️ 错误返回前缀 {@code "Error scraping web page: "} 与
 * {@code ToolCallAgent.isFailureResponse()} 耦合，不得改动。
 */
public class WebScrapingTool {

    /** 单次抓取超时（毫秒） */
    private static final int TIMEOUT_MS = 10_000;

    /** 响应体上限 2MB，防止一个无限流页面把内存吃光 */
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    @Tool(description = "抓取指定网页的 HTML 内容。使用时机：WebSearchTool 找到相关网页后，需要深入阅读具体内容时。不使用时机：URL 不可达或网页需要登录认证时。")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页 URL，例如：'https://docs.spring.io/spring-ai/reference/'") String url) {
        try {
            // 校验必须在发起请求之前；后续一律使用返回值建连
            URI safe = UrlSafetyValidator.validate(url);
            Document document = Jsoup.connect(safe.toString())
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    // 🔴 不可省：校验发生在发请求之前，跳转发生在拿到响应之后。
                    //    若允许跟随，外部 URL 返回 302 指向 127.0.0.1 即可绕过校验
                    .followRedirects(false)
                    .get();
            return document.html();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
