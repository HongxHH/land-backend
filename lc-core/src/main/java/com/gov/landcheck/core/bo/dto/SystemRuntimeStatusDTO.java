package com.gov.landcheck.core.bo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统运行状态DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemRuntimeStatusDTO {

    private SystemMetrics system;
    private RuntimeMemoryMetrics memory;
    private ThreadMetrics thread;
    private DataSourceMetrics dataSource;
    private GpuMetrics gpu;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemMetrics {
        private double cpuLoadPercent;
        private long totalMemoryBytes;
        private long freeMemoryBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuntimeMemoryMetrics {
        private long heapUsedBytes;
        private long heapMaxBytes;
        private long nonHeapUsedBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadMetrics {
        private int liveThreadCount;
        private int daemonThreadCount;
        private long peakThreadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceMetrics {
        private String poolName;
        private Integer activeConnections;
        private Integer idleConnections;
        private Integer totalConnections;
        private Integer waitingThreads;
        private Integer maxPoolSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GpuMetrics {
        private boolean supported;
        private Integer utilizationPercent;
        private Integer memoryUsedMiB;
        private Integer memoryTotalMiB;
        private String message;
    }
}
