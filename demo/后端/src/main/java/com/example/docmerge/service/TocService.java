package com.example.docmerge.service;

import com.example.docmerge.config.AppProperties;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.Br;
import org.docx4j.wml.FldChar;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.STFldCharType;
import org.docx4j.wml.Style;
import org.docx4j.wml.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成 Word 原生目录域。
 *
 * <p>目录域不预先计算页码，只写入 settings.xml 的 updateFields=true；
 * Word 打开文档时会提示更新域，也可以 Ctrl+A 后按 F9 手动更新。</p>
 */
@Service
public class TocService {

    private static final Logger log = LoggerFactory.getLogger(TocService.class);

    private final MarkerParser markerParser;
    private final AppProperties properties;

    public TocService(MarkerParser markerParser, AppProperties properties) {
        this.markerParser = markerParser;
        this.properties = properties;
    }

    /**
     * 在文档开头或 ${toc} 标记位置插入目录。
     *
     * @return 目录插入位置（顶层 body index）
     */
    public int insertToc(WordprocessingMLPackage main, List<String> warnings) {
        MainDocumentPart mainPart = main.getMainDocumentPart();
        List<Object> content = mainPart.getContent();

        int insertionIndex = findAndRemoveMarker(content, MarkerParser.TOC_MARKER);
        if (insertionIndex < 0) {
            insertionIndex = 0;
            if (!warnings.isEmpty()) {
                log.debug("主文档未找到 {}，目录默认插入到文档开头", MarkerParser.TOC_MARKER);
            }
        }
        if (insertionIndex > content.size()) {
            insertionIndex = content.size();
        }

        ensureTocStyles(mainPart);
        List<Object> tocBlocks = buildTocBlocks();
        content.addAll(insertionIndex, tocBlocks);

        updateFieldsOnOpen(main);
        log.info("目录已插入，位置 index={}，包含标题、域和分页符", insertionIndex);
        return insertionIndex;
    }

    /**
     * 定位并删除 ${toc}，返回应插入目录的位置。
     * 若标记独占段落则删除该段落后在原位插入；否则插入到该段之后。
     */
    private int findAndRemoveMarker(List<Object> content, String marker) {
        List<Integer> markerParagraphs = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            Object block = XmlUtils.unwrap(content.get(i));
            if (!(block instanceof P p)) {
                continue;
            }
            int offset = markerParser.indexOfLiteral(p, marker);
            if (offset < 0) {
                continue;
            }
            // 同一段落中出现多次时全部清理。
            while (offset >= 0) {
                markerParser.deleteRange(p, offset, offset + marker.length());
                offset = markerParser.indexOfLiteral(p, marker);
            }
            markerParagraphs.add(i);
        }
        if (markerParagraphs.isEmpty()) {
            return -1;
        }

