package com.example.docmerge;

import com.example.docmerge.support.TestDocuments;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.DocumentSettingsPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端集成测试：生成 DOCX，调用合并、下载、检查、删除接口，并校验输出文档结构。
 */
@SpringBootTest(properties = {
        "app.storage.dir=target/test-storage",
        "app.storage.retention-hours=1",
        "app.merge.toc-title=目录",
        "app.merge.toc-depth=3"
})
@AutoConfigureMockMvc
class DocumentMergeIntegrationTest {

    private static final String DOCX = TestDocuments.DOCX_CONTENT_TYPE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("合并、下载、结构校验、删除的完整流程")
    void shouldMergeDownloadValidateAndDelete() throws Exception {
        byte[] main = TestDocuments.mainDocument();
        byte[] sub1 = TestDocuments.subDocumentOne();
        byte[] sub2 = TestDocuments.subDocumentTwo();
        byte[] broken = TestDocuments.malformedSubDocument();

        MvcResult mergeResult = mockMvc.perform(multipart("/api/documents/merge")
                        .file(new MockMultipartFile("mainFile", "main.docx", DOCX, main))
                        .file(new MockMultipartFile("subFiles", "sub1.docx", DOCX, sub1))
                        .file(new MockMultipartFile("subFiles", "sub2.docx", DOCX, sub2))
                        .file(new MockMultipartFile("subFiles", "broken.docx", DOCX, broken))
                        .param("outputName", "合并结果"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fileName").value("合并结果.docx"))
                .andExpect(jsonPath("$.data.size").isNumber())
                .andExpect(jsonPath("$.data.warnings").isArray())
                .andReturn();

        JsonNode data = objectMapper.readTree(mergeResult.getResponse().getContentAsByteArray())
                .path("data");
        String fileId = data.path("fileId").asText();
        assertThat(fileId).isNotBlank();
        assertThat(data.path("downloadUrl").asText())
                .isEqualTo("/api/documents/download/" + fileId);
        assertThat(data.path("warnings").toString()).contains("broken");

        boolean hasSub1Paired = false;
        for (JsonNode tag : data.path("tags")) {
            if ("sub1.docx".equals(tag.path("source").asText())
                    && "PAIRED".equals(tag.path("status").asText())) {
                hasSub1Paired = true;
            }
        }
        assertThat(hasSub1Paired).as("sub1 的 PAIRED 标签应出现在返回结果中").isTrue();

        MvcResult download = mockMvc.perform(get("/api/documents/download/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(DOCX)))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("filename*=UTF-8''")))
                .andReturn();

        byte[] mergedBytes = download.getResponse().getContentAsByteArray();
        assertThat(mergedBytes.length).isGreaterThan(1000);
        assertThat(new String(mergedBytes, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");

        WordprocessingMLPackage merged = TestDocuments.load(mergedBytes);
        String text = TestDocuments.documentText(merged);
        String xml = TestDocuments.documentXml(merged);

        assertThat(text)
                .contains("设备检测报告")
                .contains("检测范围如下：")
                .contains("配电柜")
                .contains("同一段落中的内容")
                .contains("跨 run 的标记也能识别")
                .contains("异常项如下：")
                .contains("输入电压")
                .contains("目录")
                .doesNotContain("这段在标记之外")
                .doesNotContain("这段也不应出现")
                .doesNotContain("以下内容也不参与合并")
                .doesNotContain("${");

        assertThat(xml)
                .contains("TOC \\o")
                .contains("<w:tbl")
                .contains("<w:drawing");
        assertThat(TestDocuments.countPartsStartingWith(merged, "/word/media/"))
                .isGreaterThanOrEqualTo(1);

        assertThat(TestDocuments.hasStyle(merged, "MergeH2_Heading1")).isTrue();
        assertThat(TestDocuments.hasStyle(merged, "MergeH3_Heading2")).isTrue();
        String numberingXml = org.docx4j.XmlUtils.marshaltoString(
                merged.getMainDocumentPart().getNumberingDefinitionsPart().getJaxbElement(),
                true, true);
        assertThat(numberingXml).contains("MergeHeadingNumbering");

        DocumentSettingsPart settings = merged.getMainDocumentPart().getDocumentSettingsPart(true);
        assertThat(settings.getContents().getUpdateFields()).isNotNull();
        assertThat(settings.getContents().getUpdateFields().isVal()).isTrue();

        mockMvc.perform(delete("/api/documents/files/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/documents/download/{fileId}", fileId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("inspect 返回成对标签与缺失结束标记告警")
    void shouldInspectTagsAndWarnings() throws Exception {
        mockMvc.perform(multipart("/api/documents/inspect")
                        .file(new MockMultipartFile("file", "sub1.docx", DOCX,
                                TestDocuments.subDocumentOne())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("sub1.docx"))
                .andExpect(jsonPath("$.data.tagCount").value(6))
                .andExpect(jsonPath("$.data.tags[?(@.status == 'PAIRED')]").isNotEmpty());

        mockMvc.perform(multipart("/api/documents/inspect")
                        .file(new MockMultipartFile("file", "broken.docx", DOCX,
                                TestDocuments.malformedSubDocument())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tagCount").value(1))
                .andExpect(jsonPath("$.data.tags[0].status").value("MISSING_END"))
                .andExpect(jsonPath("$.data.warnings[0]", containsString("缺少 end")));
    }

    @Test
    @DisplayName("缺少文件、非 DOCX、非法下载 ID 返回统一错误")
    void shouldRejectInvalidRequests() throws Exception {
        mockMvc.perform(multipart("/api/documents/merge"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        mockMvc.perform(multipart("/api/documents/merge")
                        .file(new MockMultipartFile("mainFile", "main.pdf",
                                "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("subFiles", "sub1.docx", DOCX,
                                TestDocuments.subDocumentOne())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41500));

        mockMvc.perform(get("/api/documents/download/{fileId}", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(delete("/api/documents/files/{fileId}", "00000000-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("健康检查返回 UP")
    void shouldReportHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
