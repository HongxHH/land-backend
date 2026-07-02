package com.gov.landcheck.file.dto;

import java.util.List;

import com.gov.landcheck.core.bo.entity.ParsedDataItem;
import com.gov.landcheck.core.bo.entity.RoomInfo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 户室面积对照表解析结果DTO
 * 封装房间信息和合计信息
 *
 * @author system
 * @date 2025/01/20
 */
@Data
@NoArgsConstructor
public class RoomTableParseResult {

    private List<RoomInfo> roomInfos;
    private List<ParsedDataItem> totalItems;
    /** 户室表数据是否来自 LLM 兜底（非用途填充） */
    private boolean parseUsedLlm;
    /** 规则已解析出部分户室后仍触发 LLM 兜底 */
    private boolean roomTablePartialLlm;
    /** 用途列缺失时由 LLM 从勘测成果表填充 */
    private boolean usageFilledByLlm;

    public RoomTableParseResult(List<RoomInfo> roomInfos, List<ParsedDataItem> totalItems) {
        this.roomInfos = roomInfos;
        this.totalItems = totalItems;
    }
}