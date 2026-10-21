package com.gov.landcheck.file.bundle;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 每个用户同时只允许一个原件打包下载，避免把 GridFS 打满。
 */
@Component
public class SourceBundleDownloadGate {

    private final Set<Long> activeUsers = ConcurrentHashMap.newKeySet();

    public boolean tryAcquire(long userId) {
        return activeUsers.add(userId);
    }

    public void release(long userId) {
        activeUsers.remove(userId);
    }

    public boolean isBusy(long userId) {
        return activeUsers.contains(userId);
    }
}
