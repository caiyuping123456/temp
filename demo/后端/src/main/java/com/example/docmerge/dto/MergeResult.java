package com.example.docmerge.dto;

import java.util.List;

/**
 * 合并成功后的返回信息。临时文件只存在于服务端，下载地址为相对路径。
 */
public record MergeResult(
        String fileId,
        String fileName,
        long size,
        String downloadUrl,
        List<String> warnings,
        List<TagInfo> tags
) {
}
