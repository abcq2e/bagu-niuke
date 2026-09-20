package com.qian.qianaiagent.tools;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.qian.qianaiagent.constant.FileConstant;
import com.qian.qianaiagent.util.SafePathResolver;
import com.qian.qianaiagent.util.UrlSafetyValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 资源下载工具
 *
 * <p>⚠️ 错误返回前缀 {@code "Error downloading resource: "} 与
 * {@code ToolCallAgent.isFailureResponse()} 耦合，不得改动。
 */
public class ResourceDownloadTool {

    /** 单次下载超时（毫秒） */
    private static final int TIMEOUT_MS = 60_000;

    /** 单文件上限 100MB —— 与工具描述中声明的上限保持一致 */
    private static final long MAX_DOWNLOAD_BYTES = 100L * 1024 * 1024;

    private static final int BUFFER_SIZE = 8192;

    @Tool(description = "从指定 URL 下载资源文件。使用时机：用户需要下载图片、文档、或其他静态资源时。不使用时机：URL 需要认证、资源过大（>100MB）时。")
    public String downloadResource(@ToolParam(description = "资源 URL，例如：'https://example.com/chart.png'") String url, @ToolParam(description = "保存的文件名，例如：'chart.png'、'report.pdf'") String fileName) {
        try {
            // 🔴 两个入参都不可信：URL 来自 LLM，fileName 同样来自 LLM。
            //    此前 fileName 未校验即拼进路径，传入 ../../ 可写到任意位置。
            //    先校验 fileName：它是纯本地判断、不依赖网络，能更早拒绝路径穿越，
            //    也让该层防护可被「无外网环境」下的测试真实覆盖 —— 若先校验 URL，
            //    无网时 DNS 解析失败会提前抛错，路径穿越用例会因错误的原因通过
            Path target = SafePathResolver.resolveWithin(
                    Path.of(FileConstant.FILE_SAVE_DIR, "download"), fileName);
            URI safe = UrlSafetyValidator.validate(url);
            // 后续建连一律使用 safe，不再引用原始 url 字符串

            Files.createDirectories(target.getParent());

            long written = 0;
            boolean tooLarge = false;
            // 🔴 状态码必须在建流之前检查，且必须拆成两个 try-with-resources：
            //    Files.newOutputStream(target) 一旦执行就会创建（截断）文件，
            //    若与 execute() 放在同一个资源声明里，非 2xx 时也会先落一个空文件，
            //    报错却留下残file。此处先只 execute + 校验，非 2xx 时根本不创建文件。
            //    原 HttpUtil.downloadFile 遇非 2xx 会抛异常，改手写流式后丢失了该语义：
            //    任何 4xx/5xx 响应体会被原样写盘并返回「成功」，
            //    ToolCallAgent.isFailureResponse() 因此清零失败计数，自愈循环被静默绕过。
            try (HttpResponse response = HttpUtil.createGet(safe.toString())
                    .timeout(TIMEOUT_MS)
                    .execute()) {
                int status = response.getStatus();
                if (status < 200 || status >= 300) {
                    // return 落在 try-with-resources 内，response 会自动关闭 ✓
                    return "Error downloading resource: 服务端返回状态码 " + status;
                }
                // HttpResponse 必须关闭，否则连接泄漏
                try (InputStream in = response.bodyStream();
                     OutputStream out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        written += read;
                        if (written > MAX_DOWNLOAD_BYTES) {
                            tooLarge = true;
                            break;
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }

            if (tooLarge) {
                // 流已随 try-with-resources 关闭，此处可安全删除半成品
                Files.deleteIfExists(target);
                return "Error downloading resource: 文件超过 "
                        + (MAX_DOWNLOAD_BYTES / 1024 / 1024) + "MB 上限，已中止";
            }
            return "Resource downloaded successfully to: " + target;
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
