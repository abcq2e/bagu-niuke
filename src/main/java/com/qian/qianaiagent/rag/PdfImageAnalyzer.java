package com.qian.qianaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 图片提取器和分析器
 * <p>
 * 从 PDF 文档中提取嵌入的图片（截图、图表、手写笔记等），进行文字识别：
 * <ol>
 *   <li>优先使用 PaddleOCR（本地免费，中文识别精准）</li>
 *   <li>备选 DashScope 多模态 AI（需要配置 API Key，能理解图表含义）</li>
 *   <li>都不可用时跳过图片分析，仅提取 PDF 文字</li>
 * </ol>
 */
@Component
@Slf4j
public class PdfImageAnalyzer {

    @Resource
    private PaddleOcrService paddleOcrService;

    @Resource
    private ImageAnalysisService imageAnalysisService;

    /**
     * 分析结果：图片描述 + 所在页码
     */
    public record ImageAnalysisResult(String description, int pageNumber, String imageName) {
    }

    /**
     * 提取 PDF 中所有图片并进行文字识别
     *
     * @param pdfBytes  PDF 文件字节数组
     * @param pdfName   PDF 文件名
     * @return 所有图片的分析结果列表
     */
    public List<ImageAnalysisResult> analyzeImages(byte[] pdfBytes, String pdfName) {
        List<ImageAnalysisResult> results = new ArrayList<>();

        // 确定使用哪种识别方式
        boolean usePaddleOcr = paddleOcrService.isAvailable();
        boolean useDashScope = imageAnalysisService.isAvailable();

        if (!usePaddleOcr && !useDashScope) {
            log.info("PaddleOCR 未启动、DashScope 未配置，跳过 {} 的图片分析（仅提取文字）", pdfName);
            log.info("提示：启动 PaddleOCR 服务以获得图片识别能力 -> python paddle_ocr_server.py");
            return results;
        }

        String mode = usePaddleOcr ? "PaddleOCR（本地）" : "DashScope（云端多模态）";
        log.info("图片识别模式: {}", mode);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            log.info("开始分析 PDF [{}] 中的图片，共 {} 页", pdfName, totalPages);

            int imageCount = 0;
            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                PDResources resources = page.getResources();

                if (resources == null) continue;

                // 遍历页面中的所有图片资源
                for (var cosName : resources.getXObjectNames()) {
                    if (!resources.isImageXObject(cosName)) continue;

                    try {
                        PDImageXObject image = (PDImageXObject) resources.getXObject(cosName);
                        BufferedImage bufferedImage = image.getImage();

                        // 跳过太小的图片（可能是图标/装饰元素）
                        if (bufferedImage.getWidth() < 100 || bufferedImage.getHeight() < 100) {
                            continue;
                        }

                        // 转为 PNG 字节数组
                        byte[] imageBytes = bufferedImageToPngBytes(bufferedImage);
                        String imageName = pdfName + "_p" + (pageIndex + 1) + "_" + imageCount;

                        // PaddleOCR 优先，DashScope 备选
                        String description;
                        if (usePaddleOcr) {
                            description = paddleOcrService.ocr(imageBytes, imageName);
                        } else {
                            description = imageAnalysisService.analyzeImage(imageBytes, imageName);
                        }

                        if (!description.isBlank()) {
                            results.add(new ImageAnalysisResult(description, pageIndex + 1, imageName));
                            imageCount++;
                        }
                    } catch (Exception e) {
                        log.warn("PDF [{}] 第 {} 页图片处理失败: {}", pdfName, pageIndex + 1, e.getMessage());
                    }
                }
            }
            log.info("PDF [{}] 图片分析完成，共识别 {} 张图片", pdfName, results.size());
        } catch (IOException e) {
            log.error("PDF [{}] 解析失败: {}", pdfName, e.getMessage());
        }
        return results;
    }

    /**
     * BufferedImage → PNG 字节数组
     */
    private byte[] bufferedImageToPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        return os.toByteArray();
    }
}
