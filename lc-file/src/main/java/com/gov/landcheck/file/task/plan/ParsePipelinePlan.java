package com.gov.landcheck.file.task.plan;

import java.util.List;

/**
 * 解析阶段定义。各文件类型的阶段列表由对应 {@link com.gov.landcheck.file.task.context.FileParseModule} 提供。
 */
public final class ParsePipelinePlan {

    public static final StageDef PREPROCESS = new StageDef("PREPROCESS", "PDF预处理");
    public static final StageDef OCR = new StageDef("OCR", "OCR识别");
    public static final StageDef PARSE = new StageDef("PARSE", "数据解析");
    public static final StageDef FILL = new StageDef("FILL", "数据回填");
    public static final StageDef VALIDATE = new StageDef("VALIDATE", "数据校验");

    private ParsePipelinePlan() {
    }

    public record StageDef(String code, String displayName) {
    }

    /**
     * 未注册文件类型的默认阶段（合同同类：预处理 → OCR → 解析 → 回填）。
     */
    public static List<StageDef> defaultStages() {
        return List.of(PREPROCESS, OCR, PARSE, FILL);
    }
}
