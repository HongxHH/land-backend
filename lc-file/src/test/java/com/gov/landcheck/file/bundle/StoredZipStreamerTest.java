package com.gov.landcheck.file.bundle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;

class StoredZipStreamerTest {

    @Test
    void writesStoredZipWithExactLengthAndUnicodeNames() throws Exception {
        byte[] contract = "contract-pdf".getBytes(StandardCharsets.UTF_8);
        byte[] report = "实测正文".getBytes(StandardCharsets.UTF_8);
        List<ZipStreamEntry> entries = List.of(
                entry("演示项目/合同/土地出让合同.pdf", contract),
                entry("演示项目/合同/土地出让合同 (2).pdf", report),
                entry("演示项目/实测报告/第1期/空文件.pdf", new byte[0]));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StoredZipStreamer.write(output, entries);

        assertEquals(StoredZipStreamer.sizeOf(entries), output.size());
        int flags = (output.toByteArray()[6] & 0xff) | ((output.toByteArray()[7] & 0xff) << 8);
        assertEquals(0x0808, flags);
        Path temp = Files.createTempFile("source-bundle", ".zip");
        try {
            Files.write(temp, output.toByteArray());
            try (ZipFile zip = new ZipFile(temp.toFile(), StandardCharsets.UTF_8)) {
                assertArrayEquals(contract, read(zip, "演示项目/合同/土地出让合同.pdf"));
                assertArrayEquals(report, read(zip, "演示项目/合同/土地出让合同 (2).pdf"));
                assertEquals(0, read(zip, "演示项目/实测报告/第1期/空文件.pdf").length);
                assertEquals(0, zip.getEntry("演示项目/合同/土地出让合同.pdf").getMethod());
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void rejectsPathTraversalAndOversizedArchive() {
        assertThrows(IllegalArgumentException.class,
                () -> StoredZipStreamer.sizeOf(List.of(entry("../secret.pdf", new byte[] { 1 }))));
        assertThrows(IllegalArgumentException.class,
                () -> StoredZipStreamer.sizeOf(List.of(
                        new ZipStreamEntry("huge.bin", StoredZipStreamer.ZIP32_LIMIT,
                                () -> new ByteArrayInputStream(new byte[0])))));

        List<ZipStreamEntry> tooMany = new ArrayList<>();
        for (int i = 0; i <= StoredZipStreamer.ZIP32_ENTRY_LIMIT; i++) {
            tooMany.add(entry("f" + i + ".txt", new byte[] { 1 }));
        }
        assertThrows(IllegalArgumentException.class, () -> StoredZipStreamer.sizeOf(tooMany));
    }

    @Test
    void stopsWhenTheSourceIsShorterThanPlanned() {
        ZipStreamEntry entry = new ZipStreamEntry("演示项目/合同/缺字节.pdf", 8,
                () -> new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
        assertThrows(java.io.IOException.class,
                () -> StoredZipStreamer.write(new ByteArrayOutputStream(), List.of(entry)));
    }

    @Test
    void attachmentHeaderKeepsChineseFilename() {
        String header = DownloadContentDisposition.attachment("滨江项目-原件.zip");
        assertTrue(header.contains("filename=\"sources.zip\""));
        assertTrue(header.contains("filename*=UTF-8''"));
        assertTrue(header.contains("%E6%BB%A8%E6%B1%9F"));
    }

    private static ZipStreamEntry entry(String name, byte[] bytes) {
        return new ZipStreamEntry(name, bytes.length, () -> new ByteArrayInputStream(bytes));
    }

    private static byte[] read(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(entry != null, name);
        try (var input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }
}
