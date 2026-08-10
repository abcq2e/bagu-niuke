package com.qian.qianaiagent.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 知识库中的单条可考知识点（题干级）。
 */
public record KnowledgePoint(
        String id,
        String topic,
        String dimension,
        String stem,
        String source,
        String labelSource
) {
    public static final String UNCLASSIFIED = "__UNCLASSIFIED__";
    public static final String LABEL_KEYWORD = "keyword";
    public static final String LABEL_LLM = "llm";
    public static final String LABEL_UNCLASSIFIED = "unclassified";

    public static String normalizeStem(String stem) {
        if (stem == null) return "";
        return stem.trim().replaceAll("\\s+", " ");
    }

    /** 稳定 ID：topic + 规范化题干 → SHA-256 前 16 hex */
    public static String stableId(String topic, String stem) {
        String raw = (topic == null ? "" : topic) + "\0" + normalizeStem(stem);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
