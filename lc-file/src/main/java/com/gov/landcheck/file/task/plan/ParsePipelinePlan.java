package com.gov.landcheck.file.task.plan;

import java.util.List;

import com.gov.landcheck.core.enums.FileContextType;

/**
 * 用于进度可视化
 */
public final class ParsePipelinePlan {

    private ParsePipelinePlan() {
    }

    public record StageDef(String code, String displayName) {
    }

    /**
     * 按文件内容类型返回实际会执行的解析阶段（不含被跳过的阶段）。
     */
    public static List<StageDef> stagesFor(FileContextType contextType) {
        if (contextType == null) {
            return defaultContractLikeStages();
        }
        return switch (contextType) {
            case PROJECT_PARTY_SURVEY_SUMMARY -> List.of(
                    new StageDef("PARSE", "数据解析"),
                    new StageDef("FILL", "数据回填"));
            case SURVEY_REPORT -> List.of(
                    new StageDef("PREPROCESS", "PDF预处理"),
                    new StageDef("OCR", "OCR识别"),
                    new StageDef("PARSE", "数据解析"),
                    new StageDef("FILL", "数据回填"),
                    new StageDef("VALIDATE", "数据校验"));
            default -> defaultContractLikeStages();
        };
    }

    private static List<StageDef> defaultContractLikeStages() {
        return List.of(
                new StageDef("PREPROCESS", "PDF预处理"),
                new StageDef("OCR", "OCR识别"),
                new StageDef("PARSE", "数据解析"),
                new StageDef("FILL", "数据回填"));
    }
}
