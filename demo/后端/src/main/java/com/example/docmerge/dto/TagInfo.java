package com.example.docmerge.dto;

/**
 * 单个标识的解析结果，用于 inspect 接口和 merge 接口返回。
 *
 * @param name           标识名称，例如 section1
 * @param status         PAIRED / MISSING_END / MISSING_START / DUPLICATE_START / OVERLAP
 * @param source         来源文件名
 * @param startParagraph 起始段落序号（从 0 开始，仅顶层段落）
 * @param endParagraph   结束段落序号
 * @param preview        段落文本预览
 * @param message        说明信息
 */
public record TagInfo(
        String name,
        String status,
        String source,
        Integer startParagraph,
        Integer endParagraph,
        String preview,
        String message
) {
}
