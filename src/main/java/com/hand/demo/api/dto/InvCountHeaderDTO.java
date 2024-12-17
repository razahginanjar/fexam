package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountHeader;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.hzero.common.HZeroCacheKey;
import org.hzero.core.cache.CacheValue;

import javax.persistence.Transient;
import java.util.List;

@Getter
@Setter
public class InvCountHeaderDTO extends InvCountHeader {
    @ApiModelProperty(value = "Error Message")
    private String errorMsg;

    private List<InvCountLineDTO> invCountLineDTOList;

    private String countModeMeaning;

    private String countDimensionMeaning;

    private String countStatusMeaning;

    private String countTypeMeaning;


    @CacheValue(key = HZeroCacheKey.USER,
            primaryKey = "counterIds",
            db = 1,
            searchKey = "realName",
            structure = CacheValue.DataStructure.LIST_OBJECT)
    private List<String> counterList;

    @CacheValue(key = HZeroCacheKey.USER,
            primaryKey = "supervisorIds",
            db = 1,
            searchKey = "realName",
            structure = CacheValue.DataStructure.LIST_OBJECT)
    private List<String> supervisorList;

    private List<String> snapshotMaterialList;

    private List<String> snapshotBatchList;

    private boolean isWMSWarehouse;
}
