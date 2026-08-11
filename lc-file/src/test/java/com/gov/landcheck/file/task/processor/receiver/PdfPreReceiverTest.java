package com.gov.landcheck.file.task.processor.receiver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PdfPreReceiverTest {

    @Test
    void createPythonProcessBuilderUsesUnixShellOnLinux() {
        ProcessBuilder processBuilder = PdfPreReceiver.createPythonProcessBuilder("conda activate SR", "Linux");

        List<String> command = processBuilder.command();
        assertThat(command).hasSize(3);
        assertThat(command.get(0)).endsWith("sh");
        assertThat(command.get(1)).isEqualTo("-lc");
        assertThat(command.get(2)).isEqualTo("conda activate SR");
    }

    @Test
    void createPythonProcessBuilderKeepsCmdShellOnWindows() {
        ProcessBuilder processBuilder = PdfPreReceiver.createPythonProcessBuilder("conda activate SR", "Windows 11");

        assertThat(processBuilder.command()).containsExactly("cmd", "/c", "conda activate SR");
    }
}
