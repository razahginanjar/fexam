package com.hand.demo.api.dto;

import com.hand.demo.domain.entity.InvCountLine;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InvCountLineDTO extends InvCountLine {
    @ApiModelProperty(value = "Error Message")
    private String totalErrorMsg;
}
