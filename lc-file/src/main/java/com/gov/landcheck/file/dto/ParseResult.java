package com.gov.landcheck.file.dto;

import java.util.List;

import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.PlanningReviewForm;
import com.gov.landcheck.core.bo.entity.PlanningReviewRow;
import com.gov.landcheck.core.bo.entity.CapacityIndicatorInfo;
import com.gov.landcheck.core.bo.entity.ProjectPartySurveySummaryForm;
import com.gov.landcheck.core.bo.entity.RoomInfo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据解析结果DTO
 * 封装从OCR结果中提取的结构化数据
 *
 * @author system
 * @date 2025/01/18
 */
@Data
@NoArgsConstructor
public class ParseResult {

    /**
     * 解析出的数据项列表
     */
    private List<ParsedDataItem> dataItems;

    /**
     * 解析出的房间信息列表
     */
    private List<RoomInfo> roomInfos;

    /**
     * 规划复核表主表（仅规划复核文件类型使用）
     */
    private PlanningReviewForm planningReviewForm;

    /**
     * 规划复核表行（仅规划复核文件类型使用）
     */
    private List<PlanningReviewRow> planningReviewRows;

    /**
     * 项目方实测汇总主表（仅项目方汇总文件类型使用）
     */
    private ProjectPartySurveySummaryForm projectPartySummaryForm;

    /**
     * 容量指标核查表信息（仅容量指标核查文件类型使用）
     */
    private CapacityIndicatorInfo capacityIndicatorInfo;

    /**
     * 处理耗时(毫秒)
     */
    private Long processingTimeMs;

    /**
     * 使用的解析引擎
     */
    private String parseEngine;

    /**
     * 构造函数
     *
     * @param dataItems 解析出的数据项列表
     */
    public ParseResult(List<ParsedDataItem> dataItems) {
        this.dataItems = dataItems;
    }

    /**
     * 构造函数
     *
     * @param dataItems 解析出的数据项列表
     * @param roomInfos 解析出的房间信息列表
     */
    public ParseResult(List<ParsedDataItem> dataItems, List<RoomInfo> roomInfos) {
        this.dataItems = dataItems;
        this.roomInfos = roomInfos;
    }
}