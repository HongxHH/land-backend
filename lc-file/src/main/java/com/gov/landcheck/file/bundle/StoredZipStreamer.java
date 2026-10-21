package com.gov.landcheck.file.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 流式写出 zip32「仅存储」包。
 * <p>
 * PDF、Excel、图片本身已压缩，这里不再做 Deflate。本地文件头把 CRC 和长度留空，
 * 读完再写数据描述符，这样每个原件只从存储读一次，也能在开流前算准总长度。
 * JDK {@code ZipOutputStream} 的 STORED 模式要求事先有 CRC，做不到这一点。
 * 中央目录记录真实偏移，解压器按目录定位，描述符不影响下一条目。
 */
public final class StoredZipStreamer {

    public static final long ZIP32_LIMIT = 0xFFFF_FFFFL;
    public static final int ZIP32_ENTRY_LIMIT = 65_535;

    private static final int FLAG = 0x0808;
    private static final int VERSION = 20;
    private static final int LOCAL_HEADER = 30;
    private static final int DATA_DESCRIPTOR = 16;
    private static final int CENTRAL_HEADER = 46;
    private static final int EOCD = 22;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int FLUSH_EVERY = 256 * 1024;

    private StoredZipStreamer() {
    }

    public static long sizeOf(List<ZipStreamEntry> entries) {
        validate(entries, false);
        long localEnd = 0;
        long central = 0;
        for (ZipStreamEntry entry : entries) {
            int nameLength = nameBytes(entry.name()).length;
            localEnd += LOCAL_HEADER + (long) nameLength + entry.size() + DATA_DESCRIPTOR;
            central += CENTRAL_HEADER + (long) nameLength;
        }
        long total = localEnd + central + EOCD;
        if (localEnd > ZIP32_LIMIT || central > ZIP32_LIMIT || total > ZIP32_LIMIT) {
            throw new IllegalArgumentException("压缩包超过 4GB，无法一次打包");
        }
        return total;
    }

    public static void write(OutputStream raw, List<ZipStreamEntry> entries) throws IOException {
        validate(entries, true);
        long expected = sizeOf(entries);
        CountingOutputStream out = new CountingOutputStream(raw);
        LocalDateTime now = LocalDateTime.now();
        int dosTime = dosTime(now);
        int dosDate = dosDate(now);
        byte[] buffer = new byte[BUFFER_SIZE];
        List<CentralEntry> centrals = new ArrayList<>(entries.size());

        for (ZipStreamEntry entry : entries) {
            byte[] name = nameBytes(entry.name());
            long localOffset = out.count();
            writeLocalHeader(out, name, dosTime, dosDate);
            CRC32 crc = new CRC32();
            try (InputStream input = entry.opener().open()) {
                if (input == null) {
                    throw new IOException("无法打开原件: " + entry.name());
                }
                copyExactly(input, out, entry.size(), crc, buffer);
            }
            writeDataDescriptor(out, crc.getValue(), entry.size());
            centrals.add(new CentralEntry(name, crc.getValue(), entry.size(), localOffset));
        }

        long centralOffset = out.count();
        for (CentralEntry central : centrals) {
            writeCentralHeader(out, central, dosTime, dosDate);
        }
        long centralSize = out.count() - centralOffset;
        writeEndOfCentralDirectory(out, centrals.size(), centralSize, centralOffset);
        out.flush();
        if (out.count() != expected) {
            throw new IOException("zip 长度与计划不一致: expected=" + expected + ", actual=" + out.count());
        }
    }

    private static void validate(List<ZipStreamEntry> entries, boolean requireOpener) {
        if (entries == null) {
            throw new IllegalArgumentException("压缩条目不能为空");
        }
        if (entries.size() > ZIP32_ENTRY_LIMIT) {
            throw new IllegalArgumentException("原件数量超过 65535，无法一次打包");
        }
        for (ZipStreamEntry entry : entries) {
            if (entry == null || (requireOpener && entry.opener() == null)) {
                throw new IllegalArgumentException("压缩条目不完整");
            }
            if (entry.size() < 0 || entry.size() > ZIP32_LIMIT) {
                throw new IllegalArgumentException("单个原件超过 4GB，无法一次打包");
            }
            nameBytes(entry.name());
        }
    }

    static byte[] nameBytes(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("压缩路径为空");
        }
        if (name.startsWith("/") || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("压缩路径非法");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("压缩路径非法");
            }
        }
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("压缩路径过长");
        }
        return bytes;
    }

    private static void copyExactly(InputStream input, CountingOutputStream out, long size, CRC32 crc, byte[] buffer)
            throws IOException {
        long remaining = size;
        long sinceFlush = 0;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = input.read(buffer, 0, toRead);
            if (read < 0) {
                throw new IOException("原件长度短于记录值，已中止打包");
            }
            out.write(buffer, 0, read);
            crc.update(buffer, 0, read);
            remaining -= read;
            sinceFlush += read;
            if (sinceFlush >= FLUSH_EVERY) {
                out.flush();
                sinceFlush = 0;
            }
        }
    }

    private static void writeLocalHeader(OutputStream out, byte[] name, int dosTime, int dosDate) throws IOException {
        writeInt(out, 0x04034b50L);
        writeShort(out, VERSION);
        writeShort(out, FLAG);
        writeShort(out, 0);
        writeShort(out, dosTime);
        writeShort(out, dosDate);
        writeInt(out, 0);
        writeInt(out, 0);
        writeInt(out, 0);
        writeShort(out, name.length);
        writeShort(out, 0);
        out.write(name);
    }

    private static void writeDataDescriptor(OutputStream out, long crc, long size) throws IOException {
        writeInt(out, 0x08074b50L);
        writeInt(out, crc);
        writeInt(out, size);
        writeInt(out, size);
    }

    private static void writeCentralHeader(OutputStream out, CentralEntry entry, int dosTime, int dosDate)
            throws IOException {
        writeInt(out, 0x02014b50L);
        writeShort(out, VERSION);
        writeShort(out, VERSION);
        writeShort(out, FLAG);
        writeShort(out, 0);
        writeShort(out, dosTime);
        writeShort(out, dosDate);
        writeInt(out, entry.crc);
        writeInt(out, entry.size);
        writeInt(out, entry.size);
        writeShort(out, entry.name.length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, entry.localOffset);
        out.write(entry.name);
    }

    private static void writeEndOfCentralDirectory(OutputStream out, int count, long centralSize, long centralOffset)
            throws IOException {
        writeInt(out, 0x06054b50L);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, count);
        writeShort(out, count);
        writeInt(out, centralSize);
        writeInt(out, centralOffset);
        writeShort(out, 0);
    }

    private static int dosTime(LocalDateTime time) {
        return (time.getSecond() / 2) | (time.getMinute() << 5) | (time.getHour() << 11);
    }

    private static int dosDate(LocalDateTime time) {
        int year = Math.max(time.getYear(), 1980);
        return time.getDayOfMonth() | (time.getMonthValue() << 5) | ((year - 1980) << 9);
    }

    private static void writeShort(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
    }

    private static void writeInt(OutputStream out, long value) throws IOException {
        out.write((int) (value & 0xff));
        out.write((int) ((value >>> 8) & 0xff));
        out.write((int) ((value >>> 16) & 0xff));
        out.write((int) ((value >>> 24) & 0xff));
    }

    private record CentralEntry(byte[] name, long crc, long size, long localOffset) {
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count;

        private CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        private long count() {
            return count;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
    }
}
