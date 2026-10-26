package com.example.docmerge.service;

import com.example.docmerge.dto.TagInfo;
import com.example.docmerge.support.TestDocuments;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarkerParser 单元测试：覆盖跨 run、跨段落、配对告警、精确删除与格式保留。
 */
class MarkerParserTest {

    private final MarkerParser parser = new MarkerParser();

    @Test
    @DisplayName("跨 run 拆分的标记可以识别，截取后保留粗体格式")
    void shouldExtractRegionSplitAcrossRuns() throws Exception {
        P paragraph = TestDocuments.paragraph(
                TestDocuments.run("前缀${demo-"),
                TestDocuments.run("start}Hello "),
                TestDocuments.bold("World"),
                TestDocuments.run("${demo-end}后缀"));
        WordprocessingMLPackage pkg = packageWith(paragraph);

        MarkerParser.MarkerAnalysis analysis = parser.analyze(pkg);

        assertThat(analysis.pairs()).hasSize(1);
        MarkerParser.TagPair pair = analysis.pairs().get(0);
        assertThat(pair.name()).isEqualTo("demo");
        assertThat(pair.startBlock()).isZero();
        assertThat(pair.endBlock()).isZero();

        String text = parser.paragraphText(paragraph);
        int contentStart = text.indexOf("Hello");
        int contentEnd = text.indexOf("${demo-end}");
        P sliced = parser.slice(paragraph, contentStart, contentEnd);

        assertThat(parser.paragraphText(sliced)).isEqualTo("Hello World");
        assertThat(XmlUtils.marshaltoString(sliced, true, true)).contains("<w:b");
    }

    @Test
    @DisplayName("跨段落区域能正确返回起止段落")
    void shouldDetectCrossParagraphRegion() throws Exception {
        WordprocessingMLPackage pkg = packageWith(
                TestDocuments.paragraph("开始${cross-start}"),
                TestDocuments.styledParagraph("Heading1", "中间标题"),
                TestDocuments.paragraph("结束内容${cross-end}尾部"));

        MarkerParser.MarkerAnalysis analysis = parser.analyze(pkg);

        assertThat(analysis.pairs()).hasSize(1);
        MarkerParser.TagPair pair = analysis.pairs().get(0);
        assertThat(pair.startBlock()).isZero();
        assertThat(pair.endBlock()).isEqualTo(2);
    }

    @Test
    @DisplayName("只有 start 时产生 MISSING_END 告警且不生成区域")
    void shouldWarnOnMissingEnd() throws Exception {
        WordprocessingMLPackage pkg = packageWith(
                TestDocuments.paragraph("${a-start}没有结束"));

        MarkerParser.MarkerAnalysis analysis = parser.analyze(pkg);

        assertThat(analysis.pairs()).isEmpty();
        assertThat(analysis.issues()).extracting(MarkerParser.TagIssue::status)
                .containsExactly("MISSING_END");
        assertThat(analysis.tags()).extracting(TagInfo::status).contains("MISSING_END");
    }

    @Test
    @DisplayName("只有 end 时产生 MISSING_START 告警")
    void shouldWarnOnMissingStart() throws Exception {
        WordprocessingMLPackage pkg = packageWith(
                TestDocuments.paragraph("${b-end}没有开始"));

        MarkerParser.MarkerAnalysis analysis = parser.analyze(pkg);

        assertThat(analysis.pairs()).isEmpty();
        assertThat(analysis.issues()).extracting(MarkerParser.TagIssue::status)
                .containsExactly("MISSING_START");
    }

    @Test
    @DisplayName("重复 start 产生 DUPLICATE_START，并保留最后一个 start")
    void shouldWarnOnDuplicateStart() throws Exception {
        WordprocessingMLPackage pkg = packageWith(
                TestDocuments.paragraph("${c-start}第一段"),
                TestDocuments.paragraph("${c-start}第二段"),
                TestDocuments.paragraph("${c-end}"));

        MarkerParser.MarkerAnalysis analysis = parser.analyze(pkg);

        assertThat(analysis.issues()).extracting(MarkerParser.TagIssue::status)
                .contains("DUPLICATE_START");
        assertThat(analysis.pairs()).hasSize(1);
        assertThat(analysis.pairs().get(0).startBlock()).isEqualTo(1);
    }

    @Test
    @DisplayName("嵌套区域按 OVERLAP 跳过，避免内容重复")
    void shouldSkipOverlappingRegion() throws Exception {
        WordprocessingMLPackage pkg = packageWith(
                TestDocuments.paragraph("${outer-start}外层"),
                TestDocuments.paragraph("${inner-start}内层"),
                TestDocuments.paragraph("${inner-end}"),
                TestDocuments.paragraph("${outer-end}"));

        MarkerParser.MarkerAnalysis analysis = parser.analyze(pkg);

        assertThat(analysis.pairs()).hasSize(1);
        assertThat(analysis.pairs().get(0).name()).isEqualTo("outer");
        assertThat(analysis.issues()).extracting(MarkerParser.TagIssue::status)
                .contains("OVERLAP");
    }

    @Test
    @DisplayName("deleteRange 能跨多个 run 精确删除指定范围")
    void shouldDeleteExactRangeAcrossRuns() {
        P paragraph = TestDocuments.paragraph(
                TestDocuments.run("AA${x-"),
                TestDocuments.run("start}BB"),
                TestDocuments.run("CC${x-end}DD"));
        String text = parser.paragraphText(paragraph);
        int start = text.indexOf("${x-start}");
        int end = text.indexOf("${x-end}");

        parser.deleteRange(paragraph, start, end);
        parser.stripMarkers(paragraph);

        assertThat(parser.paragraphText(paragraph)).isEqualTo("AADD");
    }

    @Test
    @DisplayName("stripMarkers 清除普通、merge-point 与 toc 标记")
    void shouldStripAllMarkerKinds() {
        P paragraph = TestDocuments.paragraph(
                "A${one-start}B${one-end}C${merge-point}D${toc}E");

        boolean stripped = parser.stripMarkers(paragraph);

        assertThat(stripped).isTrue();
        assertThat(parser.paragraphText(paragraph)).isEqualTo("ABCDE");
    }

    @Test
    @DisplayName("包含图片的段落不算空段落")
    void shouldTreatImageParagraphAsNotBlank() {
        R run = TestDocuments.run("");
        run.getContent().add(new Drawing());
        P paragraph = TestDocuments.paragraph(run);

        assertThat(parser.isBlank(paragraph)).isFalse();
    }

    private static WordprocessingMLPackage packageWith(P... paragraphs) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        for (P paragraph : paragraphs) {
            pkg.getMainDocumentPart().getContent().add(paragraph);
        }
        return pkg;
    }
}