        int first = markerParagraphs.get(0);
        List<Integer> blankParagraphs = new ArrayList<>();
        for (Integer index : markerParagraphs) {
            Object block = XmlUtils.unwrap(content.get(index));
            if (block instanceof P p && markerParser.isBlank(p)) {
                blankParagraphs.add(index);
            }
        }
        boolean firstRemoved = blankParagraphs.contains(first);
        // 从后往前删除空段落，保证索引不漂移。
        for (int i = blankParagraphs.size() - 1; i >= 0; i--) {
            content.remove((int) blankParagraphs.get(i));
        }
        return firstRemoved ? first : first + 1;
    }

    private List<Object> buildTocBlocks() {
        ObjectFactory factory = new ObjectFactory();
        List<Object> blocks = new ArrayList<>();

        // 1. “目录”标题
        P title = factory.createP();
        PPr titlePPr = factory.createPPr();
        PPrBase.PStyle titleStyle = new PPrBase.PStyle();
        titleStyle.setVal("TOCHeading");
        titlePPr.setPStyle(titleStyle);
        title.setPPr(titlePPr);
        R titleRun = factory.createR();
        Text titleText = factory.createText();
        titleText.setValue(properties.getMerge().getTocTitle());
        titleText.setSpace("preserve");
        titleRun.getContent().add(titleText);
        title.getContent().add(titleRun);
        blocks.add(title);

        // 2. TOC 域：begin / instrText / separate / 占位文字 / end
        P fieldParagraph = factory.createP();
        PPr fieldPPr = factory.createPPr();
        PPrBase.PStyle fieldStyle = new PPrBase.PStyle();
        fieldStyle.setVal("TOC1");
        fieldPPr.setPStyle(fieldStyle);
        fieldParagraph.setPPr(fieldPPr);

        fieldParagraph.getContent().add(fldCharRun(factory, STFldCharType.BEGIN, true));
        fieldParagraph.getContent().add(instrRun(factory,
                " TOC \\o \"1-" + properties.getMerge().getTocDepth() + "\" \\h \\z \\u "));
        fieldParagraph.getContent().add(fldCharRun(factory, STFldCharType.SEPARATE, false));

        R placeholderRun = factory.createR();
        Text placeholder = factory.createText();
        placeholder.setValue("（目录将在 Word 中更新：Ctrl+A 后按 F9）");
        placeholder.setSpace("preserve");
        placeholderRun.getContent().add(placeholder);
        fieldParagraph.getContent().add(placeholderRun);

        fieldParagraph.getContent().add(fldCharRun(factory, STFldCharType.END, false));
        blocks.add(fieldParagraph);

        // 3. 目录后分页，避免正文紧贴目录
        P pageBreak = factory.createP();
        R pageBreakRun = factory.createR();
        Br br = factory.createBr();
        br.setType(org.docx4j.wml.STBrType.PAGE);
        pageBreakRun.getContent().add(br);
        pageBreak.getContent().add(pageBreakRun);
        blocks.add(pageBreak);

        return blocks;
    }

    private R fldCharRun(ObjectFactory factory, STFldCharType type, boolean dirty) {
        R run = factory.createR();
        FldChar fldChar = factory.createFldChar();
        fldChar.setFldCharType(type);
        if (dirty) {
            fldChar.setDirty(Boolean.TRUE);
        }
        run.getContent().add(fldChar);
        return run;
    }

    private R instrRun(ObjectFactory factory, String instruction) {
        R run = factory.createR();
        Text instr = factory.createText();
        instr.setValue(instruction);
        instr.setSpace("preserve");
        run.getContent().add(factory.createRInstrText(instr));
        return run;
    }

    /**
     * 写入 updateFields，让 Word 打开时更新目录页码。
     */
    public void updateFieldsOnOpen(WordprocessingMLPackage main) {
        try {
            DocumentSettingsPart settingsPart = main.getMainDocumentPart().getDocumentSettingsPart(true);
            var settings = settingsPart.getContents();
            BooleanDefaultTrue update = new BooleanDefaultTrue();
            update.setVal(Boolean.TRUE);
            settings.setUpdateFields(update);
            settingsPart.setJaxbElement(settings);
        } catch (Exception e) {
            log.warn("写入 updateFields 失败：{}", e.getMessage());
        }
    }

    /**
     * 补齐目录相关样式。已存在则保持原样。
     */
    private void ensureTocStyles(MainDocumentPart mainPart) {
        StyleDefinitionsPart stylesPart = mainPart.getStyleDefinitionsPart(true);
        if (stylesPart.getJaxbElement() == null) {
            stylesPart.setJaxbElement(new org.docx4j.wml.Styles());
        }
        ensureParagraphStyle(stylesPart, "TOCHeading", "TOC Heading", 0);
        for (int i = 1; i <= Math.max(3, properties.getMerge().getTocDepth()); i++) {
            ensureParagraphStyle(stylesPart, "TOC" + i, "toc " + i, i);
        }
        // Word 的 TOC 域默认使用 Hyperlink 字符样式
        if (stylesPart.getStyleById("Hyperlink") == null) {
            Style hyperlink = new Style();
            hyperlink.setType("character");
            hyperlink.setStyleId("Hyperlink");
            Style.Name name = new Style.Name();
            name.setVal("Hyperlink");
            hyperlink.setName(name);
            org.docx4j.wml.RPr rPr = new org.docx4j.wml.RPr();
            org.docx4j.wml.Color color = new org.docx4j.wml.Color();
            color.setVal("0563C1");
            rPr.setColor(color);
            org.docx4j.wml.U underline = new org.docx4j.wml.U();
            underline.setVal(org.docx4j.wml.UnderlineEnumeration.SINGLE);
            rPr.setU(underline);
            hyperlink.setRPr(rPr);
            stylesPart.getJaxbElement().getStyle().add(hyperlink);
        }
    }

    private void ensureParagraphStyle(StyleDefinitionsPart stylesPart, String styleId,
                                      String name, int level) {
        if (stylesPart.getStyleById(styleId) != null) {
            return;
        }
        Style style = new Style();
        style.setType("paragraph");
        style.setStyleId(styleId);
        Style.Name styleName = new Style.Name();
        styleName.setVal(name);
        style.setName(styleName);
        Style.BasedOn basedOn = new Style.BasedOn();
        basedOn.setVal("Normal");
        style.setBasedOn(basedOn);

        PPr pPr = new PPr();
        if (level <= 0) {
            PPrBase.Spacing spacing = new PPrBase.Spacing();
            spacing.setBefore(BigInteger.valueOf(240));
            spacing.setAfter(BigInteger.valueOf(120));
            pPr.setSpacing(spacing);
        } else {
            PPrBase.Ind ind = new PPrBase.Ind();
            ind.setLeft(BigInteger.valueOf(Math.max(0, level - 1) * 420L));
            pPr.setInd(ind);
        }
        style.setPPr(pPr);
        stylesPart.getJaxbElement().getStyle().add(style);
    }
}
