package com.hand.demo.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class WorkFlowEventDTO implements Serializable {
    /**
     * business key
     */
    private String businessKey;

    /**
     * document status
     */
    private String docStatus;

    /**
     * workflow ID
     */
    private Long workflowId;

    /**
     * approved time
     */
    private Date approvedTime;

}
