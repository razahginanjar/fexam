package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import org.hzero.boot.workflow.dto.RunInstance;
import org.hzero.boot.workflow.dto.RunTaskHistory;

import java.util.List;

public interface WorkflowService {
    RunInstance startWorkFlow(Long organizationId, WorkFlowEventDTO workFlowEventDTO,
                              String departmentCode);
    List<RunTaskHistory> getHistory(Long tenantId, String flowKey, String businessKey);
}
