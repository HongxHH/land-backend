package com.gov.landcheck.project.service;

import java.util.List;
import java.util.Map;

import com.gov.landcheck.core.bo.R.AjaxJson;
import com.gov.landcheck.project.dto.ContractInfoQueryDTO;
import com.gov.landcheck.project.dto.ContractInfoUpdateDTO;
import com.gov.landcheck.project.dto.LandParcelCreateDTO;
import com.gov.landcheck.project.dto.LandParcelUpdateDTO;
import com.gov.landcheck.project.vo.ContractProjectStatsVO;

/**
 * 合同与地块相关服务（合同查询/更新/删除、合同+地块查询、地块创建/更新/删除）
 */
public interface ContractAndLandParcelService {

    AjaxJson queryContractInfos(ContractInfoQueryDTO queryDTO);

    AjaxJson updateContractInfo(ContractInfoUpdateDTO updateDTO);

    AjaxJson getContractWithParcels(Long contractId);

    AjaxJson createLandParcel(LandParcelCreateDTO createDTO);

    AjaxJson updateLandParcel(LandParcelUpdateDTO updateDTO);

    AjaxJson deleteLandParcel(Long parcelId);

    /**
     * 按项目维度获取合同统计信息（面积汇总 + 任意非空的出让方/受让方）
     */
    ContractProjectStatsVO getContractProjectStatsByProjectId(Long projectId);

    /**
     * 批量按项目维度获取合同统计信息
     */
    Map<Long, ContractProjectStatsVO> batchGetContractProjectStatsByProjectIds(List<Long> projectIds);

}
