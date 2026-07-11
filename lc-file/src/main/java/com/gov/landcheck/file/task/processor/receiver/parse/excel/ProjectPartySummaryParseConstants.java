package com.gov.landcheck.file.task.processor.receiver.parse.excel;

/**
 * 项目方汇总表解析常量（硬编码，不暴露为配置项）。
 */
public final class ProjectPartySummaryParseConstants {

    private ProjectPartySummaryParseConstants() {
    }

    /** 自底向上扫描的最大行数窗口 */
    public static final int TAIL_SCAN_ROWS = 80;

    /** 定位到汇总行后，上下扩展行数（含列头） */
    public static final int REGION_EXPAND_ROWS = 1;

    /** 规则置信度低于此值时走 LLM 兜底 */
    public static final double RULE_MIN_CONFIDENCE = 0.75;
}
