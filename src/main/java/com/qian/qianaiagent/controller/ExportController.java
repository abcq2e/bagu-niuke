package com.qian.qianaiagent.controller;

import com.qian.qianaiagent.memory.FileBasedChatMemory;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 导出下载控制器
 *
 * ===== 🎯 Task 15: 你来完成 =====
 * 提供对话记录导出为 PDF 的功能，以及文件下载。
 *
 * <h2>📖 HTTP 文件下载原理</h2>
 * 文件下载不是返回 JSON，而是直接把文件内容写入 HTTP 响应流：
 * 1. 设置 Content-Type: application/pdf（告诉浏览器这是 PDF）
 * 2. 设置 Content-Disposition: attachment; filename="xxx.pdf"（触发浏览器下载）
 * 3. 把 PDF 的字节流写入 response.getOutputStream()
 * 4. response.flushBuffer() 强制刷新
 *
 * <h2>📖 和 AI Tool 的区别</h2>
 * PDFGenerationTool 是给 AI Agent 调用的（生成 PDF 存到磁盘）。
 * ExportController 是给用户直接调用的 REST API（生成 PDF 并返回给浏览器下载）。
 * 两者用的是同一个 iText 底层库。
 *
 * <h2>📖 对话格式设计</h2>
 * 导出的 PDF 中每条消息的格式建议：
 * <pre>
 * ┌────────────────────────────────────────┐
 * │ 🧑 用户  (2024-01-15 14:30)           │
 * │ 怎么学 Java 并发编程？                  │
 * └────────────────────────────────────────┘
 * ┌────────────────────────────────────────┐
 * │ 🤖 AI 面试官                            │
 * │ 学并发编程首先要理解 JMM（Java 内存模型）│
 * │ ...                                    │
 * └────────────────────────────────────────┘
 * </pre>
 *
 * 💡 引导问题：
 * 1. 从哪里获取对话历史？FileBasedChatMemory.getConversation(chatId)
 * 2. 怎么区分用户消息和 AI 消息？（提示：看 messageType 或 message 的具体类型）
 * 3. iText 怎么创建不同样式的段落？（用户消息蓝色加粗，AI 消息黑色正常）
 * 4. Content-Disposition 的 attachment 和 inline 有什么区别？
 *    （attachment=下载，inline=浏览器中预览）
 * 5. PDF 中怎么处理中文？（提示：用 STSongStd-Light 字体，和 PDFGenerationTool 一样）
 * 6. 如果 chatId 对应的对话不存在，返回什么？
 * 7. PDF 生成后是写到临时文件还是直接写到 response 输出流？
 *    （直接写流：不需要磁盘临时文件，更高效，但生成失败时用户看不到任何东西）
 */
@Slf4j
@RestController
@RequestMapping("/export")
public class ExportController {

    @Resource(name = "fileBasedChatMemory")
    private FileBasedChatMemory chatMemory;

    // ===== 🎯 Task 15 Part A: 导出对话为 PDF =====
    // GET /export/pdf?chatId=xxx
    //
    // 步骤：
    // 1. 从 chatMemory 获取对话历史
    // 2. 如果对话为空→返回错误
    // 3. 遍历消息列表，用 iText 构建 PDF 内容
    // 4. 设置 response 头（Content-Type + Content-Disposition）
    // 5. 用 PdfWriter(response.getOutputStream()) 创建 PDF
    // 6. 遍历消息写入 PDF（Paragraph 或 Table）
    // 7. 关闭 Document + flush response
    //
    // 💡 iText 文档结构提示：
    //    PdfWriter writer = new PdfWriter(response.getOutputStream());
    //    PdfDocument pdf = new PdfDocument(writer);
    //    Document document = new Document(pdf);
    //    ...
    //    document.close(); // 关闭后自动 flush 到 response
    //
    // 你的代码写在这里 ↓


    // 你的代码写在这里 ↑


    // ===== 🎯 Task 15 Part B: 下载已生成的文件 =====
    // GET /export/download?filePath=xxx
    //
    // 步骤：
    // 1. 校验 filePath（防止路径穿越攻击！比如 ../../../etc/passwd）
    // 2. 检查文件是否存在
    // 3. 设置 Content-Type（根据文件类型，如 application/octet-stream）
    // 4. 设置 Content-Disposition: attachment; filename="xxx"
    // 5. Files.copy() 写入 response.getOutputStream()
    //
    // 💡 思考：路径穿越攻击是什么？
    //    如果用户传 filePath=../../application.yml，就能下载你的配置文件
    //    防御：检查路径中是否有 ".."，或者只允许特定目录下的文件
    //
    // 你的代码写在这里 ↓


    // 你的代码写在这里 ↑
}
