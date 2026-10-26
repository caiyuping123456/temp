package com.example.docmerge.controller;

import com.example.docmerge.dto.ApiResponse;
import com.example.docmerge.dto.InspectResult;
import com.example.docmerge.dto.MergeResult;
import com.example.docmerge.exception.BizException;
import com.example.docmerge.service.DocxMergeService;
import com.example.docmerge.service.TempFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 文档合并接口。
 */
@RestController
@RequestMapping("/api")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocxMergeService mergeService;
    private final TempFileStore fileStore;

    public DocumentController(DocxMergeService mergeService, TempFileStore fileStore) {
        this.mergeService = mergeService;
        this.fileStore = fileStore;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping(value = "/documents/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MergeResult> merge(
            @RequestParam("mainFile") MultipartFile mainFile,
            @RequestParam("subFiles") List<MultipartFile> subFiles,
            @RequestParam(value = "outputName", required = false) String outputName) {
        MergeResult result = mergeService.merge(mainFile, subFiles, outputName);
        return ApiResponse.ok(result);
    }

    @PostMapping(value = "/documents/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InspectResult> inspect(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "mainFile", required = false) MultipartFile mainFile) {
        MultipartFile actual = file != null ? file : mainFile;
        if (actual == null) {
            throw BizException.badRequest("请通过 file 字段上传一个 .docx 文件");
        }
        return ApiResponse.ok(mergeService.inspect(actual));
    }

    @GetMapping("/documents/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable String fileId) {
        TempFileStore.StoredFile stored = fileStore.find(fileId)
                .orElseThrow(() -> BizException.notFound("文件不存在或已过期，请重新合并"));

        Resource resource = new FileSystemResource(stored.path());
        String encoded = URLEncoder.encode(stored.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(DOCX_CONTENT_TYPE));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"merged.docx\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(stored.size());
        headers.setCacheControl(CacheControl.noStore());
        log.info("下载文件: fileId={}, fileName={}, size={}", fileId, stored.fileName(), stored.size());
        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    @DeleteMapping("/documents/files/{fileId}")
    public ApiResponse<Void> delete(@PathVariable String fileId) {
        boolean deleted = fileStore.delete(fileId);
        if (!deleted) {
            throw BizException.notFound("文件不存在或已被删除");
        }
        return ApiResponse.ok("已删除", null);
    }
}
