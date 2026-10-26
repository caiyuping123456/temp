package com.example.docmerge.dto;

import java.util.List;

/**
 * 文档标识检查结果。
 */
public record InspectResult(
        String fileName,
        int paragraphCount,
        int tagCount,
        List<TagInfo> tags,
        List<String> warnings
) {
}
