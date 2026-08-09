package com.gov.landcheck.file.task.processor.receiver;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PdfPreReceiverTest {

    @Test
    void boundedOutputCaptureKeepsLimitedPrefixAndTruncationMarker() throws Exception {
        PdfPreReceiver.BoundedOutputCapture capture = new PdfPreReceiver.BoundedOutputCapture(8);

        capture.write("abcdefghijkl".getBytes(StandardCharsets.UTF_8));

        assertThat(capture.toLogString()).isEqualTo("abcdefgh...(truncated 4 bytes)");
    }
}
