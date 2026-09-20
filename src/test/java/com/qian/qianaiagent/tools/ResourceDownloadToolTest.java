package com.qian.qianaiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.qian.qianaiagent.constant.FileConstant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceDownloadToolTest {

    // 原目标 https://www.codefather.cn/logo.png 被腾讯 EdgeOne WAF 拦截：
    // curl 请求返回 200，但 Hutool 请求稳定返回 567（疑似 TLS 指纹识别），
    // 导致正向用例在本机永久 skip。改用 httpbin 的官方示例图片 ——
    // 同主机另有 /status/404，便于验证非 2xx 分支（见 downloadFailsOnNon2xxStatus）。
    private static final String PUBLIC_URL = "https://httpbin.org/image/png";

    private static final String SAVED_FILE_NAME = "httpbin-png.png";

    // ===== 新增：路径穿越（纯本地，不发起任何网络请求）=====

    @Test
    void rejectsParentTraversalFileName() {
        String result = new ResourceDownloadTool()
                .downloadResource(PUBLIC_URL, "../../evil.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        // 🔴 必须断言是「路径校验」拒绝的：未加固的实现会真的去下载，
        //    下载失败时同样返回 Error 前缀 —— 只断言前缀会在无网环境下假绿
        assertTrue(result.contains("路径"), result);
    }

    @Test
    void rejectsAbsolutePathFileName() {
        String result = new ResourceDownloadTool()
                .downloadResource(PUBLIC_URL, "C:/Windows/evil.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.contains("路径"), result);
    }

    @Test
    void rejectsNestedTraversalFileName() {
        String result = new ResourceDownloadTool()
                .downloadResource(PUBLIC_URL, "sub/../../evil.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.contains("路径"), result);
    }

    // ===== 新增：SSRF（纯本地）=====

    @Test
    void rejectsLoopbackUrl() {
        String result = new ResourceDownloadTool()
                .downloadResource("http://127.0.0.1:8080/x.png", "x.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.contains("内网"), result);
    }

    @Test
    void rejectsCloudMetadataUrl() {
        String result = new ResourceDownloadTool()
                .downloadResource("http://169.254.169.254/latest/meta-data/", "x.png");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.contains("内网"), result);
    }

    // ===== 改造：原测试只断言 assertNotNull，"Error..." 也算通过（假绿）=====

    @Test
    public void testDownloadResource() {
        // 守卫必须是「HTTP 真的可达」，而不是「DNS 可解析」：
        // DNS 通但出网被墙、站点 5xx/超时、WAF 限流时，后者会让用例误红。
        Assumptions.assumeTrue(httpReachable(PUBLIC_URL), "目标当前不可达（外网受限或站点波动），已跳过");
        String result = new ResourceDownloadTool().downloadResource(PUBLIC_URL, SAVED_FILE_NAME);
        assertNotNull(result);
        assertFalse(result.startsWith("Error downloading resource:"), result);
        assertTrue(result.startsWith("Resource downloaded successfully"), result);

        // 🔴 上面的断言只证明「HTTP 往返成功」。WAF 拦截（实测站点返回过 567）时
        //    execute() 不抛异常，落盘的是 WAF 错误页而非图片，前缀断言照样通过（假绿）。
        //    故校验落盘内容真的是 PNG。
        Path saved = Path.of(FileConstant.FILE_SAVE_DIR, "download", SAVED_FILE_NAME);
        try (InputStream in = Files.newInputStream(saved)) {
            byte[] magic = in.readNBytes(4);
            assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, magic,
                    "落盘内容不是 PNG，可能拿到了 WAF/错误页: " + result);
        } catch (java.io.IOException e) {
            throw new AssertionError("无法读取落盘文件 " + saved + ": " + result, e);
        }
    }

    /**
     * 非 2xx 必须报错，且不得留下残file。
     *
     * <p>回归防线的核心：手写流式下载若漏检状态码，404 的响应体会被当作「下载成功」
     * 原样落盘，{@code ToolCallAgent.isFailureResponse()} 随之清零失败计数。
     */
    @Test
    public void downloadFailsOnNon2xxStatus() {
        String url = "https://httpbin.org/status/404";
        Assumptions.assumeTrue(httpReachable("https://httpbin.org/get"), "目标当前不可达，已跳过");
        Path target = Path.of(FileConstant.FILE_SAVE_DIR, "download", "not-found.txt");
        try {
            Files.deleteIfExists(target);
        } catch (java.io.IOException ignored) {
            // 清理失败不应影响用例，后续断言会暴露真实状态
        }

        String result = new ResourceDownloadTool().downloadResource(url, "not-found.txt");
        assertTrue(result.startsWith("Error downloading resource:"), result);
        // 断言「看起来像状态码」，而非写死 404：httpbin 偶发返回 502，
        // 写死具体码会让用例本身变成 flaky。关键是「非 2xx 一律报错」这一契约。
        assertTrue(result.matches(".*\\d{3}.*"), result);
        // 非 2xx 时必须在建流之前返回：不得创建空文件或写入错误页
        assertFalse(Files.exists(target), "非 2xx 不应留下残file: " + target);
    }

    /**
     * 独立探测目标 URL 是否真的返回 2xx。
     *
     * <p>刻意不复用 {@code downloadResource} 的结果：若复用，WAF 返回 567 时
     * 下载同样「成功」，守卫就永远不会 skip，内容校验反而成了唯一防线（退回假绿）。
     */
    private static boolean httpReachable(String url) {
        try (HttpResponse response = HttpUtil.createGet(url).timeout(5_000).execute()) {
            return response.getStatus() >= 200 && response.getStatus() < 300;
        } catch (Exception e) {
            return false;
        }
    }
}
