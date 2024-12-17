package com.hand.demo.api.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InvCountInfoDTO {
    @ApiModelProperty(value = "Error Message")
    private String totalErrorMsg;

    @ApiModelProperty(value = "Verification passed list")
    private List<InvCountHeaderDTO> successList;

    @ApiModelProperty(value = "Verification failed list")
    private List<InvCountHeaderDTO> errorList;

}
