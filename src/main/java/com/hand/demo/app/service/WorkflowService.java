package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;

public interface WorkflowService {
    InvCountHeaderDTO approvalCallback(WorkFlowEventDTO workFlowEventDTO);

}
