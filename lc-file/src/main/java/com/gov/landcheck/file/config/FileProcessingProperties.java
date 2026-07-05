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
        private long retryIntervalMs = 5_000L;
        /** 每次轮询最多尝试提交的文件数 */
        private int retryBatchSize = 16;
    }

    @Data
    public static class Concurrency {
        private int maxPythonPreprocess = 1;
        /** 运行时动态调参硬上限（parseConcurrency 不可超过此值） */
        private int maxParseConcurrencyLimit = 16;
    }
}
