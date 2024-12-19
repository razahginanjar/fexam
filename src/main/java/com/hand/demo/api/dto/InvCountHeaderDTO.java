package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.hzero.core.cache.Cacheable;

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
}
