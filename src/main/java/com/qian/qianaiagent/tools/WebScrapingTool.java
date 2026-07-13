package com.qian.qianaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    @Tool(description = "抓取指定网页的 HTML 内容。使用时机：WebSearchTool 找到相关网页后，需要深入阅读具体内容时。不使用时机：URL 不可达或网页需要登录认证时。")
    public String scrapeWebPage(@ToolParam(description = "要抓取的网页 URL，例如：'https://docs.spring.io/spring-ai/reference/'") String url) {
        try {
            Document document = Jsoup.connect(url).get();
            return document.html();
        } catch (Exception e) {
            return "Error scraping web page: " + e.getMessage();
        }
    }
}
