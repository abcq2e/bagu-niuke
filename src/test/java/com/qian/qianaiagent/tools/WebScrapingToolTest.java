package com.qian.qianaiagent.tools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebScrapingToolTest {

    // ===== 新增：SSRF 防护（纯本地，不发起任何网络请求）=====

    @Test
    void rejectsLoopbackUrlBeforeAnyRequest() {
        String result = new WebScrapingTool().scrapeWebPage("http://127.0.0.1:8080/admin");
        // 前缀必须保留：ToolCallAgent.isFailureResponse() 依赖 "Error" 前缀驱动自愈计数
        assertTrue(result.startsWith("Error scraping web page:"), result);
        assertTrue(result.contains("内网"), result);
    }

    @Test
    void rejectsCloudMetadataUrl() {
        String result = new WebScrapingTool()
                .scrapeWebPage("http://169.254.169.254/latest/meta-data/");
        assertTrue(result.startsWith("Error scraping web page:"), result);
        // 🔴 必须断言是「网段规则」拒绝的：本机无到 link-local 的路由，
        //    未加固的实现同样会因 SocketException 落到 catch-all 并返回相同前缀 ——
        //    只断言前缀则本用例在未加固代码上就是绿的，测不到任何防护
        assertTrue(result.contains("内网"), result);
    }

    @Test
    void rejectsFileScheme() {
        String result = new WebScrapingTool().scrapeWebPage("file:///etc/passwd");
        assertTrue(result.startsWith("Error scraping web page:"), result);
        // 🔴 同理：Jsoup 自身就会拒绝 file: 并抛 MalformedURLException，
        //    未加固的实现也返回相同前缀。断言「协议」才能钉死是白名单拒绝的
        assertTrue(result.contains("协议"), result);
    }

    // ===== 改造：原测试只断言 assertNotNull，"Error..." 也算通过（假绿）=====

    @Test
    void scrapePublicPage() {
        Assumptions.assumeTrue(dnsResolvable("www.codefather.cn"), "需要外网，已跳过");
        String result = new WebScrapingTool().scrapeWebPage("https://www.codefather.cn");
        assertNotNull(result);
        // 断言正常站点未被误伤。
        // ⚠️ 不要用 contains("内网") 判断：被抓页面的正文本身就可能含该词
        //    （实测 codefather.cn 课程简介里有「实战内网穿透」），且随缓存时有时无，
        //    会产生既非稳定红也非稳定绿的 flaky 断言
        assertFalse(result.startsWith("Error scraping web page:"), result);
    }

    private static boolean dnsResolvable(String host) {
        try {
            InetAddress.getAllByName(host);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
