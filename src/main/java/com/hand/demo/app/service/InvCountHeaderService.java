package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.api.dto.WorkFlowEventDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;
import org.hzero.core.base.AopProxy;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaders);

    /**
     * 保存数据
     *
     * @param invCountHeaders 数据
     */
    List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders);

    /**
     * Manually saves the inventory count information after validation checks.
     *
     * @param invCountHeaderDTOS List of inventory count header DTOs.
     * @return Detailed information about the inventory count process.
     */
    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);

    /**
     * Checks the inventory count information and removes invalid entries.
     *
     * @param invCountHeaderDTOS List of inventory count header DTOs.
     * @return Updated inventory count information after removal.
     */
    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);

    /**
     * Retrieves detailed information for a specific inventory count header.
     *
     * @param countHeaderId ID of the inventory count header.
     * @return Detailed DTO of the requested inventory count header.
     */
    InvCountHeaderDTO detail (Long countHeaderId);

    /**
     * Executes validation checks on the inventory count headers.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return Detailed information about the inventory count process.
     */
    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Executes the inventory count process for the provided headers.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return List of processed inventory count header DTOs.
     */
    List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Synchronizes the inventory count data with the WMS (Warehouse Management System).
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return Detailed information about the inventory count synchronization.
     */
    InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Synchronizes a single inventory count result with the WMS.
     *
     * @param headerDTO Inventory count header DTO to be synchronized.
     * @return Updated inventory count header DTO after synchronization.
     */
    InvCountHeaderDTO countResultSync(InvCountHeaderDTO headerDTO);

    /**
     * Validates the inventory count data before submission.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return Detailed information about the validation process.
     */
    InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Submits the inventory count data for further processing.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return List of submitted inventory count header DTOs.
     */
    List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Saves the inventory count order data.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return Detailed information about the saved inventory count order.
     */
    InvCountInfoDTO orderSave(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Executes the inventory count order process.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return Detailed information about the executed inventory count order.
     */
    InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Submits the inventory count order data for finalization.
     *
     * @param headerDTOS List of inventory count header DTOs.
     * @return Detailed information about the submitted inventory count order.
     */
    InvCountInfoDTO orderSubmit(List<InvCountHeaderDTO> headerDTOS);

    /**
     * Generates a report based on the provided inventory count header data.
     *
     * @param invCountHeaderDTO Inventory count header DTO with report parameters.
     * @return List of inventory count header DTOs included in the report.
     */
    List<InvCountHeaderDTO> report(InvCountHeaderDTO invCountHeaderDTO);

    /**
     * Handles callback events for an inventory count header.
     *
     * @param workFlowEventDTO Workflow event data transfer object.
     */
    void callbackHeader(WorkFlowEventDTO workFlowEventDTO);
}

