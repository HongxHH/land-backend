package com.gov.landcheck.core.config.notification;

/**
 * 广播 Topic 键常量，用于
 * {@link com.gov.landcheck.core.service.StationBroadcastService#broadcast}。
 * 约定：有 projectId 时发往 /topic/project/{projectId}/{key}，否则发往 /topic/{key}。
 */
public final class BroadcastTopicConstants {

    private BroadcastTopicConstants() {
    }

    /** 文件解析状态更新（解析成功/失败等），前端可订阅以刷新文件列表/状态 */
    public static final String FILE_UPDATES = "file-updates";
}
