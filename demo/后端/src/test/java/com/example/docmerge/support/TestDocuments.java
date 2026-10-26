package com.example.docmerge.support;

import org.docx4j.XmlUtils;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTBorder;
import org.docx4j.wml.Color;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.HpsMeasure;
import org.docx4j.wml.Lvl;
import org.docx4j.wml.NumberFormat;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.RPr;
import org.docx4j.wml.STBorder;
import org.docx4j.wml.Style;
import org.docx4j.wml.Styles;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.TblBorders;
import org.docx4j.wml.TblPr;
import org.docx4j.wml.TblWidth;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试与示例文档生成工具。
 *
 * <p>所有文档都在内存中生成，便于单元测试和集成测试重复使用。</p>
 */
public final class TestDocuments {

    public static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private TestDocuments() {
    }

    // ---------------------------------------------------------------------
    // 示例文档
    // ---------------------------------------------------------------------

    public static byte[] mainDocument() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ensureDefaultStyles(pkg);
        MainDocumentPart part = pkg.getMainDocumentPart();

        part.getContent().add(styledParagraph("Heading1", "DOCX 文档合并演示"));
        part.getContent().add(paragraph(
                "本示例演示按 name-start/name-end 标记提取副文档内容，并自动生成目录。"));
        part.getContent().add(paragraph("${toc}"));
        part.getContent().add(paragraph(
                "副文档内容会插入到下方标记所在位置。"));
        part.getContent().add(paragraph("${merge-point}"));
        part.getContent().add(paragraph("—— 主文档结束 ——"));

