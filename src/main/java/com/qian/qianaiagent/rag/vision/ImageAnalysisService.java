package com.qian.qianaiagent.rag.vision;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 图片 AI 分析服务
 * <p>
 * 使用阿里云 DashScope 通义千问 VL（qwen-vl-max）多模态模型
 * 识别图片中的文字、图表、截图等内容，生成文本描述
 */
@Service
@Slf4j
public class ImageAnalysisService {

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    private final RestClient restClient = RestClient.create();

    /**
     * 分析图片内容，返回文字描述
     *
     * @param imageBytes 图片字节数据
     * @param imageName  图片文件名（用于日志）
     * @return AI 识别的图片文字描述
     */
    public String analyzeImage(byte[] imageBytes, String imageName) {
        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String imageUrl = "data:image/png;base64," + base64Image;

            Map<String, Object> requestBody = Map.of(
                    "model", "qwen-vl-max",
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)),
                                    Map.of("type", "text", "text", "请详细描述这张图片中的所有内容，包括：1)图片中的文字 2)图表/表格内容 3)流程图/架构图的内容 4)代码片段。如果是截图，请OCR识别所有可见文字。越详细越好。")
                            ))
                    ),
                    "max_tokens", 2000
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                    .header("Authorization", "Bearer " + dashScopeApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    log.info("图片分析完成 [{}]: {} 字符", imageName, content.length());
                    return content;
                }
            }
            log.warn("图片分析返回空结果 [{}]", imageName);
            return "";
        } catch (Exception e) {
            log.error("图片分析失败 [{}]: {}", imageName, e.getMessage());
            return "[图片未能识别: " + imageName + "]";
        }
    }

    /**
     * 检查服务是否可用（API Key 已配置）
     */
    public boolean isAvailable() {
        return dashScopeApiKey != null
                && !dashScopeApiKey.isBlank()
                && !dashScopeApiKey.startsWith("sk-your-");
    }
}
