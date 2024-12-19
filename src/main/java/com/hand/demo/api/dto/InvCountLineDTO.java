package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class InvCountLineDTO extends InvCountLine {
    @ApiModelProperty(value = "Error Message")
    private String totalErrorMsg;

    private String materialCode;

    private List<Long> idsHeader;

    private boolean tenantAdminFlag = false;

    private Long userId;

    private String supervisorIds;
}