        return save(pkg);
    }

    public static byte[] subDocumentOne() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ensureDefaultStyles(pkg);
        MainDocumentPart part = pkg.getMainDocumentPart();
        ListIds ids = addNumbering(pkg);

        part.getContent().add(paragraph("这段在标记之外，不应出现在合并结果中。"));
        part.getContent().add(paragraph(
                run("${design-"), run("start}"), run("本节从标记之后开始：")));
        part.getContent().add(styledParagraph("Heading1", "总体设计"));
        part.getContent().add(paragraph(
                run("系统由 "), bold("前端"), run("、"), italic("后端"),
                run(" 和存储层组成，支持按标识区域精确合并。")));
        part.getContent().add(styledParagraph("Heading2", "关键指标"));
        part.getContent().add(listItem(ids.decimal(), 0, "合并成功率不低于 99.9%"));
        part.getContent().add(listItem(ids.decimal(), 0, "单次合并耗时小于 2 秒"));
        part.getContent().add(table(new String[][]{
                {"指标", "目标值"},
                {"吞吐量", "1000 次/天"},
                {"可用性", "99.9%"}
        }));
        part.getContent().add(imageParagraph(pkg, 101));
        part.getContent().add(hyperlinkParagraph(pkg,
                "访问示例网站", "https://example.com/docx-merge"));
        part.getContent().add(paragraph(run("本节结束。"), run("${design-end}")));
        part.getContent().add(paragraph("这段也不应出现。"));

        // 同一段落内的区域
        part.getContent().add(paragraph(
                run("${inline-start}"), bold("同一段落内的内容"), run("${inline-end}")));

        // 标记被拆分到多个 run 的区域
        part.getContent().add(paragraph(
                run("${split-"), run("start}"), run("拆分 run 的标记也能识别"),
                run("${split-"), run("end}")));

        return save(pkg);
    }

    public static byte[] subDocumentTwo() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ensureDefaultStyles(pkg);
        MainDocumentPart part = pkg.getMainDocumentPart();
        ListIds ids = addNumbering(pkg);

        part.getContent().add(paragraph("sub2 的标记之外内容。"));
        part.getContent().add(paragraph(run("${api-start}"), run("接口说明如下：")));
        part.getContent().add(styledParagraph("Heading1", "接口说明"));
        part.getContent().add(listItem(ids.bullet(), 0, "POST /api/documents/merge 上传主文档与副文档"));
        part.getContent().add(listItem(ids.bullet(), 0, "GET /api/documents/download/{fileId} 下载结果"));
        part.getContent().add(listItem(ids.bullet(), 0, "DELETE /api/documents/files/{fileId} 删除临时文件"));
        part.getContent().add(styledParagraph("Heading2", "请求示例"));
        part.getContent().add(paragraph(
                run("使用 "), bold("multipart/form-data"), run(" 上传，字段名为 "),
                code("mainFile"), run(" 与 "), code("subFiles"), run("。")));
        part.getContent().add(table(new String[][]{
                {"参数", "说明"},
                {"mainFile", "主文档，必须包含 ${merge-point} 或直接追加"},
                {"subFiles", "副文档列表，至少一个"}
        }));
        part.getContent().add(hyperlinkParagraph(pkg,
                "查看完整 README", "https://example.com/docx-merge/readme"));
        part.getContent().add(paragraph("${api-end}"));
        part.getContent().add(paragraph("sub2 的标记之外内容也不应出现。"));

        return save(pkg);
    }

    /**
     * 只有 start、没有 end 的文档，用于验证配对告警与跳过逻辑。
     */
    public static byte[] malformedSubDocument() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        pkg.getMainDocumentPart().getContent().add(paragraph("${broken-start}缺少结束标记"));
        pkg.getMainDocumentPart().getContent().add(paragraph("这段没有合并价值"));
        return save(pkg);
    }

    // ---------------------------------------------------------------------
    // 读取与断言辅助
    // ---------------------------------------------------------------------

    public static WordprocessingMLPackage load(byte[] docx) throws Exception {
        return WordprocessingMLPackage.load(new ByteArrayInputStream(docx));
    }

    public static byte[] save(WordprocessingMLPackage pkg) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }

    public static String documentXml(WordprocessingMLPackage pkg) {
        return XmlUtils.marshaltoString(pkg.getMainDocumentPart().getJaxbElement(), true, true);
    }

    public static String documentText(WordprocessingMLPackage pkg) {
        StringBuilder sb = new StringBuilder();
        collectText(pkg.getMainDocumentPart().getContent(), sb);
        return sb.toString();
    }

    private static void collectText(List<Object> nodes, StringBuilder sb) {
        for (Object node : nodes) {
            Object value = XmlUtils.unwrap(node);
            if (value instanceof Text text) {
                if (text.getValue() != null) {
                    sb.append(text.getValue());
                }
                continue;
            }
            List<Object> children = org.docx4j.TraversalUtil.getChildrenImpl(value);
            if (children != null) {
                collectText(children, sb);
            }
        }
    }

    public static long countPartsStartingWith(WordprocessingMLPackage pkg, String prefix) {
        return pkg.getParts().getParts().keySet().stream()
                .map(PartName::getName)
                .filter(name -> name.startsWith(prefix))
                .count();
    }

    public static boolean hasStyle(WordprocessingMLPackage pkg, String styleId) {
        var stylesPart = pkg.getMainDocumentPart().getStyleDefinitionsPart();
        return stylesPart != null && stylesPart.getStyleById(styleId) != null;
    }

    // ---------------------------------------------------------------------
    // 段落 / run / 表格 / 图片
    // ---------------------------------------------------------------------

    public static P paragraph(String text) {
        return paragraph(run(text));
    }

    public static P paragraph(R... runs) {
        P p = new P();
        for (R run : runs) {
            p.getContent().add(run);
        }
        return p;
    }

    public static P styledParagraph(String styleId, String text) {
        P p = paragraph(text);
        PPr pPr = new PPr();
        PPrBase.PStyle pStyle = new PPrBase.PStyle();
        pStyle.setVal(styleId);
        pPr.setPStyle(pStyle);
        p.setPPr(pPr);
        return p;
    }

    public static R run(String text) {
        R run = new R();
        Text t = new Text();
        t.setValue(text);
        t.setSpace("preserve");
        run.getContent().add(t);
        return run;
    }

    public static R bold(String text) {
        R run = run(text);
        RPr rPr = new RPr();
        BooleanDefaultTrue value = new BooleanDefaultTrue();
        value.setVal(Boolean.TRUE);
        rPr.setB(value);
        run.setRPr(rPr);
        return run;
    }

    public static R italic(String text) {
        R run = run(text);
        RPr rPr = new RPr();
        BooleanDefaultTrue value = new BooleanDefaultTrue();
        value.setVal(Boolean.TRUE);
        rPr.setI(value);
        run.setRPr(rPr);
        return run;
    }

    public static R code(String text) {
        R run = run(text);
        RPr rPr = new RPr();
        Color color = new Color();
        color.setVal("C7254E");
        rPr.setColor(color);
        HpsMeasure size = new HpsMeasure();
        size.setVal(BigInteger.valueOf(18));
        rPr.setSz(size);
        run.setRPr(rPr);
        return run;
    }

    public static P listItem(int numId, int level, String text) {
        P p = paragraph(text);
        PPr pPr = new PPr();
        PPrBase.NumPr numPr = new PPrBase.NumPr();
        PPrBase.NumPr.Ilvl ilvl = new PPrBase.NumPr.Ilvl();
        ilvl.setVal(BigInteger.valueOf(level));
        numPr.setIlvl(ilvl);
        PPrBase.NumPr.NumId id = new PPrBase.NumPr.NumId();
        id.setVal(BigInteger.valueOf(numId));
        numPr.setNumId(id);
        pPr.setNumPr(numPr);
        p.setPPr(pPr);
        return p;
    }

    public static Tbl table(String[][] rows) {
        Tbl tbl = new Tbl();
        TblPr tblPr = new TblPr();
        TblBorders borders = new TblBorders();
        borders.setTop(border());
        borders.setLeft(border());
        borders.setBottom(border());
        borders.setRight(border());
        borders.setInsideH(border());
        borders.setInsideV(border());
        tblPr.setTblBorders(borders);
        TblWidth width = new TblWidth();
        width.setType("pct");
        width.setW(BigInteger.valueOf(5000));
        tblPr.setTblW(width);
        tbl.setTblPr(tblPr);

        for (String[] row : rows) {
            Tr tr = new Tr();
            for (String cell : row) {
                Tc tc = new Tc();
                tc.getContent().add(paragraph(cell));
                tr.getContent().add(tc);
            }
            tbl.getContent().add(tr);
        }
        return tbl;
    }

    private static CTBorder border() {
        CTBorder border = new CTBorder();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(4));
        border.setColor("auto");
        return border;
    }

    public static P imageParagraph(WordprocessingMLPackage pkg, int drawingId) throws Exception {
        BinaryPartAbstractImage imagePart =
                BinaryPartAbstractImage.createImagePart(pkg, pngBytes());
        Inline inline = imagePart.createImageInline("merge-demo.png", "合并示例图片",
                drawingId, drawingId + 1, 2_600_000L, 1_200_000L, false);
        Drawing drawing = new Drawing();
        drawing.getAnchorOrInline().add(inline);
        R run = new R();
        run.getContent().add(drawing);
        return paragraph(run);
    }

    public static P hyperlinkParagraph(WordprocessingMLPackage pkg, String text, String url)
            throws Exception {
        Relationship rel = new Relationship();
        rel.setType("http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink");
        rel.setTarget(url);
        rel.setTargetMode("External");
        pkg.getMainDocumentPart().getRelationshipsPart(true).addRelationship(rel);

        P.Hyperlink hyperlink = new P.Hyperlink();
        hyperlink.setId(rel.getId());

        R run = run(text);
        RPr rPr = new RPr();
        Color color = new Color();
        color.setVal("0563C1");
        rPr.setColor(color);
        org.docx4j.wml.U underline = new org.docx4j.wml.U();
        underline.setVal(org.docx4j.wml.UnderlineEnumeration.SINGLE);
        rPr.setU(underline);
        run.setRPr(rPr);
        hyperlink.getContent().add(run);

        P p = new P();
        p.getContent().add(hyperlink);
        return p;
    }

    public static byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(520, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(0x1F4E79));
            g.fillRect(0, 0, 520, 200);
            g.setColor(new java.awt.Color(0x2E75B6));
            g.fillRoundRect(24, 24, 472, 152, 24, 24);
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(24, 24, 472, 152, 24, 24);
            g.setFont(new Font("SansSerif", Font.BOLD, 40));
            g.drawString("DOCX MERGE", 70, 112);
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.drawString("Spring Boot + docx4j", 70, 146);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------------
    // 编号定义
    // ---------------------------------------------------------------------

    public record ListIds(int decimal, int bullet) {
    }

    /**
     * 创建十进制列表（numId=1）与项目符号列表（numId=2）。
     */
    public static ListIds addNumbering(WordprocessingMLPackage pkg) throws Exception {
        NumberingDefinitionsPart part = new NumberingDefinitionsPart();
        Numbering numbering = new Numbering();
        part.setJaxbElement(numbering);
        pkg.getMainDocumentPart().addTargetPart(part);

        numbering.getAbstractNum().add(decimalAbstractNum());
        numbering.getAbstractNum().add(bulletAbstractNum());

        numbering.getNum().add(num(1, 0));
        numbering.getNum().add(num(2, 1));
        return new ListIds(1, 2);
    }

    private static Numbering.AbstractNum decimalAbstractNum() {
        Numbering.AbstractNum abstractNum = baseAbstractNum(0, "MergeTestDecimal");
        for (int level = 0; level < 3; level++) {
            Lvl lvl = baseLvl(level, NumberFormat.DECIMAL);
            Lvl.LvlText text = new Lvl.LvlText();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i <= level; i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append('%').append(i + 1);
            }
            sb.append('.');
            text.setVal(sb.toString());
            lvl.setLvlText(text);
            abstractNum.getLvl().add(lvl);
        }
        return abstractNum;
    }

    private static Numbering.AbstractNum bulletAbstractNum() {
        Numbering.AbstractNum abstractNum = baseAbstractNum(1, "MergeTestBullet");
        String[] bullets = {"•", "–", "▪"};
        for (int level = 0; level < 3; level++) {
            Lvl lvl = baseLvl(level, NumberFormat.BULLET);
            Lvl.LvlText text = new Lvl.LvlText();
            text.setVal(bullets[level]);
            lvl.setLvlText(text);
            abstractNum.getLvl().add(lvl);
        }
        return abstractNum;
    }

    private static Numbering.AbstractNum baseAbstractNum(int id, String name) {
        Numbering.AbstractNum abstractNum = new Numbering.AbstractNum();
        abstractNum.setAbstractNumId(BigInteger.valueOf(id));
        Numbering.AbstractNum.MultiLevelType type = new Numbering.AbstractNum.MultiLevelType();
        type.setVal("hybridMultilevel");
        abstractNum.setMultiLevelType(type);
        Numbering.AbstractNum.Name abstractName = new Numbering.AbstractNum.Name();
        abstractName.setVal(name);
        abstractNum.setName(abstractName);
        return abstractNum;
    }

    private static Lvl baseLvl(int level, NumberFormat format) {
        Lvl lvl = new Lvl();
        lvl.setIlvl(BigInteger.valueOf(level));
        Lvl.Start start = new Lvl.Start();
        start.setVal(BigInteger.ONE);
        lvl.setStart(start);
        org.docx4j.wml.NumFmt numFmt = new org.docx4j.wml.NumFmt();
        numFmt.setVal(format);
        lvl.setNumFmt(numFmt);
        return lvl;
    }

    private static Numbering.Num num(int numId, int abstractNumId) {
        Numbering.Num num = new Numbering.Num();
        num.setNumId(BigInteger.valueOf(numId));
        Numbering.Num.AbstractNumId abstractNumIdValue = new Numbering.Num.AbstractNumId();
        abstractNumIdValue.setVal(BigInteger.valueOf(abstractNumId));
        num.setAbstractNumId(abstractNumIdValue);
        return num;
    }

    /**
     * 为示例文档补充一份最小 styles.xml，确保 Heading 样式存在。
     */
    public static void ensureDefaultStyles(WordprocessingMLPackage pkg) {
        var stylesPart = pkg.getMainDocumentPart().getStyleDefinitionsPart(true);
        if (stylesPart.getJaxbElement() == null) {
            stylesPart.setJaxbElement(new Styles());
        }
        for (int level = 1; level <= 3; level++) {
            String id = "Heading" + level;
            if (stylesPart.getStyleById(id) != null) {
                continue;
            }
            Style style = new Style();
            style.setType("paragraph");
            style.setStyleId(id);
            Style.Name name = new Style.Name();
            name.setVal("heading " + level);
            style.setName(name);
            Style.BasedOn basedOn = new Style.BasedOn();
            basedOn.setVal("Normal");
            style.setBasedOn(basedOn);
            PPr pPr = new PPr();
            PPrBase.OutlineLvl outlineLvl = new PPrBase.OutlineLvl();
            outlineLvl.setVal(BigInteger.valueOf(level - 1L));
            pPr.setOutlineLvl(outlineLvl);
            style.setPPr(pPr);
            stylesPart.getJaxbElement().getStyle().add(style);
        }
    }

    /**
     * 生成所有示例文档的字节内容，键为文件名。
     */
    public static List<Sample> samples() throws Exception {
        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample("main.docx", mainDocument()));
        samples.add(new Sample("sub1.docx", subDocumentOne()));
        samples.add(new Sample("sub2.docx", subDocumentTwo()));
        return samples;
    }

    public record Sample(String fileName, byte[] content) {
    }
}
