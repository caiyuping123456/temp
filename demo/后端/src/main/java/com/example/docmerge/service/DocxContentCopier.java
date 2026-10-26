package com.example.docmerge.service;

import com.example.docmerge.config.AppProperties;
import org.docx4j.TraversalUtil;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.contenttype.ContentType;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart.AddPartBehaviour;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.Br;
import org.docx4j.wml.NumberFormat;
import org.docx4j.wml.Numbering;
import org.docx4j.wml.P;
import org.docx4j.wml.PPr;
import org.docx4j.wml.PPrBase;
import org.docx4j.wml.R;
import org.docx4j.wml.STBrType;
import org.docx4j.wml.Style;
import org.docx4j.wml.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copies the tagged content of sub documents into the main document while keeping
 * paragraphs, tables, images, styles and numbering intact.
 *
 * <p>Workflow:
 * <ol>
 *     <li>Extract every paired ${name-start}...${name-end} range from the sub document.</li>
 *     <li>Convert each block to DOM, copy referenced relationships / numbering definitions,
 *         then rewrite relationship ids, numIds and drawing ids.</li>
 *     <li>Copy missing styles from the sub document and demote sub headings by one level.</li>
 * </ol>
 */
@Component
public class DocxContentCopier {

    private static final Logger log = LoggerFactory.getLogger(DocxContentCopier.class);

    private static final String REL_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String W_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private static final String HEADING_NUMBERING_NAME = "MergeHeadingNumbering";

    private final MarkerParser markerParser;
    private final AppProperties properties;

    public DocxContentCopier(MarkerParser markerParser, AppProperties properties) {
        this.markerParser = markerParser;
        this.properties = properties;
    }

    /**
     * Prepare the main document: styles, heading styles and optional heading numbering.
     */
    public void initialize(WordprocessingMLPackage main, List<String> warnings) {
        StyleDefinitionsPart styles = ensureStylePart(main);
        ensureHeadingStyles(styles, warnings);
        if (properties.getMerge().isNumberHeadings()) {
            ensureHeadingNumbering(main, styles, warnings);
        }
    }

    /**
     * Extract all paired ranges from a sub document and copy them as top-level blocks.
     */
    public List<Object> copySubDocument(WordprocessingMLPackage main,
                                        WordprocessingMLPackage sub,
                                        MarkerParser.MarkerAnalysis analysis,
                                        String subName,
                                        List<String> warnings) {
        SubContext ctx = new SubContext(main, sub, subName, ensureStylePart(main),
                ensureNumberingPart(main, warnings));

        List<Object> extracted = extract(analysis);
        List<Object> result = new ArrayList<>();
        for (Object block : extracted) {
            try {
                Object copied = remapBlock(block, ctx, warnings);
                if (copied == null) {
                    continue;
                }
                Object value = XmlUtils.unwrap(copied);
                demoteHeadings(ctx, value, warnings);
                if (value instanceof P p && markerParser.isBlank(p)) {
                    continue;
                }
                result.add(value);
            } catch (Exception e) {
                log.warn("Failed to copy block: sub={}, type={}, message={}",
                        subName, block.getClass().getSimpleName(), e.getMessage(), e);
                warnings.add("副文档 [" + subName + "] 中有元素复制失败，已跳过该元素：" + e.getMessage());
            }
        }
        log.info("Sub document [{}] copied {} top-level blocks ({} images)",
                subName, result.size(), ctx.imageCount);
        return result;
    }

    /**
     * Build a Heading1 paragraph for a merged sub document.
     */
    public P createHeading(String text) {
        P p = new P();
        PPr pPr = new PPr();
        PPrBase.PStyle style = new PPrBase.PStyle();
        style.setVal("Heading1");
        pPr.setPStyle(style);
        p.setPPr(pPr);
        p.getContent().add(textRun(text));
        return p;
    }

    /**
     * Build a page break paragraph.
     */
    public P createPageBreak() {
        P p = new P();
        R r = new R();
        Br br = new Br();
        br.setType(STBrType.PAGE);
        r.getContent().add(br);
        p.getContent().add(r);
        return p;
    }

    private R textRun(String text) {
        R r = new R();
        Text t = new Text();
        t.setValue(text);
        t.setSpace("preserve");
        r.getContent().add(t);
        return r;
    }

    // ------------------------------------------------------------------
    // Tag extraction
    // ------------------------------------------------------------------

