package com.example.docmerge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DocMerge 示例应用入口。
 *
 * <p>提供 DOCX 主/副文档按 ${tag-start}/${tag-end} 标记合并、生成目录、临时下载等能力。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DocMergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocMergeApplication.class, args);
    }
}
