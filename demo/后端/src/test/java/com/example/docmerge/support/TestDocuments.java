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


    public static byte[] mainDocument() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ensureDefaultStyles(pkg);
        MainDocumentPart part = pkg.getMainDocumentPart();

        part.getContent().add(styledParagraph("Heading1", "设备检测报告"));
        part.getContent().add(paragraph(
                "本次对一号车间配电和暖通设备进行运行状态检测。"));
        part.getContent().add(paragraph("${toc}"));
        part.getContent().add(paragraph("检测结果附在下方。"));
        part.getContent().add(paragraph("${merge-point}"));
        part.getContent().add(paragraph("—— 报告结束 ——"));

        return save(pkg);
    }

    public static byte[] subDocumentOne() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ensureDefaultStyles(pkg);
        MainDocumentPart part = pkg.getMainDocumentPart();
        ListIds ids = addNumbering(pkg);

        part.getContent().add(paragraph("这段在标记之外，不应出现在合并结果中。"));
        part.getContent().add(paragraph(
                run("${scope-"), run("start}"), run("检测范围如下：")));
        part.getContent().add(styledParagraph("Heading1", "检测范围"));
        part.getContent().add(paragraph(
                run("检查 "), bold("配电柜"), run("、"), italic("空调机组"),
                run(" 和循环风机的运行参数与保护功能。")));
        part.getContent().add(styledParagraph("Heading2", "主要检测项目"));
        part.getContent().add(listItem(ids.decimal(), 0, "记录输入电压和回路电流"));
        part.getContent().add(listItem(ids.decimal(), 0, "检查控制柜指示灯和保护动作"));
        part.getContent().add(table(new String[][]{
                {"检测项目", "记录要求"},
                {"输入电压", "三相 380 V"},
                {"环境温度", "26 ℃"}
        }));
        part.getContent().add(imageParagraph(pkg, 101));
        part.getContent().add(hyperlinkParagraph(pkg,
                "查看设备台账", "https://example.com/equipment"));
        part.getContent().add(paragraph(run("本节结束。"), run("${scope-end}")));
        part.getContent().add(paragraph("这段也不应出现。"));

        // 同一段落内的区域
        part.getContent().add(paragraph(
                run("${inline-start}"), bold("同一段落中的内容"), run("${inline-end}")));

        // 标记被拆分到多个 run 的区域
        part.getContent().add(paragraph(
                run("${split-"), run("start}"), run("跨 run 的标记也能识别"),
                run("${split-"), run("end}")));

        return save(pkg);
    }

    public static byte[] subDocumentTwo() throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ensureDefaultStyles(pkg);
        MainDocumentPart part = pkg.getMainDocumentPart();
        ListIds ids = addNumbering(pkg);

        part.getContent().add(paragraph("以下内容不参与合并。"));
        part.getContent().add(paragraph(run("${issue-start}"), run("异常项如下：")));
        part.getContent().add(styledParagraph("Heading1", "异常项与建议"));
        part.getContent().add(listItem(ids.bullet(), 0, "控制柜端子排有少量积尘"));
        part.getContent().add(listItem(ids.bullet(), 0, "循环风机轴承温度偏高"));
        part.getContent().add(styledParagraph("Heading2", "处理建议"));
        part.getContent().add(paragraph(
                run("停电后清理端子排，并对 "), bold("循环风机"), run(" 补加润滑脂，"),
                italic("复测正常后恢复运行"), run("。")));
        part.getContent().add(table(new String[][]{
                {"设备", "建议"},
                {"控制柜", "清理端子排后复测"},
                {"循环风机", "补加润滑脂并记录温度"}
        }));
        part.getContent().add(hyperlinkParagraph(pkg,
                "查看现场记录", "https://example.com/site-record"));
        part.getContent().add(paragraph("${issue-end}"));
        part.getContent().add(paragraph("以下内容也不参与合并。"));

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
