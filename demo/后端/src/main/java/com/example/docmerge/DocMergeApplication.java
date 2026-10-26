package com.example.docmerge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 文档合并服务入口。 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DocMergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocMergeApplication.class, args);
    }
}
