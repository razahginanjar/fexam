package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.hzero.boot.workflow.dto.RunInstance;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;
import org.hzero.export.annotation.ExcelSheet;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    @ApiModelProperty(value = "Error Message")
    private String errorMsg;

    private List<InvCountLineDTO> countOrderLineList;

    private String status;

    private String countModeMeaning;

    private String countDimensionMeaning;

    private String countStatusMeaning;

    private String countTypeMeaning;


    private List<UserDTO> counterList;

    private List<UserDTO> supervisorList;

    private List<SnapShotMaterialDTO> snapshotMaterialList;

    private List<SnapShotBatchDTO> snapshotBatchList;

    private boolean isWMSWarehouse;

    private String employeeNumber;

    private boolean isTenantAdminFlag = false;

    private Long userId;

    private String wareHouseCode;

    private String departmentName;

    private List<RunTaskHistory> historyApproval;

    @CacheValue(key = HZeroCacheKey.USER,
            primaryKey = "createdBy",
            searchKey = "realName",
            structure = CacheValue.DataStructure.MAP_OBJECT)
    private String creator;


    //for report
    private String batchCodes;
    private String materialCodes;
    private String superVisorNames;
    private String counterNames;

    @CacheValue(key = "hiam:tenant",
            primaryKey = "tenantId",
            searchKey = "tenantNum",
            structure = CacheValue.DataStructure.MAP_OBJECT)
    private String tenantCode;

    //condition report
    private List<String> docStatuses;
    private String companyCode;
    private String departmentCode;
}
