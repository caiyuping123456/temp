package com.example.docmerge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用配置。所有配置项都可以在 application.yml 中通过 app.* 覆盖。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Storage storage = new Storage();
    private final Merge merge = new Merge();

    public Storage getStorage() {
        return storage;
    }

    public Merge getMerge() {
        return merge;
    }

    public static class Storage {
        /** 临时文件目录，默认 ${java.io.tmpdir}/docx-merge。 */
        private String dir = System.getProperty("java.io.tmpdir") + "/docx-merge";
        /** 文件保留小时数，超过后由定时任务清理。 */
        private long retentionHours = 2;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public long getRetentionHours() {
            return retentionHours;
        }

        public void setRetentionHours(long retentionHours) {
            this.retentionHours = retentionHours;
        }
    }

    public static class Merge {
        /** 副文档之间是否插入分页符。 */
        private boolean pageBreakBetween = true;
        /** 目录标题。 */
        private String tocTitle = "目录";
        /** 目录深度，默认 3（对应 1 / 1.1 / 1.1.1）。 */
        private int tocDepth = 3;
        /** 副文档标题来源：filename（文件名）或 tag（第一个标识名）。 */
        private String headingTitleSource = "filename";
        /** 是否为标题样式生成自动编号（1、1.1、1.1.1）。 */
        private boolean numberHeadings = true;

        public boolean isPageBreakBetween() {
            return pageBreakBetween;
        }

        public void setPageBreakBetween(boolean pageBreakBetween) {
            this.pageBreakBetween = pageBreakBetween;
        }

        public String getTocTitle() {
            return tocTitle;
        }

        public void setTocTitle(String tocTitle) {
            this.tocTitle = tocTitle;
        }

        public int getTocDepth() {
            return tocDepth;
        }

        public void setTocDepth(int tocDepth) {
            this.tocDepth = Math.max(1, Math.min(9, tocDepth));
        }

        public String getHeadingTitleSource() {
            return headingTitleSource;
        }

        public void setHeadingTitleSource(String headingTitleSource) {
            this.headingTitleSource = headingTitleSource;
        }

        public boolean isNumberHeadings() {
            return numberHeadings;
        }

        public void setNumberHeadings(boolean numberHeadings) {
            this.numberHeadings = numberHeadings;
        }
    }
}
