package com.qian.qianaiagent.knowledge;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 🔴 [终版-双链路] Ground Truth 真值记录
 * <p>
 * 存储经人工/异构模型仲裁后的维度真值，优先级高于 LLM 原始输出。
 * 通过弱点评指纹（fingerprint）精确匹配，不做模糊查询。
 * </p>
 */
public class GroundTruthRecord {

    /** Ground Truth 指纹 key（弱点评归一化后取前 10 字 + md5[:6]） */
    private final String fingerprint;

    /** 审核确认的维度名 */
    private final String dimension;

    /** 数据来源：human / cross_model / batch_llm */
    private final String source;

    /** 创建时间 */
    private final LocalDateTime createdAt;

    /** 是否仍有效 */
    private boolean active;

    public GroundTruthRecord(String fingerprint, String dimension, String source) {
        this.fingerprint = Objects.requireNonNull(fingerprint);
        this.dimension = Objects.requireNonNull(dimension);
        this.source = Objects.requireNonNull(source);
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public String getFingerprint() { return fingerprint; }
    public String getDimension() { return dimension; }
    public String getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /**
     * 从弱点评原文生成 Ground Truth 指纹。
     * <p>
     * 规则：去除标点、英文转小写、取前 10 字 + md5[:6]。
     * 确保相同知识点的不同表述归入同一指纹。
     */
    public static String buildFingerprint(String weakPointText) {
        if (weakPointText == null || weakPointText.isBlank()) return "";
        try {
            // 去标点，英文转小写
            StringBuilder normalized = new StringBuilder();
            for (char c : weakPointText.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    normalized.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : c);
                }
            }
            String result = normalized.toString().trim();
            String prefix = result.length() > 10 ? result.substring(0, 10) : result;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(digest).substring(0, 6);
            return prefix + "_" + hash;
        } catch (NoSuchAlgorithmException e) {
            String cleaned = weakPointText.replaceAll("[^\\p{L}\\p{N}]", "").trim();
            return cleaned.length() > 16 ? cleaned.substring(0, 16) : cleaned;
        }
    }
}
