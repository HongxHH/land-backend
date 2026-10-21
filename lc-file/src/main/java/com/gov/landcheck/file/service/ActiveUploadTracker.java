package com.gov.landcheck.file.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 登记当前进程里仍在读取请求体的上传。
 * <p>
 * 僵死扫描用最后一次读到字节的时间判断连接是否还在传；进程重启后登记消失，
 * 启动清理仍会处理崩溃留下的 UPLOADING 记录。
 */
@Component
public class ActiveUploadTracker {

    private final ConcurrentHashMap<Long, Long> lastByteAtMs = new ConcurrentHashMap<>();

    public void begin(Long fileId) {
        if (fileId == null) {
            return;
        }
        lastByteAtMs.put(fileId, System.currentTimeMillis());
    }

    /** 仅刷新已经 begin 且尚未 end 的上传，避免关闭后的迟到读把记录重新登记进来。 */
    public void touch(Long fileId) {
        noteBytes(fileId, System.currentTimeMillis());
    }

    public void end(Long fileId) {
        if (fileId == null) {
            return;
        }
        lastByteAtMs.remove(fileId);
    }

    /**
     * @param stallTimeoutMs 超过该时长没有新字节则视为已停住
     */
    public boolean isReceiving(Long fileId, long stallTimeoutMs) {
        return isReceivingAt(fileId, stallTimeoutMs, System.currentTimeMillis());
    }

    void noteBytes(Long fileId, long atMs) {
        if (fileId == null) {
            return;
        }
        lastByteAtMs.computeIfPresent(fileId, (id, previous) -> atMs);
    }

    boolean isReceivingAt(Long fileId, long stallTimeoutMs, long nowMs) {
        if (fileId == null || stallTimeoutMs < 0) {
            return false;
        }
        Long last = lastByteAtMs.get(fileId);
        if (last == null) {
            return false;
        }
        return nowMs - last < stallTimeoutMs;
    }
}
