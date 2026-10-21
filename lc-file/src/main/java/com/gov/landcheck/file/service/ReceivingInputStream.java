package com.gov.landcheck.file.service;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 从请求体读到字节时刷新 {@link ActiveUploadTracker}，让慢传输不被僵死扫描误杀。
 */
public final class ReceivingInputStream extends FilterInputStream {

    private final Long fileId;
    private final ActiveUploadTracker tracker;

    public ReceivingInputStream(InputStream in, Long fileId, ActiveUploadTracker tracker) {
        super(in);
        this.fileId = fileId;
        this.tracker = tracker;
    }

    @Override
    public int read() throws IOException {
        int value = in.read();
        if (value >= 0) {
            tracker.touch(fileId);
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int count = in.read(buffer, offset, length);
        if (count > 0) {
            tracker.touch(fileId);
        }
        return count;
    }
}
