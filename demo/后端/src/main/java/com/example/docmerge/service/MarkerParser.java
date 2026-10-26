package com.example.docmerge.service;

import com.example.docmerge.dto.TagInfo;
import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Br;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.Pict;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 DOCX 中的 ${name-start} / ${name-end} 标识，并完成跨 run 的精确文本截取与删除。
 *
 * <p>Word 经常把一个标识拆到多个 w:r / w:t 中，因此本类先把段落内所有 w:t 文本拼接起来做匹配，
 * 再用“段落内绝对字符偏移”回写到各个 Text 节点。这样不需要破坏原有格式。</p>
 */
@Component
public class MarkerParser {

    private static final Logger log = LoggerFactory.getLogger(MarkerParser.class);

    /** 合法区域标识：字母、数字、下划线，名称区分大小写。 */
    public static final Pattern TAG_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z0-9_]+)-(start|end)}");

    /** 用于清除合并结果中的标记（包含 merge-point 与 toc 这类特殊标记）。 */
    private static final Pattern ANY_MARKER_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z0-9_]+)-(?:start|end)}|\\$\\{merge-point}|\\$\\{toc}");

    public static final String MERGE_POINT = "${merge-point}";
    public static final String TOC_MARKER = "${toc}";

    /**
     * 分析一个副文档 body 中所有顶层段落里的标识。
     */
    public MarkerAnalysis analyze(WordprocessingMLPackage pkg) {
        List<Object> blocks = new ArrayList<>(pkg.getMainDocumentPart().getContent());
        List<ParagraphInfo> paragraphs = new ArrayList<>();
        List<MarkerOccurrence> occurrences = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            Object block = XmlUtils.unwrap(blocks.get(i));
            if (!(block instanceof P p)) {
                continue;
            }
            String text = paragraphText(p);
            List<MarkerOccurrence> paragraphMarkers = new ArrayList<>();
            Matcher matcher = TAG_PATTERN.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                boolean start = "start".equals(matcher.group(2));
                MarkerOccurrence occurrence = new MarkerOccurrence(
                        name, start, i, matcher.start(), matcher.end(), text);
                paragraphMarkers.add(occurrence);
                occurrences.add(occurrence);
            }
            paragraphs.add(new ParagraphInfo(i, p, text, paragraphMarkers));
        }

        List<TagIssue> issues = new ArrayList<>();
        Map<String, MarkerOccurrence> open = new HashMap<>();
        List<CandidatePair> candidates = new ArrayList<>();

        // 按文档顺序配对：每个 start 与它之后最近的同名 end 配对。
        for (MarkerOccurrence occ : occurrences) {
            if (occ.start()) {
                MarkerOccurrence previous = open.put(occ.name(), occ);
                if (previous != null) {
                    issues.add(new TagIssue(occ.name(), "DUPLICATE_START", occ.blockIndex(),
                            "标识 [" + occ.name() + "] 出现重复 start（可能是嵌套或复制粘贴），已保留最新的 start"));
                    log.warn("重复 start: name={}, 段落={}", occ.name(), occ.blockIndex());
                }
            } else {
                MarkerOccurrence start = open.remove(occ.name());
                if (start == null) {
                    issues.add(new TagIssue(occ.name(), "MISSING_START", occ.blockIndex(),
                            "标识 [" + occ.name() + "] 的 end 没有对应的 start，已跳过"));
                    log.warn("孤立 end: name={}, 段落={}", occ.name(), occ.blockIndex());
                } else {
                    candidates.add(new CandidatePair(start, occ));
                }
            }
        }
        for (MarkerOccurrence value : open.values()) {
            issues.add(new TagIssue(value.name(), "MISSING_END", value.blockIndex(),
                    "标识 [" + value.name() + "] 缺少 end，已跳过该区域"));
            log.warn("缺少 end: name={}, 段落={}", value.name(), value.blockIndex());
        }

        // 按起始位置排序，并剔除互相重叠（嵌套）的区域，避免同一段内容被重复复制。
        candidates.sort(Comparator
                .comparingInt((CandidatePair c) -> c.start.blockIndex())
                .thenComparingInt(c -> c.start.startOffset()));
        List<TagPair> pairs = new ArrayList<>();
        CandidatePair previousAccepted = null;
        for (CandidatePair candidate : candidates) {
            if (previousAccepted != null
                    && compare(candidate.start.blockIndex(), candidate.start.startOffset(),
                    previousAccepted.end.blockIndex(), previousAccepted.end.startOffset()) < 0) {
                issues.add(new TagIssue(candidate.start.name(), "OVERLAP", candidate.start.blockIndex(),
                        "标识 [" + candidate.start.name() + "] 与其他标识区域重叠（嵌套），已跳过该区域"));
                log.warn("标识区域重叠，跳过: name={}, start={}:{}, previousEnd={}:{}",
                        candidate.start.name(), candidate.start.blockIndex(), candidate.start.startOffset(),
                        previousAccepted.end.blockIndex(), previousAccepted.end.startOffset());
                continue;
            }
            TagPair pair = new TagPair(
                    candidate.start.name(),
                    candidate.start.blockIndex(), candidate.start.startOffset(),
                    candidate.end.blockIndex(), candidate.end.startOffset(),
                    textPreview(candidate.start.paragraphText()),
                    textPreview(candidate.end.paragraphText()));
            pairs.add(pair);
            previousAccepted = candidate;
            log.info("发现标识对: name={}, start={}:{}, end={}:{}", pair.name(),
                    pair.startBlock(), pair.startOffset(), pair.endBlock(), pair.endOffset());
        }

        List<TagInfo> tags = new ArrayList<>();
        for (TagPair pair : pairs) {
            tags.add(new TagInfo(pair.name(), "PAIRED", null,
                    pair.startBlock(), pair.endBlock(), pair.startPreview(), "已配对，可参与合并"));
        }
        for (TagIssue issue : issues) {
            tags.add(new TagInfo(issue.name(), issue.status(), null,
                    issue.blockIndex(), null, null, issue.message()));
        }
        tags.sort(Comparator
                .comparing((TagInfo t) -> t.startParagraph() == null ? Integer.MAX_VALUE : t.startParagraph())
                .thenComparing(TagInfo::name));

        return new MarkerAnalysis(blocks, paragraphs, pairs, issues, tags, occurrences.size());
    }

    /**
     * 从段落中截取 [startOffset, endOffset) 文本范围。
     *
     * <p>实现方式是深拷贝整段，然后删除范围外的文本；这样 pPr、rPr、超链接、图片等结构都能保留。
     * 如果范围外只剩文本，结果就是精确的 run 片段。</p>
     */
    public P slice(P source, int startOffset, int endOffset) {
        String text = paragraphText(source);
        int length = text.length();
        int start = Math.max(0, Math.min(startOffset, length));
        int end = Math.max(start, Math.min(endOffset, length));

        P copy = XmlUtils.deepCopy(source);
        List<TextRange> deletes = new ArrayList<>();
        if (start > 0) {
            deletes.add(new TextRange(0, start));
        }
        if (end < length) {
            deletes.add(new TextRange(end, length));
        }
        applyDeletions(copy, deletes);
        return copy;
    }

    /**
     * 删除段落中绝对偏移范围内的文本，保留其余 XML 与格式。
     */
    public void deleteRange(P paragraph, int startOffset, int endOffset) {
        if (endOffset <= startOffset) {
            return;
        }
        applyDeletions(paragraph, List.of(new TextRange(startOffset, endOffset)));
    }

    /**
     * 删除段落中的全部标记文本，用于防止标识泄漏到合并结果中。
     */
    public boolean stripMarkers(P paragraph) {
        String text = paragraphText(paragraph);
        Matcher matcher = ANY_MARKER_PATTERN.matcher(text);
        List<TextRange> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new TextRange(matcher.start(), matcher.end()));
        }
        if (ranges.isEmpty()) {
            return false;
        }
        applyDeletions(paragraph, ranges);
        return true;
    }

    /**
     * 查找字面量（例如 ${merge-point}）在段落文本中的偏移。
     */
    public int indexOfLiteral(P paragraph, String literal) {
        return paragraphText(paragraph).indexOf(literal);
    }

    /**
     * 段落是否只剩空白。仅当没有文本且没有图片/分页符等非文本内容时返回 true。
     */
    public boolean isBlank(P paragraph) {
        if (!paragraphText(paragraph).isBlank()) {
            return false;
        }
        return !hasNonTextContent(paragraph);
    }

    public String paragraphText(P paragraph) {
        List<Text> texts = collectTextNodes(paragraph);
        StringBuilder sb = new StringBuilder();
        for (Text text : texts) {
            if (text.getValue() != null) {
                sb.append(text.getValue());
            }
        }
        return sb.toString();
    }

    /**
     * 收集段落内所有 w:t（按文档顺序），跳过图片内部文本。
     */
    public List<Text> collectTextNodes(P paragraph) {
        List<Text> result = new ArrayList<>();
        collectTextNodes(paragraph, result);
        return result;
    }

    private void collectTextNodes(Object node, List<Text> result) {
        Object value = XmlUtils.unwrap(node);
        if (value instanceof Text text) {
            result.add(text);
            return;
        }
        if (value instanceof Drawing || value instanceof Pict) {
            return;
        }
        List<Object> children = TraversalUtil.getChildrenImpl(value);
        if (children == null) {
            return;
        }
        for (Object child : new ArrayList<>(children)) {
            collectTextNodes(child, result);
        }
    }

    private boolean hasNonTextContent(Object node) {
        Object value = XmlUtils.unwrap(node);
        if (value instanceof Drawing || value instanceof Pict || value instanceof Br
                || value instanceof R.Tab) {
            return true;
        }
        List<Object> children = TraversalUtil.getChildrenImpl(value);
        if (children == null) {
            return false;
        }
        for (Object child : new ArrayList<>(children)) {
            if (hasNonTextContent(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按绝对字符偏移删除文本。ranges 必须已按起点排序。
     */
    private void applyDeletions(P paragraph, List<TextRange> ranges) {
        if (ranges.isEmpty()) {
            return;
        }
        List<TextRange> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparingInt(TextRange::start));

        List<Text> texts = collectTextNodes(paragraph);
        int offset = 0;
        for (Text text : texts) {
            String value = text.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            int textStart = offset;
            StringBuilder kept = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                int absolute = textStart + i;
                if (!isDeleted(absolute, sorted)) {
                    kept.append(value.charAt(i));
                }
            }
            if (kept.length() != value.length()) {
                text.setValue(kept.toString());
            }
            offset = textStart + value.length();
        }
    }

    private boolean isDeleted(int absoluteOffset, List<TextRange> ranges) {
        for (TextRange range : ranges) {
            if (absoluteOffset >= range.start() && absoluteOffset < range.end()) {
                return true;
            }
            if (range.start() > absoluteOffset) {
                return false;
            }
        }
        return false;
    }

    private static int compare(int block1, int offset1, int block2, int offset2) {
        int byBlock = Integer.compare(block1, block2);
        return byBlock != 0 ? byBlock : Integer.compare(offset1, offset2);
    }

    private static String textPreview(String text) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 60) {
            return cleaned;
        }
        return cleaned.substring(0, 60) + "...";
    }

    public record MarkerOccurrence(String name, boolean start, int blockIndex,
                                   int startOffset, int endOffset, String paragraphText) {
    }

    public record TagPair(String name, int startBlock, int startOffset, int endBlock, int endOffset,
                          String startPreview, String endPreview) {
    }

    public record TagIssue(String name, String status, int blockIndex, String message) {
    }

    public record ParagraphInfo(int blockIndex, P paragraph, String text,
                                List<MarkerOccurrence> markers) {
    }

    public record MarkerAnalysis(List<Object> blocks, List<ParagraphInfo> paragraphs,
                                 List<TagPair> pairs, List<TagIssue> issues,
                                 List<TagInfo> tags, int occurrenceCount) {

        public ParagraphInfo paragraphAt(int blockIndex) {
            return paragraphs.stream()
                    .filter(p -> p.blockIndex() == blockIndex)
                    .findFirst()
                    .orElse(null);
        }

        public Set<String> pairNames() {
            Set<String> names = new LinkedHashSet<>();
            for (TagPair pair : pairs) {
                names.add(pair.name());
            }
            return names;
        }
    }

    private record CandidatePair(MarkerOccurrence start, MarkerOccurrence end) {
    }

    private record TextRange(int start, int end) {
    }
}
