package com.gov.landcheck.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gov.landcheck.file.processing.ProcessingConcurrencyGate;

@Configuration
@EnableConfigurationProperties(FileProcessingProperties.class)
public class FileProcessingConfig {

    public static final String PARSE_PIPELINE_GATE = "parsePipelineConcurrencyGate";
    public static final String PDF_PREPROCESS_GATE = "pdfPreprocessConcurrencyGate";

    @Bean(name = PARSE_PIPELINE_GATE)
    public ProcessingConcurrencyGate parsePipelineConcurrencyGate(FileProcessingProperties properties) {
        int max = properties.getParsePool().getMaxSize();
        return new ProcessingConcurrencyGate("parse-pipeline", max, properties.getGateAcquireTimeoutMs());
    }

    @Bean(name = PDF_PREPROCESS_GATE)
    public ProcessingConcurrencyGate pdfPreprocessConcurrencyGate(FileProcessingProperties properties) {
        int max = properties.getConcurrency().getMaxPythonPreprocess();
        long timeout = Math.max(properties.getGateAcquireTimeoutMs(), 300_000L);
        return new ProcessingConcurrencyGate("pdf-preprocess", max, timeout);
    }
}
