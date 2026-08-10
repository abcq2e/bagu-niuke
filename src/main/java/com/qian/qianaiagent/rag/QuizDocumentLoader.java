package com.qian.qianaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.qian.qianaiagent.app.SequentialRotationService;
import com.qian.qianaiagent.app.TopicRotationService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识点考察应用文档加载器
 * <p>
 * 支持加载用户笔记/知识点的 Markdown（.md）和 PDF（.pdf）文档：
 * <ul>
 *   <li>Markdown：直接提取文本内容</li>
 *   <li>PDF：提取文字 + AI 识别嵌入图片（截图/图表/手写笔记）</li>
 * </ul>
 * 自动提取分类元数据用于后续过滤检索。
 * <p>
 * 使用方法：将你的笔记 PDF 或 Markdown 文件放到
 * src/main/resources/document/ 目录下即可自动加载
 */
@Component
@Slf4j
public class QuizDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    @jakarta.annotation.Resource
    private PdfImageAnalyzer pdfImageAnalyzer;

    public QuizDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载 document 目录下的所有文档（Markdown + PDF）
     *
     * @return 文档列表（含分类元数据）
     */
    public List<Document> loadDocuments() {
        List<Document> allDocuments = new ArrayList<>();
        allDocuments.addAll(loadMarkdowns());
        allDocuments.addAll(loadPdfs());
        log.info("共加载 {} 篇文档", allDocuments.size());
        return allDocuments;
    }

    /**
     * 加载 document 目录下的所有 Markdown 文档
     */
    public List<Document> loadMarkdowns() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("加载 Markdown 文档: {}", filename);
                // 用文件名前缀作为分类标签（如 "八股-Java并发.md" → "八股"）
                String category = extractCategory(filename);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("category", category)
                        .withAdditionalMetadata("topic", extractTopic(filename))
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                documents.addAll(reader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return documents;
    }

    /**
     * 加载 document 目录下的所有 PDF 文档
     * <p>
     * 分两步处理：
     * 1. 提取文字（PagePdfDocumentReader，每页为一个 Document）
     * 2. 提取嵌入图片 → AI 多模态识别 → 附加到对应页 Document
     */
    public List<Document> loadPdfs() {
        List<Document> documents = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.pdf");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                log.info("加载 PDF 文档: {}", filename);
                String category = extractCategory(filename);

                // === 第 1 步：提取文字 ===
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)  // 每页作为一个 Document
                        .build();
                PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
                List<Document> pdfDocs = reader.read();

                // === 第 2 步：提取图片 + AI 识别 ===
                try {
                    byte[] pdfBytes = resource.getContentAsByteArray();
                    List<PdfImageAnalyzer.ImageAnalysisResult> imageResults =
                            pdfImageAnalyzer.analyzeImages(pdfBytes, filename);

                    // 将图片描述合并到对应页的 Document 中
                    for (PdfImageAnalyzer.ImageAnalysisResult imageResult : imageResults) {
                        int pageIndex = imageResult.pageNumber() - 1; // 0-based
                        if (pageIndex < pdfDocs.size()) {
                            Document doc = pdfDocs.get(pageIndex);
                            String originalText = doc.getText();
                            String enrichedText = originalText +
                                    "\n\n--- 图片内容（AI 识别）---\n" +
                                    imageResult.description();
                            doc.getMetadata().put("imageDescription", imageResult.description());
                            log.info("  第 {} 页图片已识别，描述存入 metadata", imageResult.pageNumber());
                            log.info("  第 {} 页图片已识别并合并", imageResult.pageNumber());
                        }
                    }
                } catch (Exception e) {
                    log.warn("PDF [{}] 图片分析异常，仅使用文字内容: {}", filename, e.getMessage());
                }

                // 为每页附加元数据
                for (Document doc : pdfDocs) {
                    doc.getMetadata().put("filename", filename);
                    doc.getMetadata().put("category", category);
                    doc.getMetadata().put("topic", extractTopic(filename));
                }
                documents.addAll(pdfDocs);
            }
        } catch (IOException e) {
            log.error("PDF 文档加载失败", e);
        }
        return documents;
    }

    /**
     * 从文件名前缀提取分类标签
     * <p>
     * 文件命名规范：<b>分类-文档名.md</b>，分类名取第一个 "-" 之前的部分。
     * <pre>
     *   "八股-Java并发编程知识点.md"  →  "八股"
     *   "Agent-MCP.md"              →  "Agent"
     *   "实践-落地实践.md"            →  "实践"
     *   "没有前缀的文件.md"           →  "default"
     * </pre>
     */
    private String extractCategory(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "default";
        }
        int dashIndex = filename.indexOf('-');
        if (dashIndex <= 0) {
            return "default";
        }
        return filename.substring(0, dashIndex);
    }

    /**
     * 从文件名提取二级主题：用 {@link SequentialRotationService#topicFromFilename} 优先匹配，
     * 匹配失败才 fallback 到文件名截取。
     * "bagu-java-concurrency.md" → "Java并发"（通过逆向映射）
     * "面渣逆袭-并发编程.md" → "Java并发"
     */
    private String extractTopic(String filename) {
        // 🔴 [Hotfix-RAG联动] 优先用已知映射获取正确的方向名
        String mapped = SequentialRotationService.topicFromFilename(filename);
        if (!"default".equals(mapped)) {
            return mapped;
        }
        // 未知文件，fallback 到文件名截取
        if (filename == null || filename.isEmpty()) {
            return "default";
        }
        int dashIndex = filename.indexOf('-');
        int dotIndex = filename.lastIndexOf('.');
        if (dashIndex <= 0 || dotIndex <= dashIndex) {
            return "default";
        }
        return filename.substring(dashIndex + 1, dotIndex);
    }
}
