package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import com.hand.demo.app.service.WorkflowService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import org.hzero.boot.workflow.WorkflowClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WorkFlowServiceImpl implements WorkflowService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private WorkflowClient workflowClient;

    @Override
    public InvCountHeaderDTO approvalCallback(WorkFlowEventDTO workFlowEventDTO) {
        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        invCountHeaderDTO.setCountNumber(workFlowEventDTO.getBusinessKey());
        invCountHeaderDTO.setCountStatus(workFlowEventDTO.getDocStatus());

        InvCountHeader invCountHeader = invCountHeaderRepository.selectOne(invCountHeaderDTO);
        StringBuilder errMsg = new StringBuilder();
        if(invCountHeader == null){
            errMsg.append("There is no Such Data");
        }

//        workflowClient.app

        return null;
    }
}