    /**
     * Extract content for every tag pair. Supports same-paragraph ranges, ranges
     * spanning multiple paragraphs and whole paragraphs / tables in between.
     */
    private List<Object> extract(MarkerParser.MarkerAnalysis analysis) {
        List<Object> result = new ArrayList<>();
        for (MarkerParser.TagPair pair : analysis.pairs()) {
            MarkerParser.ParagraphInfo startInfo = analysis.paragraphAt(pair.startBlock());
            MarkerParser.ParagraphInfo endInfo = analysis.paragraphAt(pair.endBlock());
            if (startInfo == null || endInfo == null) {
                continue;
            }

            if (pair.startBlock() == pair.endBlock()) {
                P sliced = markerParser.slice(startInfo.paragraph(), pair.startOffset(), pair.endOffset());
                markerParser.stripMarkers(sliced);
                if (!markerParser.isBlank(sliced)) {
                    result.add(sliced);
                }
                continue;
            }

            P first = markerParser.slice(startInfo.paragraph(), pair.startOffset(),
                    startInfo.text().length());
            markerParser.stripMarkers(first);
            if (!markerParser.isBlank(first)) {
                result.add(first);
            }

            for (int i = pair.startBlock() + 1; i < pair.endBlock(); i++) {
                Object source = analysis.blocks().get(i);
                if (XmlUtils.unwrap(source) instanceof org.docx4j.wml.SectPr) {
                    continue;
                }
                Object copy = XmlUtils.deepCopy(source);
                boolean hadMarkers = containsMarkers(copy);
                stripMarkersDeep(copy);
                if (hadMarkers && copy instanceof P p && markerParser.isBlank(p)) {
                    continue;
                }
                result.add(copy);
            }

            P last = markerParser.slice(endInfo.paragraph(), 0, pair.endOffset());
            markerParser.stripMarkers(last);
            if (!markerParser.isBlank(last)) {
                result.add(last);
            }
        }
        return result;
    }

    private boolean containsMarkers(Object block) {
        List<P> paragraphs = new ArrayList<>();
        collectParagraphs(block, paragraphs);
        for (P p : paragraphs) {
            if (MarkerParser.TAG_PATTERN.matcher(markerParser.paragraphText(p)).find()) {
                return true;
            }
        }
        return false;
    }

    private void stripMarkersDeep(Object block) {
        List<P> paragraphs = new ArrayList<>();
        collectParagraphs(block, paragraphs);
        for (P p : paragraphs) {
            markerParser.stripMarkers(p);
        }
    }

    private void collectParagraphs(Object node, List<P> result) {
        Object value = XmlUtils.unwrap(node);
        if (value instanceof P p) {
            result.add(p);
        }
        List<Object> children = TraversalUtil.getChildrenImpl(value);
        if (children == null) {
            return;
        }
        for (Object child : new ArrayList<>(children)) {
            collectParagraphs(child, result);
        }
    }

    // ------------------------------------------------------------------
    // Reference remapping
    // ------------------------------------------------------------------

    /**
     * Marshal one block to DOM, copy referenced parts, rewrite ids and unmarshal again.
     */
    private Object remapBlock(Object block, SubContext ctx, List<String> warnings) throws Exception {
        Document document = XmlUtils.marshaltoW3CDomDocument(block);
        Element root = document.getDocumentElement();

        Set<String> relIds = new LinkedHashSet<>();
        Set<Integer> numIds = new LinkedHashSet<>();
        Set<String> styleIds = new LinkedHashSet<>();
        collectReferences(root, relIds, numIds, styleIds);

        Map<String, String> relMapping = new HashMap<>();
        for (String oldRelId : relIds) {
            String newRelId = copyRelationship(ctx, oldRelId, warnings);
            if (newRelId != null) {
                relMapping.put(oldRelId, newRelId);
            }
        }

        Map<Integer, Integer> numMapping = new HashMap<>();
        for (Integer oldNumId : numIds) {
            Integer newNumId = ensureNumbering(ctx, oldNumId, warnings);
            if (newNumId != null) {
                numMapping.put(oldNumId, newNumId);
            }
        }

        rewriteReferences(root, relMapping, numMapping, ctx);
        Object copied = XmlUtils.unmarshal(document);
        Object value = XmlUtils.unwrap(copied);

        for (String styleId : styleIds) {
            ensureStyle(ctx, styleId, warnings);
        }
        return value;
    }

