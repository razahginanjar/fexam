package com.hand.demo.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import com.hand.demo.app.service.WorkflowService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.constant.Constants;
import org.hzero.boot.apaas.common.userinfo.domain.UserVO;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.workflow.WorkflowClient;
import org.hzero.boot.workflow.dto.RunInstance;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.base.BaseConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkFlowServiceImpl implements WorkflowService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private WorkflowClient workflowClient;

    @Autowired
    private IamRemoteService iamRemoteService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public RunInstance startWorkFlow(Long organizationId,
                                           WorkFlowEventDTO workFlowEventDTO,
                                           String departmentCode) {
        try{
            ResponseEntity<String> selectSelf = iamRemoteService.selectSelf();
            UserVO userVO = objectMapper.readValue(selectSelf.getBody(), UserVO.class);
            Map<String, Object> args = new HashMap<>();
            args.put("departmentCode", departmentCode);
            return workflowClient.startInstanceByFlowKey(organizationId,
                    Constants.FLOW_KEY_CODE,
                    workFlowEventDTO.getBusinessKey(),
                    Constants.DIMENSION,
                    String.valueOf(userVO.getId()),
                    args
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<RunTaskHistory> getHistory(Long tenantId, String flowKey, String businessKey) {
        return workflowClient.approveHistoryByFlowKey(tenantId, flowKey, businessKey);
    }
}
