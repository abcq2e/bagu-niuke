package com.qian.qianaiagent.app;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🔴 [终版-双链路] 离线异常队列记录
 * <p>
 * 存储需要离线仲裁的分类样本，数据来源包括：
 * <ul>
 *   <li>维度名验证失败（幻觉维度名）</li>
 *   <li>双链路标签冲突（CONFLICT）</li>
 *   <li>评分 AI 维度标注自评低置信</li>
 *   <li>LLM 丢失 DIM 标签</li>
 *   <li>降级层生成的临时数据</li>
 * </ul>
 * 实时流程只写入不处理，由离线仲裁任务消费。
 * </p>
 */
public class StagingRecord {

    private final String id;
    private final String fingerprint;
    private final String weakPointText;
    private final String topic;
    private final List<String> linkADimensions;
    private final List<String> linkBDimensions;
    private final ConfidenceLevel confidence;
    private final String reason;
    private final LocalDateTime createdAt;
    private boolean resolved;

    public StagingRecord(String id, String fingerprint, String weakPointText,
                         String topic, List<String> linkADimensions,
                         List<String> linkBDimensions,
                         ConfidenceLevel confidence, String reason) {
        this.id = id;
        this.fingerprint = fingerprint;
        this.weakPointText = weakPointText;
        this.topic = topic;
        this.linkADimensions = linkADimensions;
        this.linkBDimensions = linkBDimensions;
        this.confidence = confidence;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
        this.resolved = false;
    }

    public String getId() { return id; }
    public String getFingerprint() { return fingerprint; }
    public String getWeakPointText() { return weakPointText; }
    public String getTopic() { return topic; }
    public List<String> getLinkADimensions() { return linkADimensions; }
    public List<String> getLinkBDimensions() { return linkBDimensions; }
    public ConfidenceLevel getConfidence() { return confidence; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}
