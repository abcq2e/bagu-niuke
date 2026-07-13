package com.qian.qianaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * PaddleOCR HTTP 调用服务
 * <p>
 * 需要先启动 PaddleOCR Python 服务：python paddle_ocr_server.py
 * 默认地址：http://localhost:8866
 */
@Service
@Slf4j
public class PaddleOcrService {

    private final RestClient restClient = RestClient.create();

    private static final String OCR_URL = "http://localhost:8866/ocr";
    private static final String HEALTH_URL = "http://localhost:8866/health";

    /**
     * 识别图片中的文字
     *
     * @param imageBytes 图片字节数组（支持 PNG/JPG/JPEG）
     * @param imageName  图片名称（用于日志）
     * @return OCR 识别出的文字，服务不可用时返回空字符串
     */
    public String ocr(byte[] imageBytes, String imageName) {
        try {
            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return imageName + ".png";
                }
            };

            LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(OCR_URL)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("text")) {
                String text = (String) response.get("text");
                int lines = response.containsKey("lines") ? (int) response.get("lines") : 0;
                if (!text.isBlank()) {
                    log.info("PaddleOCR 识别完成 [{}]: {} 行文字", imageName, lines);
                    return text;
                }
            }
            log.info("PaddleOCR [{}]: 未识别到文字", imageName);
            return "";
        } catch (Exception e) {
            log.debug("PaddleOCR 服务调用失败 [{}]: {}（服务可能未启动，跳过图片分析）",
                    imageName, e.getMessage());
            return "";
        }
    }

    /**
     * 检查 PaddleOCR 服务是否可用
     */
    public boolean isAvailable() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri(HEALTH_URL)
                    .retrieve()
                    .body(Map.class);
            return response != null && "ok".equals(response.get("status"));
        } catch (Exception e) {
            return false;
        }
    }
}
