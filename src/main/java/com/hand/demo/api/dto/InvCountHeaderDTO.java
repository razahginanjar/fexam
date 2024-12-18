package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;
import org.hzero.core.cache.Cacheable;

import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader implements Cacheable {
    @ApiModelProperty(value = "Error Message")
    private String errorMsg;

    private List<InvCountLineDTO> invCountLineDTOList;

    private String status;

    private String countModeMeaning;

    private String countDimensionMeaning;

    private String countStatusMeaning;

    private String countTypeMeaning;


    private List<UserDTO> counterList;

    private List<UserDTO> supervisorList;

    private List<String> snapshotMaterialList;

    private List<String> snapshotBatchList;

    private boolean isWMSWarehouse;

    private String employeeNumber;
}
