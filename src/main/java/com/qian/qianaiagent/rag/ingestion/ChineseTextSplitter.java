package com.qian.qianaiagent.rag.ingestion;

import org.springframework.ai.transformer.splitter.TextSplitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文友好的文档切分器。
 *
 * <p>Spring AI 自带的 {@code TokenTextSplitter} 只识别 ASCII 标点（. ? ! 换行），
 * 对中文八股文会退化成按 token 数硬切，且不支持 chunk 重叠。本类继承 {@link TextSplitter}
 * 重写 {@link #splitText(String)}，实现：
 * <ul>
 *   <li>按中文句末标点（。！？；）拆句，句子不会被拦腰切断；</li>
 *   <li>按「字符数」累计成 chunk（中文场景 1 字 ≈ 1 token，与模型分词近似）；</li>
 *   <li>相邻 chunk 之间保留 overlap 字符的完整句子，缓解上下文断裂。</li>
 * </ul>
 *
 * <p>继承 {@link TextSplitter} 后，{@code apply(List<Document>)} 会自动为每个切出的
 * 片段复制父文档的 metadata（filename / topic 等），调用方无需额外处理。
 */
public class ChineseTextSplitter extends TextSplitter {

    /** 句末标点（正向后顾，拆分后标点保留在句子末尾）。 */
    private static final String SENTENCE_END_REGEX = "(?<=[。！？；.!?;])";

    /** 目标 chunk 大小（字符数）。 */
    private final int chunkSizeChars;

    /** 相邻 chunk 重叠的字符数（实际按完整句子对齐，且最多取前一块的 size-1 句）。 */
    private final int overlapChars;

    /** 短于此长度（含）的 chunk 直接丢弃，用于过滤标题残留、空行等碎片。 */
    private final int minChunkChars;

    public ChineseTextSplitter(int chunkSizeChars, int overlapChars, int minChunkChars) {
        this.chunkSizeChars = chunkSizeChars;
        this.overlapChars = overlapChars;
        this.minChunkChars = minChunkChars;
    }

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> sentences = splitSentences(text);
        if (sentences.isEmpty()) {
            return List.of();
        }
        // 第一步：按字符数把句子分组为「不重叠」的块
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int length = 0;
        for (String sentence : sentences) {
            if (!current.isEmpty() && length + sentence.length() > chunkSizeChars) {
                groups.add(current);
                current = new ArrayList<>();
                length = 0;
            }
            current.add(sentence);
            length += sentence.length();
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        // 第二步：给每个块（除第一块外）前补上「前一块末尾的 overlap 句子」
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<String> prefix = (i == 0) ? List.<String>of() : overlapTail(groups.get(i - 1));
            List<String> combined = new ArrayList<>(prefix);
            combined.addAll(groups.get(i));
            chunks.add(join(combined));
        }
        return chunks.stream()
                .filter(chunk -> chunk.length() > minChunkChars)
                .toList();
    }

    /** 按句末标点拆分，丢弃空白片段。 */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String part : text.split(SENTENCE_END_REGEX)) {
            if (!part.isBlank()) {
                sentences.add(part);
            }
        }
        return sentences;
    }

    /**
     * 从前一块末尾取 overlap 字符对应的完整句子，作为下一块的前缀。
     * <p>最多取到「倒数第二句」（index &gt;= 1），保证前一块至少有第一句不参与重叠，
     * 从而避免 overlap 覆盖整个块造成的重复。
     */
    private List<String> overlapTail(List<String> group) {
        if (overlapChars <= 0 || group.size() <= 1) {
            return new ArrayList<>();
        }
        List<String> tail = new ArrayList<>();
        int length = 0;
        for (int i = group.size() - 1; i >= 1 && length < overlapChars; i--) {
            tail.add(0, group.get(i));
            length += group.get(i).length();
        }
        return tail;
    }

    private static String join(List<String> sentences) {
        return String.join("", sentences).trim();
    }
}
