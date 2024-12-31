package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hzero.core.cache.Cacheable;

import javax.persistence.Column;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class InvCountLineDTO extends InvCountLine implements Cacheable {
    @ApiModelProperty(value = "Error Message")
    private String totalErrorMsg;

    private String materialCode;

    private List<Long> idsHeader;

    private boolean tenantAdminFlag = false;

    private Long userId;

    private String supervisorIds;

    private List<UserDTO> counterList;

    //for report
    private String counterLineNames;
    private String itemName;
    private String batchCode;
    private String itemCode;
}
