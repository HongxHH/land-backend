package com.gov.landcheck.file.task.processor.receiver;

import com.gov.landcheck.file.task.processor.receiver.ocr.DoubaoVisionOcrStrategy;
import com.gov.landcheck.file.task.processor.receiver.ocr.OcrProcessingStrategy;
import com.gov.landcheck.file.task.processor.receiver.ocr.PaddleLayoutOcrStrategy;
import org.springframework.stereotype.Component;

import com.gov.landcheck.core.enums.FileContextType;

import jakarta.annotation.Resource;

@Component
public class OcrStrategySelector {

    @Resource
    private PaddleLayoutOcrStrategy paddleLayoutOcrStrategy;

    @Resource
    private DoubaoVisionOcrStrategy doubaoVisionOcrStrategy;

    public OcrProcessingStrategy select(FileContextType type) {
        if (doubaoVisionOcrStrategy.supports(type)) {
            return doubaoVisionOcrStrategy;
        }
        return paddleLayoutOcrStrategy;
    }
}
