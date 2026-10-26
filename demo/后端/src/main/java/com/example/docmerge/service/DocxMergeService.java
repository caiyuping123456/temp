package com.example.docmerge.service;

import com.example.docmerge.config.AppProperties;
import com.example.docmerge.dto.InspectResult;
import com.example.docmerge.dto.MergeResult;
import com.example.docmerge.dto.TagInfo;
import com.example.docmerge.exception.BizException;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;
import org.docx4j.wml.SectPr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * DOCX 合并核心服务。
 */
@Service
public class DocxMergeService {

    private static final Logger log = LoggerFactory.getLogger(DocxMergeService.class);
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final MarkerParser markerParser;
    private final DocxContentCopier contentCopier;
    private final TocService tocService;
    private final TempFileStore fileStore;
    private final AppProperties properties;

    public DocxMergeService(MarkerParser markerParser,
                            DocxContentCopier contentCopier,
                            TocService tocService,
                            TempFileStore fileStore,
                            AppProperties properties) {
        this.markerParser = markerParser;
        this.contentCopier = contentCopier;
        this.tocService = tocService;
        this.fileStore = fileStore;
        this.properties = properties;
    }

    /**
     * 合并主文档与多个副文档，返回临时文件信息。
     */
    public MergeResult merge(MultipartFile mainFile, List<MultipartFile> subFiles, String outputName) {
        validateMain(mainFile);
        if (subFiles == null || subFiles.isEmpty()) {
            throw BizException.badRequest("subFiles 不能为空，请至少上传一个副文档");
        }
        for (MultipartFile subFile : subFiles) {
            validateDocx(subFile, "副文档");
        }

        List<String> warnings = new ArrayList<>();
        List<TagInfo> allTags = new ArrayList<>();

        WordprocessingMLPackage main = load(mainFile, "主文档");
        contentCopier.initialize(main, warnings);

        // 目录优先插入，后续定位 ${merge-point} 时索引已包含目录内容。
        tocService.insertToc(main, warnings);

        List<Object> mergedBlocks = new ArrayList<>();
        int acceptedSubDocs = 0;

        for (MultipartFile subFile : subFiles) {
            String subName = baseName(subFile.getOriginalFilename());
            log.info("开始处理副文档: {}", subName);
            WordprocessingMLPackage sub = load(subFile, "副文档 [" + subName + "]");
            MarkerParser.MarkerAnalysis analysis = markerParser.analyze(sub);

            allTags.addAll(withSource(analysis.tags(), subName));
            for (MarkerParser.TagIssue issue : analysis.issues()) {
                addWarning(warnings, "副文档 [" + subName + "] " + issue.message());
            }

            if (analysis.pairs().isEmpty()) {
                addWarning(warnings, "副文档 [" + subName + "] 未找到成对的 ${name-start}/${name-end} 标识，已跳过该文档");
                log.warn("副文档 [{}] 没有可用标识对，跳过", subName);
                continue;
            }

            if (acceptedSubDocs > 0 && properties.getMerge().isPageBreakBetween()) {
                mergedBlocks.add(contentCopier.createPageBreak());
            }

            String title = buildTitle(analysis, subName);
            mergedBlocks.add(contentCopier.createHeading(title));

            List<Object> content = contentCopier.copySubDocument(main, sub, analysis, subName, warnings);
            mergedBlocks.addAll(content);
            acceptedSubDocs++;
            log.info("副文档 [{}] 合并完成，标识对 {} 个，顶层内容 {} 个",
                    subName, analysis.pairs().size(), content.size());
        }

        if (acceptedSubDocs == 0) {
            addWarning(warnings, "没有副文档成功合并，输出文档仅保留主文档内容和目录");
        }

        int insertIndex = findMergePointAndRemove(main, warnings);
        if (insertIndex < 0) {
            insertIndex = indexBeforeSectPr(main);
        }
        List<Object> body = main.getMainDocumentPart().getContent();
        if (insertIndex > body.size()) {
            insertIndex = body.size();
        }
        body.addAll(insertIndex, mergedBlocks);

        String normalizedOutputName = normalizeOutputName(outputName, mainFile.getOriginalFilename());
        String fileId = UUID.randomUUID().toString();
        Path target = fileStore.allocate(fileId);
        try {
            main.save(target.toFile());
            TempFileStore.StoredFile stored = fileStore.register(fileId, normalizedOutputName, target);
            log.info("合并完成: fileId={}, fileName={}, size={}, tags={}, warnings={}",
                    fileId, normalizedOutputName, stored.size(), allTags.size(), warnings.size());
            return new MergeResult(
                    fileId,
                    stored.fileName(),
                    stored.size(),
                    "/api/documents/download/" + fileId,
                    List.copyOf(warnings),
                    List.copyOf(allTags));
        } catch (Exception e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 忽略清理异常
            }
            log.error("保存合并结果失败: fileId={}", fileId, e);
            throw BizException.internal("生成合并文档失败：" + e.getMessage());
        }
    }

    /**
     * 检查单文档中的标识，便于调试。
     */
    public InspectResult inspect(MultipartFile file) {
        validateDocx(file, "文档");
        String fileName = baseName(file.getOriginalFilename());
        WordprocessingMLPackage pkg = load(file, "文档 [" + fileName + "]");
        MarkerParser.MarkerAnalysis analysis = markerParser.analyze(pkg);

        List<String> warnings = new ArrayList<>();
        for (MarkerParser.TagIssue issue : analysis.issues()) {
            warnings.add(issue.message());
        }
        return new InspectResult(
                fileName,
                analysis.paragraphs().size(),
                analysis.occurrenceCount(),
                withSource(analysis.tags(), fileName),
                warnings);
    }

    private void validateMain(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("mainFile 不能为空，请上传一个 .docx 主文档");
        }
        validateDocx(file, "主文档");
    }

    private void validateDocx(MultipartFile file, String role) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest(role + "不能为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw BizException.unsupportedMedia(role + "必须是 .docx 文件（不支持 .doc/.docm/.pdf）");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 41300, role + "超过 50MB 限制");
        }
    }

    private WordprocessingMLPackage load(MultipartFile file, String role) {
        try (InputStream in = file.getInputStream()) {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(in);
            if (pkg.getMainDocumentPart() == null) {
                throw BizException.badRequest(role + "不是有效的 Word 文档");
            }
            log.info("已加载{}: {}, 大小 {} bytes", role, file.getOriginalFilename(), file.getSize());
            return pkg;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("加载{}失败: {}", role, e.getMessage());
            throw BizException.badRequest(role + " 解析失败，请确认是有效的 .docx 文件（错误：" + e.getMessage() + "）");
        }
    }

    /**
     * 查找 ${merge-point}：独占段落则删除段落后原位插入，否则插入到段落之后。
     *
     * @return 插入位置；未找到返回 -1
     */
    private int findMergePointAndRemove(WordprocessingMLPackage main, List<String> warnings) {
        List<Object> body = main.getMainDocumentPart().getContent();
        int insertIndex = -1;
        boolean found = false;
        for (int i = 0; i < body.size(); i++) {
            Object block = XmlUtils.unwrap(body.get(i));
            if (!(block instanceof P p)) {
                continue;
            }
            int offset = markerParser.indexOfLiteral(p, MarkerParser.MERGE_POINT);
            if (offset < 0) {
                continue;
            }
            while (offset >= 0) {
                markerParser.deleteRange(p, offset, offset + MarkerParser.MERGE_POINT.length());
                offset = markerParser.indexOfLiteral(p, MarkerParser.MERGE_POINT);
            }
            if (!found) {
                found = true;
                if (markerParser.isBlank(p)) {
                    body.remove(i);
                    insertIndex = i;
                } else {
                    insertIndex = i + 1;
                }
            } else {
                addWarning(warnings, "主文档中存在多个 ${merge-point}，仅第一个生效，其余已删除");
            }
        }
        if (found) {
            log.info("发现 ${merge-point}，合并内容插入位置 index={}", insertIndex);
        }
        return insertIndex;
    }

    /**
     * sectPr 必须是 body 的最后一个元素，追加内容时插在它之前。
     */
    private int indexBeforeSectPr(WordprocessingMLPackage main) {
        List<Object> body = main.getMainDocumentPart().getContent();
        for (int i = body.size() - 1; i >= 0; i--) {
            if (XmlUtils.unwrap(body.get(i)) instanceof SectPr) {
                return i;
            }
        }
        return body.size();
    }

    private String buildTitle(MarkerParser.MarkerAnalysis analysis, String subName) {
        if ("tag".equalsIgnoreCase(properties.getMerge().getHeadingTitleSource())
                && !analysis.pairs().isEmpty()) {
            return analysis.pairs().get(0).name();
        }
        return stripDocxExtension(subName);
    }

    private List<TagInfo> withSource(List<TagInfo> tags, String source) {
        List<TagInfo> result = new ArrayList<>(tags.size());
        for (TagInfo tag : tags) {
            result.add(new TagInfo(tag.name(), tag.status(), source, tag.startParagraph(),
                    tag.endParagraph(), tag.preview(), tag.message()));
        }
        return result;
    }

    private static void addWarning(List<String> warnings, String message) {
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private static String baseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document.docx";
        }
        String normalized = fileName.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static String stripDocxExtension(String fileName) {
        String base = baseName(fileName);
        return base.toLowerCase(Locale.ROOT).endsWith(".docx")
                ? base.substring(0, base.length() - 5) : base;
    }

    /**
     * 清洗输出文件名，防止路径穿越和非法字符。
     */
    static String normalizeOutputName(String outputName, String mainFileName) {
        String base = outputName == null ? "" : outputName.trim();
        if (base.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            base = base.substring(0, base.length() - 5);
        }
        if (base.isBlank()) {
            base = stripDocxExtension(mainFileName) + "-merged";
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("[. ]+$", "")
                .trim();
        if (base.isBlank()) {
            base = "merged-" + Instant.now().toEpochMilli();
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base + ".docx";
    }

    public record DownloadInfo(TempFileStore.StoredFile file) {
    }
}