    private void collectReferences(Element root, Set<String> relIds, Set<Integer> numIds,
                                   Set<String> styleIds) {
        walk(root, element -> {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String local = localName(attr);
                String ns = attr.getNamespaceURI();
                String value = attr.getNodeValue();

                if (REL_NS.equals(ns) && ("embed".equals(local) || "link".equals(local) || "id".equals(local))) {
                    relIds.add(value);
                }
                if (W_NS.equals(ns) && "numId".equals(element.getLocalName()) && "val".equals(local)) {
                    try {
                        numIds.add(Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        // keep the original value
                    }
                }
                if (("pStyle".equals(element.getLocalName()) || "rStyle".equals(element.getLocalName())
                        || "tblStyle".equals(element.getLocalName())) && "val".equals(local)) {
                    styleIds.add(value);
                }
            }
        });
    }

    private void rewriteReferences(Element root, Map<String, String> relMapping,
                                   Map<Integer, Integer> numMapping, SubContext ctx) {
        walk(root, element -> {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String local = localName(attr);
                String ns = attr.getNamespaceURI();
                String value = attr.getNodeValue();

                if (REL_NS.equals(ns) && ("embed".equals(local) || "link".equals(local) || "id".equals(local))) {
                    String mapped = relMapping.get(value);
                    if (mapped != null) {
                        attr.setNodeValue(mapped);
                    }
                }
                if (W_NS.equals(ns) && "numId".equals(element.getLocalName()) && "val".equals(local)) {
                    try {
                        Integer mapped = numMapping.get(Integer.parseInt(value));
                        if (mapped != null) {
                            attr.setNodeValue(String.valueOf(mapped));
                        }
                    } catch (NumberFormatException ignored) {
                        // keep the original value
                    }
                }
                if (W_NS.equals(ns)
                        && ("bookmarkStart".equals(element.getLocalName())
                        || "bookmarkEnd".equals(element.getLocalName()))
                        && "id".equals(local)) {
                    String mapped = ctx.bookmarkIdMap.computeIfAbsent(value,
                            key -> String.valueOf(++ctx.bookmarkId));
                    attr.setNodeValue(mapped);
                }
            }

            // Drawing object ids must be unique after merging, otherwise Word may report a repair.
            if ("docPr".equals(element.getLocalName()) || "cNvPr".equals(element.getLocalName())) {
                String id = element.getAttribute("id");
                if (id != null && !id.isEmpty()) {
                    element.setAttribute("id", String.valueOf(++ctx.drawingId));
                }
            }
        });
    }

    private static String localName(Node attr) {
        return attr.getLocalName() == null ? attr.getNodeName() : attr.getLocalName();
    }

    private void walk(Element element, Consumer<Element> visitor) {
        visitor.accept(element);
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                walk(childElement, visitor);
            }
        }
    }

    // ------------------------------------------------------------------
    // Relationships
    // ------------------------------------------------------------------

    private String copyRelationship(SubContext ctx, String oldRelId, List<String> warnings) {
        if (oldRelId == null || oldRelId.isBlank()) {
            return null;
        }
        String cached = ctx.relMapping.get(oldRelId);
        if (cached != null) {
            return cached;
        }

        Relationship rel = ctx.subPart.getRelationshipsPart(true).getRelationshipByID(oldRelId);
        if (rel == null) {
            warnings.add("副文档 [" + ctx.subName + "] 的关系 " + oldRelId + " 不存在，已跳过相关引用");
            log.warn("Relationship not found: sub={}, rId={}", ctx.subName, oldRelId);
            return null;
        }

        try {
            String newId;
            if (isExternal(rel)) {
                newId = copyExternalRelationship(ctx, rel);
            } else {
                Part part = ctx.subPart.getRelationshipsPart(true).getPart(rel);
                if (part == null) {
                    warnings.add("副文档 [" + ctx.subName + "] 的关系目标缺失（" + oldRelId + "），已跳过");
                    return null;
                }
                if (part instanceof BinaryPart binaryPart) {
                    newId = copyBinaryPart(ctx, rel, binaryPart, warnings);
                } else {
                    // Charts / embedded XML need recursive copying of their own relationships.
                    // This demo deliberately keeps the original reference and warns instead.
                    warnings.add("副文档 [" + ctx.subName + "] 中的复杂对象（图表/嵌入 XML，rId="
                            + oldRelId + "）未复制，已保留原引用，可能导致该对象无法显示");
                    log.warn("Skipped non-binary relationship: sub={}, rId={}, type={}, part={}",
                            ctx.subName, oldRelId, rel.getType(), part.getClass().getName());
                    return oldRelId;
                }
            }
            ctx.relMapping.put(oldRelId, newId);
            return newId;
        } catch (Exception e) {
            warnings.add("副文档 [" + ctx.subName + "] 的关系 " + oldRelId + " 复制失败：" + e.getMessage());
            log.warn("Failed to copy relationship: sub={}, rId={}", ctx.subName, oldRelId, e);
            return null;
        }
    }

    private String copyExternalRelationship(SubContext ctx, Relationship rel) throws Exception {
        Relationship newRel = new org.docx4j.relationships.ObjectFactory().createRelationship();
        newRel.setType(rel.getType());
        newRel.setTarget(rel.getTarget());
        newRel.setTargetMode(rel.getTargetMode());
        ctx.mainPart.getRelationshipsPart(true).addRelationship(newRel);
        log.debug("Copied external relationship: {} -> {}", rel.getTarget(), newRel.getId());
        return newRel.getId();
    }

    private String copyBinaryPart(SubContext ctx, Relationship rel, BinaryPart source,
                                  List<String> warnings) throws Exception {
        byte[] bytes = source.getBytes();
        if (bytes == null || bytes.length == 0) {
            warnings.add("副文档 [" + ctx.subName + "] 中的二进制对象内容为空，已跳过");
            return null;
        }

        String contentType = source.getContentType();
        String extension = safeExtension(source.getPartName(), contentType);
        boolean image = isImageRelation(rel) || (contentType != null && contentType.startsWith("image/"));

        String folder = image ? "/word/media/" : folderOf(source.getPartName().getName());
        String fileName = (image
                ? "merge-" + (++ctx.imageCount) + "-image"
                : "merge-" + (++ctx.binaryCount) + "-object")
                + "." + extension;
        String partName = uniquePartName(ctx.main, folder + fileName);

        BinaryPart target = new BinaryPart(new PartName(partName));
        target.setPackage(ctx.main);
        target.setContentType(new ContentType(contentType == null ? "application/octet-stream" : contentType));
        target.setRelationshipType(image ? Namespaces.IMAGE : rel.getType());
        target.setBinaryData(bytes);

        Relationship added = ctx.mainPart.addTargetPart(target, AddPartBehaviour.RENAME_IF_NAME_EXISTS);
        log.info("Copied binary part: {} -> {}, type={}", source.getPartName().getName(),
                partName, contentType);
        return added.getId();
    }

    private String uniquePartName(WordprocessingMLPackage main, String candidate) throws Exception {
        String name = candidate;
        int index = 1;
        while (main.getParts().get(new PartName(name)) != null) {
            int dot = candidate.lastIndexOf('.');
            if (dot > 0) {
                name = candidate.substring(0, dot) + "-" + index + candidate.substring(dot);
            } else {
                name = candidate + "-" + index;
            }
            index++;
        }
        return name;
    }

    private static String folderOf(String partName) {
        int slash = partName.lastIndexOf('/');
        return slash > 0 ? partName.substring(0, slash + 1) : "/word/";
    }

    private static boolean isExternal(Relationship rel) {
        return rel.getTargetMode() != null && "External".equalsIgnoreCase(rel.getTargetMode());
    }

    private static boolean isImageRelation(Relationship rel) {
        return Namespaces.IMAGE.equals(rel.getType());
    }

    private static String safeExtension(PartName partName, String contentType) {
        String extension = partName == null ? null : partName.getExtension();
        if (extension != null) {
            extension = extension.replace(".", "");
            if (extension.matches("[A-Za-z0-9]{1,8}")) {
                return extension.toLowerCase();
            }
        }
        if (contentType != null) {
            int slash = contentType.indexOf('/');
            if (slash >= 0) {
                String ext = contentType.substring(slash + 1).replace("x-", "");
                if (ext.matches("[A-Za-z0-9]{1,8}")) {
                    return ext;
                }
            }
        }
        return "bin";
    }

    // ------------------------------------------------------------------
    // Styles
    // ------------------------------------------------------------------

    private StyleDefinitionsPart ensureStylePart(WordprocessingMLPackage main) {
        StyleDefinitionsPart part = main.getMainDocumentPart().getStyleDefinitionsPart(true);
        if (part.getJaxbElement() == null) {
            part.setJaxbElement(new org.docx4j.wml.Styles());
        }
        return part;
    }

    private NumberingDefinitionsPart ensureNumberingPart(WordprocessingMLPackage main,
                                                         List<String> warnings) {
        NumberingDefinitionsPart part = main.getMainDocumentPart().getNumberingDefinitionsPart();
        if (part == null) {
            try {
                part = new NumberingDefinitionsPart();
                part.setJaxbElement(new Numbering());
                main.getMainDocumentPart().addTargetPart(part);
            } catch (Exception e) {
                log.warn("Failed to create numbering.xml", e);
                warnings.add("主文档缺少 numbering.xml，编号列表可能无法完整复制");
                return null;
            }
        }
        if (part.getJaxbElement() == null) {
            part.setJaxbElement(new Numbering());
        }
        return part;
    }

    private void ensureHeadingStyles(StyleDefinitionsPart styles, List<String> warnings) {
        for (int i = 1; i <= 9; i++) {
            String id = "Heading" + i;
            if (styles.getStyleById(id) != null) {
                continue;
            }
            Style known = StyleDefinitionsPart.getKnownStyles().get(id);
            if (known == null) {
                known = minimalHeadingStyle(id, i);
            }
            styles.getJaxbElement().getStyle().add(XmlUtils.deepCopy(known));
            log.info("Added missing heading style: {}", id);
        }
    }

    private Style minimalHeadingStyle(String id, int level) {
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
        return style;
    }

    /**
     * Make sure a style used by the sub document exists in the main document.
     * Copies basedOn / link chains first.
     */
    private String ensureStyle(SubContext ctx, String styleId, List<String> warnings) {
        if (styleId == null || styleId.isBlank()) {
            return styleId;
        }
        if (ctx.mainStyles.getStyleById(styleId) != null) {
            return styleId;
        }
        if (!ctx.copyingStyles.add(styleId)) {
            return styleId;
        }
        try {
            Style source = ctx.subStyles == null ? null : ctx.subStyles.getStyleById(styleId);
            if (source == null) {
                if (copyKnownStyle(ctx.mainStyles, styleId)) {
                    return styleId;
                }
                warnings.add("副文档 [" + ctx.subName + "] 引用的样式 [" + styleId
                        + "] 在主/副文档中均未定义，已保留原引用");
                return styleId;
            }

            if (source.getBasedOn() != null && source.getBasedOn().getVal() != null) {
                ensureStyle(ctx, source.getBasedOn().getVal(), warnings);
            }
            if (source.getLink() != null && source.getLink().getVal() != null) {
                ensureStyle(ctx, source.getLink().getVal(), warnings);
            }

            Style copy = XmlUtils.deepCopy(source);
            remapStyleNumbering(ctx, copy, warnings);
            ctx.mainStyles.getJaxbElement().getStyle().add(copy);
            log.debug("Copied style: {} (type={})", styleId, source.getType());
            return styleId;
        } finally {
            ctx.copyingStyles.remove(styleId);
        }
    }

    private boolean copyKnownStyle(StyleDefinitionsPart mainStyles, String styleId) {
        Style known = StyleDefinitionsPart.getKnownStyles().get(styleId);
        if (known == null) {
            return false;
        }
        mainStyles.getJaxbElement().getStyle().add(XmlUtils.deepCopy(known));
        return true;
    }

    private void remapStyleNumbering(SubContext ctx, Style style, List<String> warnings) {
        if (style.getPPr() == null || style.getPPr().getNumPr() == null
                || style.getPPr().getNumPr().getNumId() == null) {
            return;
        }
        PPrBase.NumPr numPr = style.getPPr().getNumPr();
        BigInteger oldId = numPr.getNumId().getVal();
        if (oldId == null) {
            return;
        }
        Integer newId = ensureNumbering(ctx, oldId.intValue(), warnings);
        if (newId != null) {
            numPr.getNumId().setVal(BigInteger.valueOf(newId));
        }
    }

    // ------------------------------------------------------------------
    // Numbering
    // ------------------------------------------------------------------

    /**
     * Copy a numbering definition and return the new numId.
     */
    private Integer ensureNumbering(SubContext ctx, Integer oldNumId, List<String> warnings) {
        if (oldNumId == null || oldNumId == 0) {
            return oldNumId;
        }
        if (ctx.numIdMap.containsKey(oldNumId)) {
            return ctx.numIdMap.get(oldNumId);
        }
        if (ctx.subNumbering == null || ctx.mainNumbering == null) {
            return oldNumId;
        }
        Numbering subNumbering = ctx.subNumbering.getJaxbElement();
        Numbering mainNumbering = ctx.mainNumbering.getJaxbElement();

        Numbering.Num sourceNum = findNum(subNumbering, oldNumId);
        if (sourceNum == null) {
            warnings.add("副文档 [" + ctx.subName + "] 的编号 numId=" + oldNumId
                    + " 未定义，已保留原值");
            return oldNumId;
        }
        BigInteger oldAbstractId = sourceNum.getAbstractNumId() == null
                ? null : sourceNum.getAbstractNumId().getVal();
        Numbering.AbstractNum sourceAbstract = oldAbstractId == null
                ? null : findAbstractNum(subNumbering, oldAbstractId.intValue());
        if (sourceAbstract == null) {
            warnings.add("副文档 [" + ctx.subName + "] 的编号 numId=" + oldNumId
                    + " 缺少 abstractNum，已保留原值");
            return oldNumId;
        }

        int newAbstractId = maxAbstractNumId(mainNumbering) + 1;
        int newNumId = maxNumId(mainNumbering) + 1;

        Numbering.AbstractNum abstractCopy = XmlUtils.deepCopy(sourceAbstract);
        abstractCopy.setAbstractNumId(BigInteger.valueOf(newAbstractId));
        mainNumbering.getAbstractNum().add(abstractCopy);

        Numbering.Num numCopy = XmlUtils.deepCopy(sourceNum);
        numCopy.setNumId(BigInteger.valueOf(newNumId));
        if (numCopy.getAbstractNumId() == null) {
            numCopy.setAbstractNumId(new Numbering.Num.AbstractNumId());
        }
        numCopy.getAbstractNumId().setVal(BigInteger.valueOf(newAbstractId));
        mainNumbering.getNum().add(numCopy);

        ctx.numIdMap.put(oldNumId, newNumId);
        log.info("Copied numbering: numId {} -> {}, abstractNumId {} -> {}",
                oldNumId, newNumId, oldAbstractId, newAbstractId);
        return newNumId;
    }

    private Numbering.Num findNum(Numbering numbering, int numId) {
        for (Numbering.Num num : numbering.getNum()) {
            if (num.getNumId() != null && num.getNumId().intValue() == numId) {
                return num;
            }
        }
        return null;
    }

    private Numbering.AbstractNum findAbstractNum(Numbering numbering, int abstractId) {
        for (Numbering.AbstractNum abstractNum : numbering.getAbstractNum()) {
            if (abstractNum.getAbstractNumId() != null
                    && abstractNum.getAbstractNumId().intValue() == abstractId) {
                return abstractNum;
            }
        }
        return null;
    }

    private int maxNumId(Numbering numbering) {
        int max = 0;
        for (Numbering.Num num : numbering.getNum()) {
            if (num.getNumId() != null) {
                max = Math.max(max, num.getNumId().intValue());
            }
        }
        return max;
    }

    private int maxAbstractNumId(Numbering numbering) {
        int max = 0;
        for (Numbering.AbstractNum abstractNum : numbering.getAbstractNum()) {
            if (abstractNum.getAbstractNumId() != null) {
                max = Math.max(max, abstractNum.getAbstractNumId().intValue());
            }
        }
        return max;
    }

    /**
     * Add multilevel numbering to Heading1..9 so the TOC shows 1 / 1.1 / 1.1.1.
     */
    private void ensureHeadingNumbering(WordprocessingMLPackage main, StyleDefinitionsPart styles,
                                        List<String> warnings) {
        NumberingDefinitionsPart numberingPart = ensureNumberingPart(main, warnings);
        if (numberingPart == null) {
            return;
        }
        Numbering numbering = numberingPart.getJaxbElement();
        if (findHeadingNumberingNumId(numbering) != null) {
            return;
        }

        int numId = maxNumId(numbering) + 1;
        int abstractId = maxAbstractNumId(numbering) + 1;

        Numbering.AbstractNum abstractNum = new Numbering.AbstractNum();
        abstractNum.setAbstractNumId(BigInteger.valueOf(abstractId));
        Numbering.AbstractNum.MultiLevelType multiLevelType = new Numbering.AbstractNum.MultiLevelType();
        multiLevelType.setVal("multilevel");
        abstractNum.setMultiLevelType(multiLevelType);
        Numbering.AbstractNum.Name name = new Numbering.AbstractNum.Name();
        name.setVal(HEADING_NUMBERING_NAME);
        abstractNum.setName(name);

        for (int level = 0; level < 9; level++) {
            abstractNum.getLvl().add(createHeadingLevel(level));
        }
        numbering.getAbstractNum().add(abstractNum);

        Numbering.Num num = new Numbering.Num();
        num.setNumId(BigInteger.valueOf(numId));
        Numbering.Num.AbstractNumId abstractNumId = new Numbering.Num.AbstractNumId();
        abstractNumId.setVal(BigInteger.valueOf(abstractId));
        num.setAbstractNumId(abstractNumId);
        numbering.getNum().add(num);

        for (int level = 1; level <= 9; level++) {
            Style style = styles.getStyleById("Heading" + level);
            if (style == null) {
                continue;
            }
            if (style.getPPr() == null) {
                style.setPPr(new PPr());
            }
            if (style.getPPr().getNumPr() == null) {
                PPrBase.NumPr numPr = new PPrBase.NumPr();
                PPrBase.NumPr.Ilvl ilvl = new PPrBase.NumPr.Ilvl();
                ilvl.setVal(BigInteger.valueOf(level - 1L));
                numPr.setIlvl(ilvl);
                PPrBase.NumPr.NumId numIdValue = new PPrBase.NumPr.NumId();
                numIdValue.setVal(BigInteger.valueOf(numId));
                numPr.setNumId(numIdValue);
                style.getPPr().setNumPr(numPr);
            }
        }
        log.info("Created heading numbering: numId={}, abstractNumId={}", numId, abstractId);
    }

    private Integer findHeadingNumberingNumId(Numbering numbering) {
        for (Numbering.Num num : numbering.getNum()) {
            if (num.getAbstractNumId() == null || num.getAbstractNumId().getVal() == null) {
                continue;
            }
            Numbering.AbstractNum abstractNum =
                    findAbstractNum(numbering, num.getAbstractNumId().getVal().intValue());
            if (abstractNum != null && abstractNum.getName() != null
                    && HEADING_NUMBERING_NAME.equals(abstractNum.getName().getVal())) {
                return num.getNumId() == null ? null : num.getNumId().intValue();
            }
        }
        return null;
    }

    private org.docx4j.wml.Lvl createHeadingLevel(int zeroBasedLevel) {
        org.docx4j.wml.Lvl lvl = new org.docx4j.wml.Lvl();
        lvl.setIlvl(BigInteger.valueOf(zeroBasedLevel));

        org.docx4j.wml.Lvl.Start start = new org.docx4j.wml.Lvl.Start();
        start.setVal(BigInteger.ONE);
        lvl.setStart(start);

        org.docx4j.wml.NumFmt numFmt = new org.docx4j.wml.NumFmt();
        numFmt.setVal(NumberFormat.DECIMAL);
        lvl.setNumFmt(numFmt);

        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= zeroBasedLevel; i++) {
            if (i > 0) {
                text.append('.');
            }
            text.append('%').append(i + 1);
        }
        org.docx4j.wml.Lvl.LvlText lvlText = new org.docx4j.wml.Lvl.LvlText();
        lvlText.setVal(text.toString());
        lvl.setLvlText(lvlText);

        org.docx4j.wml.Lvl.PStyle pStyle = new org.docx4j.wml.Lvl.PStyle();
        pStyle.setVal("Heading" + (zeroBasedLevel + 1));
        lvl.setPStyle(pStyle);
        return lvl;
    }

    // ------------------------------------------------------------------
    // Heading demotion
    // ------------------------------------------------------------------

    /**
     * Demote sub document headings by one level and create derived styles that keep
     * the original formatting while exposing the new outline level.
     */
    private void demoteHeadings(SubContext ctx, Object block, List<String> warnings) {
        List<P> paragraphs = new ArrayList<>();
        collectParagraphs(block, paragraphs);
        for (P p : paragraphs) {
            if (p.getPPr() == null) {
                continue;
            }
            if (p.getPPr().getPStyle() != null && p.getPPr().getPStyle().getVal() != null) {
                String styleId = p.getPPr().getPStyle().getVal();
                int level = resolveHeadingLevel(ctx.subStyles, styleId);
                if (level > 0) {
                    int target = Math.min(level + 1, 9);
                    String baseId = ensureStyle(ctx, styleId, warnings);
                    String derivedId = ensureDerivedHeadingStyle(ctx, baseId, target);
                    p.getPPr().getPStyle().setVal(derivedId);
                    log.debug("Demoted heading: {} -> {} (level {} -> {})",
                            styleId, derivedId, level, target);
                }
            } else if (p.getPPr().getOutlineLvl() != null
                    && p.getPPr().getOutlineLvl().getVal() != null) {
                int level = p.getPPr().getOutlineLvl().getVal().intValue() + 1;
                int target = Math.min(level + 1, 9);
                PPrBase.OutlineLvl outlineLvl = new PPrBase.OutlineLvl();
                outlineLvl.setVal(BigInteger.valueOf(target - 1L));
                p.getPPr().setOutlineLvl(outlineLvl);
            }
        }
    }

    private int resolveHeadingLevel(StyleDefinitionsPart styles, String styleId) {
        Style style = styles == null ? null : styles.getStyleById(styleId);
        Set<String> visited = new HashSet<>();
        int depth = 0;
        while (style != null && depth++ < 20 && visited.add(style.getStyleId())) {
            if (style.getPPr() != null && style.getPPr().getOutlineLvl() != null
                    && style.getPPr().getOutlineLvl().getVal() != null) {
                return style.getPPr().getOutlineLvl().getVal().intValue() + 1;
            }
            String id = style.getStyleId();
            if (id != null && id.matches("(?i)heading[1-9]")) {
                return Integer.parseInt(id.substring(id.length() - 1));
            }
            if (style.getName() != null && style.getName().getVal() != null) {
                String name = style.getName().getVal().trim().toLowerCase();
                if (name.matches("heading [1-9]")) {
                    return Integer.parseInt(name.substring(name.length() - 1));
                }
            }
            style = style.getBasedOn() == null || style.getBasedOn().getVal() == null
                    ? null : styles.getStyleById(style.getBasedOn().getVal());
        }
        if (styleId != null && styleId.matches("(?i)heading[1-9]")) {
            return Integer.parseInt(styleId.substring(styleId.length() - 1));
        }
        return 0;
    }

    private String ensureDerivedHeadingStyle(SubContext ctx, String baseStyleId, int targetLevel) {
        String safeBase = baseStyleId.replaceAll("[^A-Za-z0-9_]", "_");
        if (safeBase.length() > 24) {
            safeBase = safeBase.substring(0, 24);
        }
        String derivedId = "MergeH" + targetLevel + "_" + safeBase;
        if (ctx.mainStyles.getStyleById(derivedId) != null) {
            return derivedId;
        }

        Style style = new Style();
        style.setType("paragraph");
        style.setStyleId(derivedId);
        Style.Name name = new Style.Name();
        name.setVal("Merge Heading " + targetLevel + " (" + baseStyleId + ")");
        style.setName(name);
        Style.BasedOn basedOn = new Style.BasedOn();
        basedOn.setVal(baseStyleId);
        style.setBasedOn(basedOn);

        PPr pPr = new PPr();
        PPrBase.OutlineLvl outlineLvl = new PPrBase.OutlineLvl();
        outlineLvl.setVal(BigInteger.valueOf(targetLevel - 1L));
        pPr.setOutlineLvl(outlineLvl);

        if (properties.getMerge().isNumberHeadings() && ctx.mainNumbering != null) {
            Integer headingNumId = findHeadingNumberingNumId(ctx.mainNumbering.getJaxbElement());
            if (headingNumId != null) {
                PPrBase.NumPr numPr = new PPrBase.NumPr();
                PPrBase.NumPr.Ilvl ilvl = new PPrBase.NumPr.Ilvl();
                ilvl.setVal(BigInteger.valueOf(targetLevel - 1L));
                numPr.setIlvl(ilvl);
                PPrBase.NumPr.NumId numId = new PPrBase.NumPr.NumId();
                numId.setVal(BigInteger.valueOf(headingNumId));
                numPr.setNumId(numId);
                pPr.setNumPr(numPr);
            }
        }
        style.setPPr(pPr);
        ctx.mainStyles.getJaxbElement().getStyle().add(style);
        return derivedId;
    }

    // ------------------------------------------------------------------
    // Context
    // ------------------------------------------------------------------

    private static final class SubContext {
        private final WordprocessingMLPackage main;
        private final MainDocumentPart mainPart;
        private final MainDocumentPart subPart;
        private final StyleDefinitionsPart mainStyles;
        private final StyleDefinitionsPart subStyles;
        private final NumberingDefinitionsPart mainNumbering;
        private final NumberingDefinitionsPart subNumbering;
        private final String subName;
        private final Map<String, String> relMapping = new HashMap<>();
        private final Map<Integer, Integer> numIdMap = new HashMap<>();
        private final Map<String, String> bookmarkIdMap = new HashMap<>();
        private final Set<String> copyingStyles = new HashSet<>();
        private int imageCount;
        private int binaryCount;
        private int drawingId = 10000;
        private int bookmarkId = 1000;

        private SubContext(WordprocessingMLPackage main, WordprocessingMLPackage sub, String subName,
                           StyleDefinitionsPart mainStyles, NumberingDefinitionsPart mainNumbering) {
            this.main = main;
            this.mainPart = main.getMainDocumentPart();
            this.subPart = sub.getMainDocumentPart();
            this.mainStyles = mainStyles;
            this.mainNumbering = mainNumbering;
            this.subStyles = sub.getMainDocumentPart().getStyleDefinitionsPart();
            this.subNumbering = sub.getMainDocumentPart().getNumberingDefinitionsPart();
            this.subName = subName;
        }
    }
}
