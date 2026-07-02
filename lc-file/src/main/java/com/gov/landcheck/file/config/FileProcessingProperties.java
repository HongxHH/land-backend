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
    private Pool parsePool = new Pool(2, 3, 512);
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
        if (autoParse.getMaxPending() < 1) {
            throw new IllegalStateException("landcheck.file.processing.auto-parse.max-pending must be >= 1");
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
        private boolean enabled = true;
        private long deferMs = 5_000L;
        private int maxPending = 2_000;
        private long retryDelayMs = 5_000L;
    }

    @Data
    public static class Concurrency {
        private int maxActiveParsePipelines = 2;
        private int maxPythonPreprocess = 1;
    }
}
