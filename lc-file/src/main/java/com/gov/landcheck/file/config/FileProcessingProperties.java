package com.gov.landcheck.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import lombok.Data;

/**
 * 文件上传后处理与解析管道的资源与并发配置。
 */
@Data
@ConfigurationProperties(prefix = "landcheck.file.processing")
public class FileProcessingProperties {

    private Pool uploadPool = new Pool(4, 4, 0);
    private Pool parsePool = new Pool(2, 2, 512);
    private AutoParse autoParse = new AutoParse();
    private Concurrency concurrency = new Concurrency();
    private StaleUpload staleUpload = new StaleUpload();
    private int thumbnailDpi = 120;
    private long gateAcquireTimeoutMs = 30_000L;

    @PostConstruct
    void validate() {
        uploadPool.validate("upload-pool");
        parsePool.validate("parse-pool");
        if (thumbnailDpi < 72 || thumbnailDpi > 300) {
            throw new IllegalStateException("landcheck.file.processing.thumbnail-dpi must be between 72 and 300");
        }
        if (parsePool.getCoreSize() != parsePool.getMaxSize()) {
            throw new IllegalStateException(
                    "landcheck.file.processing.parse-pool core-size must equal max-size (single parse concurrency N)");
        }
        if (concurrency.getMaxParseConcurrencyLimit() < 1) {
            throw new IllegalStateException(
                    "landcheck.file.processing.concurrency.max-parse-concurrency-limit must be >= 1");
        }
        if (parsePool.getMaxSize() > concurrency.getMaxParseConcurrencyLimit()) {
            throw new IllegalStateException(
                    "landcheck.file.processing.parse-pool.max-size cannot exceed concurrency.max-parse-concurrency-limit");
        }
        if (autoParse.getRetryIntervalMs() < 1_000L) {
            throw new IllegalStateException(
                    "landcheck.file.processing.auto-parse.retry-interval-ms must be >= 1000");
        }
        if (autoParse.getRetryBatchSize() < 1) {
            throw new IllegalStateException(
                    "landcheck.file.processing.auto-parse.retry-batch-size must be >= 1");
        }
        staleUpload.validate();
    }

    @Data
    public static class Pool {
        private int coreSize;
        private int maxSize;
        private int queueCapacity;

        public Pool() {
        }

        public Pool(int coreSize, int maxSize, int queueCapacity) {
            this.coreSize = coreSize;
            this.maxSize = maxSize;
            this.queueCapacity = queueCapacity;
        }

        void validate(String name) {
            if (coreSize < 1 || maxSize < 1) {
                throw new IllegalStateException(
                        "landcheck.file.processing." + name + " core-size/max-size must be >= 1");
            }
            if (coreSize > maxSize) {
                throw new IllegalStateException(
                        "landcheck.file.processing." + name + " core-size cannot exceed max-size");
            }
            if (queueCapacity < 0) {
                throw new IllegalStateException("landcheck.file.processing." + name + " queue-capacity must be >= 0");
            }
        }
    }

    @Data
    public static class AutoParse {
        /** 上传后处理完成是否自动提交解析任务到线程池 */
        private boolean enabled = true;
        /** 线程池满时 WAITING_PARSE 文件的轮询重试间隔（毫秒） */
        private long retryIntervalMs = 30_000L;
        /** 每次轮询最多尝试提交的文件数 */
        private int retryBatchSize = 16;
    }

    @Data
    public static class Concurrency {
        private int maxPythonPreprocess = 1;
        /** 运行时动态调参硬上限（parseConcurrency 不可超过此值） */
        private int maxParseConcurrencyLimit = 16;
    }

    /**
     * 上传中 / 后处理中僵死状态清理：超时或进程重启后标为 UPLOAD_FAIL，由用户删除后重传。
     * <p>
     * post-process-timeout 需覆盖大 PDF 缩略图最坏耗时；定时扫描还会跳过仍在
     * UploadThreadPool 中的 fileId，避免活任务被误杀。
     */
    @Data
    public static class StaleUpload {
        private boolean enabled = true;
        /** UPLOADING 超过该时长视为僵死（毫秒） */
        private long uploadingTimeoutMs = 600_000L;
        /**
         * WAITING_POST_PROCESS 超过该时长视为僵死（毫秒）。
         * 默认 20 分钟，覆盖大扫描件落盘+PDFBox 首页渲染；仍活跃的任务不会被标失败。
         */
        private long postProcessTimeoutMs = 1_200_000L;
        /** 定时扫描间隔（毫秒） */
        private long scanIntervalMs = 60_000L;
        /**
         * 启动恢复宽限期（毫秒）：只清理 update_time 早于 now-grace 的记录，
         * 避免 ApplicationRunner 阶段误杀刚写入的上传/后处理记录。
         */
        private long startupGraceMs = 60_000L;

        void validate() {
            if (uploadingTimeoutMs < 60_000L) {
                throw new IllegalStateException(
                        "landcheck.file.processing.stale-upload.uploading-timeout-ms must be >= 60000");
            }
            if (postProcessTimeoutMs < 60_000L) {
                throw new IllegalStateException(
                        "landcheck.file.processing.stale-upload.post-process-timeout-ms must be >= 60000");
            }
            if (scanIntervalMs < 10_000L) {
                throw new IllegalStateException(
                        "landcheck.file.processing.stale-upload.scan-interval-ms must be >= 10000");
            }
            if (startupGraceMs < 0L) {
                throw new IllegalStateException(
                        "landcheck.file.processing.stale-upload.startup-grace-ms must be >= 0");
            }
        }
    }
}
