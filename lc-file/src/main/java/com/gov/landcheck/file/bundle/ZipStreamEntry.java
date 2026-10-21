package com.gov.landcheck.file.bundle;

import java.io.IOException;
import java.io.InputStream;

/**
 * 一条只存储、不压缩的 zip 条目。{@code size} 必须等于 {@link Opener} 实际可读字节数。
 */
public record ZipStreamEntry(String name, long size, Opener opener) {

    @FunctionalInterface
    public interface Opener {
        InputStream open() throws IOException;
    }
}
