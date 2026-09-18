package com.qian.qianaiagent.controller;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.kernel.colors.ColorConstants;
import com.qian.qianaiagent.constant.FileConstant;
import com.qian.qianaiagent.memory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/export")
public class ExportController {


    private static final Path DOWNLOAD_BASE_DIR =
            Paths.get(FileConstant.FILE_SAVE_DIR).toAbsolutePath().normalize();


    private static final List<String> CJK_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/msyh.ttc,0",      // 微软雅黑
            "C:/Windows/Fonts/simsun.ttc,0",    // 宋体
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
            "/System/Library/Fonts/PingFang.ttc,0"
    );

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource(name = "fileBasedChatMemory")
    private FileBasedChatMemory chatMemory;


    @GetMapping("/pdf")
    public void exportPdf(@RequestParam String chatId, HttpServletResponse response) throws IOException {
        // 1. 防路径穿越：chatId 会被拼成文件名，不能含 ".." 或路径分隔符
        if (isUnsafeChatId(chatId)) {
            writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "无效的会话 ID");
            return;
        }

        // 2. 取对话历史
        List<Message> messages = chatMemory.getConversation(chatId);
        if (messages == null || messages.isEmpty()) {
            writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "会话不存在或暂无对话记录");
            return;
        }

        // 3. 设置响应头（必须在写出任何字节之前完成）
        response.setContentType("application/pdf");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", attachmentHeader("chat-" + chatId + ".pdf"));

        // 4. PdfWriter 直接写 response 输出流 —— 不落磁盘临时文件，省一次全量 IO
        OutputStream out = response.getOutputStream();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            PdfFont font = resolveFont();
            document.setFont(font);

            writeTitle(document, font, chatId, messages.size());
            for (Message message : messages) {
                writeMessage(document, font, message);
            }
            // document.close() 会写出 PDF 尾部并 flush 缓冲区到 OutputStream，
            // 所以 PDF 内容是在这一步才真正落到 response 里的。
        } catch (Exception e) {
            log.error("导出 PDF 失败: chatId={}", chatId, e);
            // 字节已经写出去了就无力回天，只能记录日志；未提交时还能返回结构化错误
            if (!response.isCommitted()) {
                response.reset();
                writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "PDF 生成失败: " + e.getMessage());
            }
            return;
        }

        response.flushBuffer();
        log.info("已导出对话 PDF: chatId={}, 消息数={}", chatId, messages.size());
    }

    /** 写入 PDF 封面标题区：标题 + 会话信息 + 分隔线 */
    private void writeTitle(Document document, PdfFont font, String chatId, int messageCount) {
        Paragraph title = new Paragraph("对话记录")
                .setFont(font)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4);
        title.setProperty(Property.BOLD_SIMULATION, true);
        document.add(title);

        document.add(new Paragraph("会话 " + chatId + "　·　共 " + messageCount + " 条消息"
                + "　·　导出时间 " + LocalDateTime.now().format(TIME_FORMAT))
                .setFont(font)
                .setFontSize(9)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(16));
    }

    private void writeMessage(Document document, PdfFont font, Message message) {
        MessageType type = message.getMessageType();
        String role = switch (type) {
            case USER -> "用户";
            case ASSISTANT -> "AI 助手";
            case SYSTEM -> "系统";
            case TOOL -> "工具";
        };
        // 用户消息蓝色、AI 黑色、系统/工具灰色
        var accent = switch (type) {
            case USER -> ColorConstants.BLUE;
            case ASSISTANT -> ColorConstants.DARK_GRAY;
            case SYSTEM, TOOL -> ColorConstants.GRAY;
        };

        Table table = new Table(UnitValue.createPercentArray(new float[]{1}))
                .useAllAvailableWidth()
                .setMarginBottom(10);

        Paragraph roleLabel = new Paragraph(role)
                .setFont(font)
                .setFontSize(9)
                .setFontColor(accent)
                .setMarginBottom(2);
        roleLabel.setProperty(Property.BOLD_SIMULATION, true);

        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                // 只留左侧竖线做角色标识
                .setBorderLeft(new SolidBorder(accent, 2))
                .setPaddingLeft(8)
                .setPaddingTop(4)
                .setPaddingBottom(4)
                .add(roleLabel)
                .add(new Paragraph(safeText(message.getText()))
                        .setFont(font)
                        .setFontSize(11)
                        .setMarginBottom(0));

        table.addCell(cell);
        document.add(table);
    }

    @GetMapping("/download")
    public void download(@RequestParam String filePath, HttpServletResponse response) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "缺少 filePath 参数");
            return;
        }

        // ① 规范化：resolve 拼接 + normalize 消掉 ".."/"."
        Path target = DOWNLOAD_BASE_DIR.resolve(filePath).normalize();
        // ② 白名单校验：规范化后必须仍在 tmp/ 之内
        if (!target.startsWith(DOWNLOAD_BASE_DIR)) {
            log.warn("拦截路径穿越尝试: filePath={} -> {}", filePath, target);
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "非法的文件路径");
            return;
        }
        if (!Files.isRegularFile(target)) {
            writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "文件不存在");
            return;
        }
        // ③ 解析符号链接后再校验一次，防止软链接逃逸
        try {
            Path realPath = target.toRealPath();
            if (!realPath.startsWith(DOWNLOAD_BASE_DIR.toRealPath())) {
                log.warn("拦截符号链接逃逸: filePath={} -> {}", filePath, realPath);
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "非法的文件路径");
                return;
            }
        } catch (IOException e) {
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件路径解析失败");
            return;
        }

        String fileName = target.getFileName().toString();
        long fileSize = Files.size(target);

        response.setContentType(contentTypeOf(fileName));
        response.setContentLengthLong(fileSize);
        // 不要自己读整个文件到内存：Files.copy 会在内核态完成流式搬运，天生支持大文件
        response.setHeader("Content-Disposition", attachmentHeader(fileName));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(target, out);
            out.flush();
        } catch (IOException e) {
            log.error("下载文件失败: {}", target, e);
            if (!response.isCommitted()) {
                response.reset();
                writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件下载失败");
            }
            return;
        }
        response.flushBuffer();
        log.info("已下载文件: {} ({} bytes)", fileName, fileSize);
    }

    private PdfFont resolveFont() {
        // ① iText 内置 CJK 字体
        try {
            return PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
        } catch (Exception e) {
            log.warn("内置中文字体不可用（请确认 font-asian 依赖在运行时类路径上）: {}", e.getMessage());
        }
        // ② 系统字体
        for (String candidate : CJK_FONT_CANDIDATES) {
            try {
                // 系统字体多为 TTF/TTC，用 Identity-H 编码嵌入
                return PdfFontFactory.createFont(candidate, "Identity-H");
            } catch (Exception ignored) {
                // 该候选不存在或不可读，继续试下一个
            }
        }
        // ③ 兜底：标准字体（中文会渲染为空白，但接口仍可用）
        log.error("未找到任何可用中文字体，PDF 中的中文将无法显示");
        try {
            return PdfFontFactory.createFont();
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 PDF 字体", e);
        }
    }

    private String attachmentHeader(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    /** 按扩展名推断 Content-Type，未知类型一律 {@code application/octet-stream} 强制下载 */
    private String contentTypeOf(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".txt") || lower.endsWith(".md")) return "text/plain;charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json;charset=UTF-8";
        if (lower.endsWith(".csv")) return "text/csv;charset=UTF-8";
        if (lower.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }


    private boolean isUnsafeChatId(String chatId) {
        return chatId == null || chatId.isBlank()
                || chatId.contains("..") || chatId.contains("/") || chatId.contains("\\");
    }

    /** 尽力避免 null 正文触发 iText NPE */
    private String safeText(String text) {
        return text == null || text.isBlank() ? "(空消息)" : text;
    }

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\": " + status + ", \"message\": \"" + message + "\", \"data\": null}");
    }
}
