package com.qian.qianaiagent.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.qian.qianaiagent.knowledge.TopicDimensions;

/**
 * 🔴 [终版-双链路] 维度验证与一致性门控
 * <p>
 * 核心职责（不实时修正，仅验证/标记/放行）：
 * <ul>
 *   <li>提取 AI 回复中的 {@code [DIM:xxx]} 标签</li>
 *   <li>验证维度名是否存在于受控枚举白名单</li>
 *   <li>执行双链路互证（链路A DIM vs 链路B评分标注）</li>
 *   <li>自动划分 4 档置信度标记，随数据落库</li>
 * </ul>
 * <p>
 * 不引入任何关键词表、不做语义匹配、不实时篡改 LLM 输出。
 */
public final class DimensionValidator {

    private DimensionValidator() {}

    /** [DIM:xxx] 标记提取正则 */
    private static final Pattern DIM_PATTERN =
            Pattern.compile("\\[DIM:([^\\]]+)\\]", Pattern.CASE_INSENSITIVE);

    /**
     * 从 AI 回复中提取所有 {@code [DIM:xxx]} 标签，按出现顺序返回。
     *
     * @param aiResponse AI 回复全文
     * @return 提取到的标签名列表（去前后空格，不含重复），可能为空
     */
    public static List<String> extractDimTags(String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) return List.of();
        Matcher matcher = DIM_PATTERN.matcher(aiResponse);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String tag = matcher.group(1).trim();
            if (!tag.isEmpty() && seen.add(tag.toLowerCase())) {
                result.add(tag);
            }
        }
        return result;
    }

    /**
     * 验证维度名是否存在于指定方向的受控白名单中。
     * <p>
     * 匹配规则（从左到右尝试）：
     * <ol>
     *   <li>精确等于完整维度名（含括号）</li>
     *   <li>以维度名主体开头（括号前部分，如 "锁机制" 匹配 "锁机制（synchronized/...）"）</li>
     *   <li>包含在维度名中（如 "锁" 匹配 "锁机制（...）"）</li>
     * </ol>
     * 不模糊匹配、不推断、不纠正。只回答「存在/不存在」。
     *
     * @param topic   方向名
     * @param dimName AI 输出的 DIM 标签名
     * @return true 如果存在于白名单
     */
    public static boolean isValidDimensionName(String topic, String dimName) {
        if (topic == null || dimName == null || dimName.isBlank()) return false;
        List<String> dims = TopicDimensions.getDimensions(topic);
        if (dims.isEmpty()) return false;

        for (String dim : dims) {
            if (dim.equals(dimName)) return true;
            String subject = TopicDimensions.dimensionSubject(dim);
            if (!subject.isEmpty() && (dim.startsWith(dimName) || dimName.startsWith(subject))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从原始 DIM 标签列表中过滤出合法维度名。
     * <p>
     * 这是「维度名验证门」的核心方法：
     * 合法标签 → 放行；非法标签（幻觉维度名）→ 丢弃，不入 stats。
     *
     * @param topic   方向名
     * @param rawTags AI 原始输出的 DIM 标签列表
     * @return 白名单中存在匹配的维度名列表（完整维度名，非标签原文）
     */
    public static List<String> filterValidDimensions(String topic, List<String> rawTags) {
        if (topic == null || rawTags == null || rawTags.isEmpty()) return List.of();
        List<String> dims = TopicDimensions.getDimensions(topic);
        if (dims.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String tag : rawTags) {
            String matched = findMatchingDimension(tag, dims);
            if (matched != null && seen.add(matched)) {
                result.add(matched);
            }
        }
        return result;
    }

    /**
     * 双链路互证门：根据出题 LLM（链路A）和评分 LLM（链路B）的输出，
     * 自动划分置信度等级。
     * <p>
     * 规则：
     * <table>
     *   <tr><th>场景</th><th>置信度</th></tr>
     *   <tr><td>A 和 B 均有有效维度，且存在交集</td><td>HIGH</td></tr>
     *   <tr><td>A 空（自由出题），B 有效</td><td>MEDIUM</td></tr>
     *   <tr><td>A 有效但 B 为空，或 B 自评 low 置信</td><td>LOW</td></tr>
     *   <tr><td>A 和 B 均有效但无交集</td><td>CONFLICT</td></tr>
     *   <tr><td>所有标签均未通过维度名验证</td><td>INVALID</td></tr>
     * </table>
     *
     * @param linkAValidDims 链路A通过验证的维度列表
     * @param linkBDims      链路B评分AI输出的维度数组（原始值）
     * @param bSelfConfidence 链路B自评置信度（"high"/"medium"/"low"），可为 null
     * @return 分类结果（含置信度、采用的维度、来源）
     */
    public static DimensionClassification classify(
            List<String> linkAValidDims,
            List<String> linkBDims,
            String bSelfConfidence) {

        boolean aValid = linkAValidDims != null && !linkAValidDims.isEmpty();
        boolean bValid = linkBDims != null && !linkBDims.isEmpty();
        boolean bLowConf = "low".equalsIgnoreCase(bSelfConfidence);
        boolean bMediumConf = "medium".equalsIgnoreCase(bSelfConfidence);

        // 场景 1：A 和 B 均有值
        if (aValid && bValid) {
            // 检查交集
            List<String> intersection = new ArrayList<>(linkAValidDims);
            intersection.retainAll(linkBDims);

            if (!intersection.isEmpty()) {
                if (bLowConf || bMediumConf) {
                    // 交集存在但 B 自评不自信 → LOW（不直接信任）
                    return new DimensionClassification(
                            intersection, ConfidenceLevel.LOW, "link_a+link_b",
                            linkAValidDims, linkBDims);
                }
                // A 和 B 一致且 B 自信 → HIGH
                return new DimensionClassification(
                        intersection, ConfidenceLevel.HIGH, "link_a+link_b",
                        linkAValidDims, linkBDims);
            } else {
                // A 和 B 都有值但无交集 → CONFLICT
                // 优先使用链路A（出题AI的标签，更贴近实际出的题）
                return new DimensionClassification(
                        linkAValidDims, ConfidenceLevel.CONFLICT, "link_a",
                        linkAValidDims, linkBDims);
            }
        }

        // 场景 2：自由出题（A 空，B 有效）
        if (!aValid && bValid) {
            return new DimensionClassification(
                    linkBDims, ConfidenceLevel.MEDIUM, "link_b",
                    linkAValidDims, linkBDims);
        }

        // 场景 3：仅有 A 值（B 缺失）
        if (aValid && !bValid) {
            return new DimensionClassification(
                    linkAValidDims, ConfidenceLevel.LOW, "link_a",
                    linkAValidDims, linkBDims);
        }

        // 场景 4：A 和 B 均无有效值 → INVALID
        return DimensionClassification.empty();
    }

    /**
     * 判断指定的 DIM 标签名是否属于「幻觉维度」（不在任何已知方向的维度白名单中）。
     *
     * @param topic   方向名
     * @param dimName 要检查的维度名
     * @return true 如果是幻觉维度名
     */
    public static boolean isHallucinatedDimension(String topic, String dimName) {
        return !isValidDimensionName(topic, dimName);
    }

    // ===== 内部辅助方法 =====

    /**
     * 在维度列表中寻找与标签名匹配的完整维度名。
     * 匹配优先级：完整相等 → 标签是维度名一部分 → 维度主体名包含标签。
     */
    private static String findMatchingDimension(String tag, List<String> dims) {
        String trimmed = tag.trim();
        for (String dim : dims) {
            if (dim.equals(trimmed)) return dim;
            String subject = TopicDimensions.dimensionSubject(dim);
            if (dim.startsWith(trimmed)) return dim;
            if (!subject.isEmpty() && trimmed.startsWith(subject)) return dim;
        }
        return null;
    }
}
