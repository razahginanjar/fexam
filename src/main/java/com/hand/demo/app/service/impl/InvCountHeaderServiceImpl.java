package com.hand.demo.app.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.constant.CountStatus;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import jodd.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.boot.apaas.common.userinfo.domain.UserVO;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.base.AopProxy;
import org.hzero.core.base.BaseAppService;
import org.hzero.mybatis.domian.Condition;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */
@Service
@Slf4j
public class InvCountHeaderServiceImpl extends BaseAppService implements InvCountHeaderService, AopProxy<InvCountHeaderServiceImpl> {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;
    @Autowired
    private CodeRuleBuilder codeRuleBuilder;
    @Autowired
    private Utils utils;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileClient profileClient;
    @Autowired
    private IamCompanyRepository iamCompanyRepository;
    @Autowired
    private IamRemoteService iamRemoteService;
    @Autowired
    private InvWarehouseRepository invWarehouseRepository;
    @Autowired
    private InvMaterialRepository invMaterialRepository;
    @Autowired
    private InvBatchRepository invBatchRepository;
    @Autowired
    private InvCountLineRepository invCountLineRepository;
    @Autowired
    private IamDepartmentRepository iamDepartmentRepository;
    @Autowired
    private InvStockRepository invStockRepository;
    @Autowired
    private InvCountLineService invCountLineService;
    @Autowired
    private InvCountExtraRepository invCountExtraRepository;
    @Autowired
    private InvStockService invStockService;
    @Autowired
    private InvCountExtraService invCountExtraService;
    @Autowired
    private IamCompanyService iamCompanyService;
    @Autowired
    private InvWarehouseService invWarehouseService;
    @Autowired
    private IamDepartmentService iamDepartmentService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private InvBatchService invBatchService;
    @Autowired
    private InvMaterialService invMaterialService;

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    /**
     * Saves a list of inventory count headers by performing insert or update operations.
     *
     * @param invCountHeaders List of InvCountHeaderDTO objects to be saved.
     * @return The processed list of InvCountHeaderDTO objects.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        // Return immediately if the input list is null or empty
        if (CollectionUtils.isEmpty(invCountHeaders)) {
            return invCountHeaders;
        }

        // Split the input list into insert and update lists based on the presence of countHeaderId
        List<InvCountHeaderDTO> insertList = invCountHeaders.stream()
                .filter(header -> header.getCountHeaderId() == null)
                .collect(Collectors.toList());

        List<InvCountHeaderDTO> updateList = invCountHeaders.stream()
                .filter(header -> header.getCountHeaderId() != null)
                .collect(Collectors.toList());

        // Generate count numbers for new records
        insertList.forEach(header -> {
            Map<String, String> args = new HashMap<>();
            args.put("customSegment", header.getTenantId().toString());
            String generatedCode = codeRuleBuilder.generateCode(Constants.RULE_BUILDER_CODE, args);
            header.setCountNumber(generatedCode);
        });

        // Process update records if the update list is not empty
        if (!CollectionUtils.isEmpty(updateList)) {
            // Filter update records by their status
            List<InvCountHeaderDTO> drafts = updateList.stream()
                    .filter(header -> CountStatus.DRAFT.toString().equals(header.getCountStatus()))
                    .collect(Collectors.toList());

            List<InvCountHeaderDTO> inCounting = updateList.stream()
                    .filter(header -> CountStatus.INCOUNTING.toString().equals(header.getCountStatus()))
                    .collect(Collectors.toList());

            // Batch update for draft records
            if (!CollectionUtils.isEmpty(drafts)) {
                invCountHeaderRepository.batchUpdateOptional(
                        new ArrayList<>(drafts),
                        InvCountHeader.FIELD_COMPANY_ID,
                        InvCountHeader.FIELD_DEPARTMENT_ID,
                        InvCountHeader.FIELD_WAREHOUSE_ID,
                        InvCountHeader.FIELD_COUNT_DIMENSION,
                        InvCountHeader.FIELD_COUNT_TYPE,
                        InvCountHeader.FIELD_COUNT_MODE,
                        InvCountHeader.FIELD_COUNT_TIME_STR,
                        InvCountHeader.FIELD_COUNTER_IDS,
                        InvCountHeader.FIELD_SUPERVISOR_IDS,
                        InvCountHeader.FIELD_SNAPSHOT_MATERIAL_IDS,
                        InvCountHeader.FIELD_SNAPSHOT_BATCH_IDS,
                        InvCountHeader.FIELD_REMARK
                );
            }

            // Batch update for in-counting records and their associated lines
            if (!CollectionUtils.isEmpty(inCounting)) {
                invCountHeaderRepository.batchUpdateOptional(
                        new ArrayList<>(inCounting),
                        InvCountHeader.FIELD_REMARK,
                        InvCountHeader.FIELD_REASON
                );

                inCounting.forEach(header -> {
                    if (!CollectionUtils.isEmpty(header.getCountOrderLineList())) {
                        invCountLineRepository.batchUpdateOptional(
                                new ArrayList<>(header.getCountOrderLineList()),
                                InvCountLine.FIELD_REMARK,
                                InvCountLine.FIELD_COUNTER_IDS,
                                InvCountLine.FIELD_UNIT_DIFF_QTY,
                                InvCountLine.FIELD_UNIT_QTY
                        );
                    }
                });
            }
        }

        // Batch insert new records
        invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));

        // Return the original input list, now processed
        return invCountHeaders;
    }

    /**
     * Service class to manually save and validate inventory count headers.
     */
    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // Return an empty DTO if the input list is null or empty
        if (CollectionUtils.isEmpty(invCountHeaderDTOS)) {
            return new InvCountInfoDTO();
        }

        // Separate the input into insert and update lists based on the presence of countHeaderId
        List<InvCountHeaderDTO> insertList = invCountHeaderDTOS.stream()
                .filter(header -> header.getCountHeaderId() == null)
                .collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaderDTOS.stream()
                .filter(header -> header.getCountHeaderId() != null)
                .collect(Collectors.toList());

        // Validate insert and update lists
        insertVerification(insertList);
        updateVerification(updateList);

        // Return the processed inventory count information
        return getTheInfo(invCountHeaderDTOS);
    }


    /**
     * Processes and categorizes inventory count headers based on validation results.
     */
    public InvCountInfoDTO getTheInfo(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        List<InvCountHeaderDTO> success = new ArrayList<>();
        List<InvCountHeaderDTO> failed = new ArrayList<>();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        StringBuilder tErrMsg = new StringBuilder();

        invCountHeaderDTOS.forEach(invCountHeaderDTO -> {
            if (invCountHeaderDTO.getErrorMsg().isEmpty()) {
                success.add(invCountHeaderDTO);
            } else {
                failed.add(invCountHeaderDTO);
                tErrMsg.append(invCountHeaderDTO.getErrorMsg());
            }
        });

        // Populate result DTO
        invCountInfoDTO.setTotalErrorMsg(tErrMsg.toString());
        invCountInfoDTO.setSuccessList(success);
        invCountInfoDTO.setErrorList(failed);
        return invCountInfoDTO;
    }


    /**
     * Service method to validate and remove inventory count headers.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        // Map existing headers by ID for validation
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(invCountHeaderDTOS);
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();

        // Iterate through the list of headers to validate each entry
        invCountHeaderDTOS.forEach(
                invCountHeaderDTO -> {
                    StringBuilder errMsg = new StringBuilder();
                    InvCountHeader countHeader = invCountHeaderMap.get(invCountHeaderDTO.getCountHeaderId());

                    // Ensure the status is "DRAFT" before allowing deletion
                    if (!CountStatus.DRAFT.name().equals(countHeader.getCountStatus())) {
                        errMsg.append("Only allow draft status to be deleted");
                    }

                    // Verify the current user is the creator of the document
                    if (!Objects.equals(userDetails.getUserId(), countHeader.getCreatedBy())) {
                        errMsg.append("Only the document creator is allowed to delete the document");
                    }

                    // Assign error messages, if any, to the DTO
                    invCountHeaderDTO.setErrorMsg(errMsg.toString());
                }
        );

        // Collect validated information into success and error lists
        InvCountInfoDTO theInfo = getTheInfo(invCountHeaderDTOS);

        // Proceed with deletion if there are no errors
        if (CollectionUtils.isEmpty(theInfo.getErrorList())) {
            // Batch delete successfully validated headers
            invCountHeaderRepository.batchDeleteByPrimaryKey(new ArrayList<>(theInfo.getSuccessList()));
        }

        // Return the result, including any errors
        return theInfo;
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        // Fetch inventory count header by its primary ID
        InvCountHeaderDTO invCountHeaderDTO = invCountHeaderRepository.selectByPrimary(countHeaderId);

        // Throw exception if no header is found
        if (Objects.isNull(invCountHeaderDTO)) {
            throw new CommonException("Order Not found");
        }
        // Populate snapshot material list if material IDs are present
        if (StringUtil.isNotBlank(invCountHeaderDTO.getSnapshotMaterialIds())) {
            List<InvMaterial> invMaterials = invMaterialRepository.selectByIds(invCountHeaderDTO.getSnapshotMaterialIds());
            List<SnapShotMaterialDTO> materials = invMaterials.stream().map(invMaterial -> {
                SnapShotMaterialDTO materialDTO = new SnapShotMaterialDTO();
                materialDTO.setId(invMaterial.getMaterialId());
                materialDTO.setCode(invMaterial.getMaterialCode());
                materialDTO.setNameItem(invMaterial.getMaterialName());
                return materialDTO;
            }).collect(Collectors.toList());
            invCountHeaderDTO.setSnapshotMaterialList(materials);
        }

        // Populate snapshot batch list if batch IDs are present
        if (StringUtil.isNotBlank(invCountHeaderDTO.getSnapshotBatchIds())) {
            List<InvBatch> invBatches = invBatchRepository.selectByIds(invCountHeaderDTO.getSnapshotBatchIds());
            List<SnapShotBatchDTO> batches = invBatches.stream().map(invBatch -> {
                SnapShotBatchDTO batchDTO = new SnapShotBatchDTO();
                batchDTO.setId(invBatch.getBatchId());
                batchDTO.setCode(invBatch.getBatchCode());
                return batchDTO;
            }).collect(Collectors.toList());
            invCountHeaderDTO.setSnapshotBatchList(batches);
        }

        // Fetch user information for further processing
        UserVO userSelf = getUserSelf();

        // Prepare a request object for fetching inventory count lines
        InvCountLineDTO reqList = new InvCountLineDTO();
        reqList.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
        reqList.setSupervisorIds(invCountHeaderDTO.getSupervisorIds());

        // Include tenant admin flag if present
        if (Boolean.TRUE.equals(userSelf.getTenantAdminFlag())) {
            reqList.setTenantAdminFlag(userSelf.getTenantAdminFlag());
        }

        // Fetch and map inventory count lines
        List<InvCountLineDTO> invCountLines = invCountLineRepository.selectList(reqList);
        if (!CollectionUtils.isEmpty(invCountLines)) {
            invCountHeaderDTO.setCountOrderLineList(invCountLines);
        }

        // Fetch warehouse information and set WMS warehouse flag
        InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId());
        invCountHeaderDTO.setWMSWarehouse(invWarehouse.getIsWmsWarehouse() == 1);
        return invCountHeaderDTO;
    }

    public void validateLOVs(InvCountHeaderDTO invCountHeaderDTO, StringBuilder errMsg) {
        // Determine the expected date format based on the count type
        String limitFormat = "MONTH".equals(invCountHeaderDTO.getCountType()) ? Constants.MONTH_FORMAT : Constants.YEAR_FORMAT;
        String time = invCountHeaderDTO.getCountTimeStr();
        try {
            // Parse the input time string
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(limitFormat);
            formatter.parse(time);
        } catch (DateTimeParseException e) {
            // Handle invalid date format exception
            errMsg.append("Invalid date format: ").append(time)
                    .append(". Expected format: ").append(limitFormat).append(".");
        }
    }


    public void validateDepartment(InvCountHeaderDTO invCountHeaderDTO,
                                   StringBuilder errMsg,
                                   Map<Long, IamDepartment> iamDepartmentMap) {
        IamDepartment iamDepartment = iamDepartmentMap.get(invCountHeaderDTO.getDepartmentId());

        // Validate warehouse, department, and company existence
        if (Objects.isNull(iamDepartment)) {
            errMsg.append("Department Cannot Be Found.");
        }

    }

    public void validateAvailability(InvCountHeaderDTO invCountHeaderDTO, StringBuilder errMsg) {
        // Parse and extract batch IDs from the input string
        // Build an inventory stock query object
        InvStockDTO invStock = new InvStockDTO();
        invStock.setTenantId(invCountHeaderDTO.getTenantId());
        invStock.setCompanyId(invCountHeaderDTO.getCompanyId());
        invStock.setWarehouseId(invCountHeaderDTO.getWarehouseId());
        invStock.setDepartmentId(invCountHeaderDTO.getDepartmentId());

        if (StringUtil.isNotBlank(invCountHeaderDTO.getSnapshotBatchIds())) {
            // Parse and extract batch IDs from the input string
            String snapshotBatchIds = invCountHeaderDTO.getSnapshotBatchIds();
            List<Long> batchIds = Arrays.stream(snapshotBatchIds.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invStock.setBatchIds(batchIds);
        }

        if (StringUtil.isNotBlank(invCountHeaderDTO.getSnapshotMaterialIds())) {
            // Parse and extract material IDs from the input string
            String snapshotMaterialIds = invCountHeaderDTO.getSnapshotMaterialIds();
            List<Long> materialIds = Arrays.stream(snapshotMaterialIds.split(","))
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            invStock.setMaterialsId(materialIds);
        }

        // Fetch inventory stocks from the repository
        List<InvStock> invStocks = invStockRepository.checkAvailability(invStock);

        // Validate that stock data is available
        if (CollectionUtils.isEmpty(invStocks)) {
            errMsg.append("Unable to query on hand quantity data.");
        }
    }


    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOS) {
        // Check if the input list is null or empty
        if (CollectionUtils.isEmpty(headerDTOS)) {
            return new InvCountInfoDTO();
        }

        // Fetch related data from repositories and services
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();

        Map<Long, IamDepartment> iamDepartmentMap = iamDepartmentService.getFromHeaders(headerDTOS);

        // Iterate through each header DTO for validation
        headerDTOS.forEach(headerDTO -> {
            if (Objects.isNull(headerDTO)) {
                return;
            }

            StringBuilder errMsg = new StringBuilder();
            InvCountHeader countHeader = invCountHeaderMap.get(headerDTO.getCountHeaderId());

            // Validate that the status is DRAFT
            if (!CountStatus.DRAFT.name().equals(countHeader.getCountStatus())) {
                errMsg.append("Only draft status can execute.");
            }

            // Validate that the user is the creator of the document
            if (!Objects.equals(countHeader.getCreatedBy(), userDetails.getUserId())) {
                errMsg.append("Only the document creator can execute.");
            }

            // Perform specific validations
            validateLOVs(headerDTO, errMsg);

            //validate Department
            validateDepartment(headerDTO, errMsg, iamDepartmentMap);
            validateAvailability(headerDTO, errMsg);
            headerDTO.setErrorMsg(errMsg.toString());
        });

        // Return detailed information
        return getTheInfo(headerDTOS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> headerDTOS) {
        // Check if the input list is empty; if so, return it immediately
        if (CollectionUtils.isEmpty(headerDTOS)) {
            return headerDTOS;
        }

        // Set the count status for each header DTO to "INCOUNTING"
        headerDTOS.forEach(invCountHeaderDTO -> invCountHeaderDTO.setCountStatus(CountStatus.INCOUNTING.name()));

        // Batch update the header DTOs in the repository
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(headerDTOS));

        List<InvCountLineDTO> allLineDTO = new ArrayList<>();
        // Process each header DTO to summarize stock and save count order lines
        for (InvCountHeaderDTO invCountHeaderDTO : headerDTOS) {
            // Retrieve summarized stock information for the current header DTO
            List<InvStockDTO> summarizeStock = invStockService.getSummarizeStock(invCountHeaderDTO);

            // Set the summarized stock information to the header DTO
            setCreateLineToHeader(invCountHeaderDTO, summarizeStock);

            // Check if there are count order lines to save
            if (!CollectionUtils.isEmpty(invCountHeaderDTO.getCountOrderLineList())) {
                allLineDTO.addAll(invCountHeaderDTO.getCountOrderLineList());
            }
        }
        // Save the count order lines
        invCountLineService.saveData(allLineDTO);
        // Return the updated list of header DTOs
        return headerDTOS;
    }

    public UserVO getUserSelf() {
        try {
            ResponseEntity<String> stringResponseEntity = iamRemoteService.selectSelf();
            return objectMapper.readValue(stringResponseEntity.getBody(), UserVO.class);
        } catch (Exception e) {
            throw new CommonException("Failed to get current user");
        }
    }

    public Map<Long, List<InvCountExtra>> getExtraMappingSource(List<Long> sourcesId) {
        InvCountExtra invCountExtra = new InvCountExtra();
        invCountExtra.setSourceIds(sourcesId);
        invCountExtra.setEnabledFlag(1);
        List<InvCountExtra> invCountExtras = invCountExtraRepository.selectList(invCountExtra);

        // Create a map to group InvCountExtra records by source ID
        Map<Long, List<InvCountExtra>> extraMap = new HashMap<>();
        for (InvCountExtra countExtra : invCountExtras) {
            extraMap
                    .computeIfAbsent(countExtra.getSourceId(), k -> new ArrayList<>())
                    .add(countExtra);
        }
        return extraMap;
    }

    public Map<String, InvWarehouse> getWarehouseMap(Set<Long> wareHouseIds, Set<Long> tenantIds) {
        // Convert sets to lists for further processing
        List<Long> wareIds = new ArrayList<>(wareHouseIds);
        List<Long> teIds = new ArrayList<>(tenantIds);

        // Create an InvWarehouse object to hold the warehouse and tenant IDs
        InvWarehouse invWarehouse = new InvWarehouse();
        invWarehouse.setIdsWarehouse(wareIds);
        invWarehouse.setTenantIds(teIds);

        // Fetch the list of warehouses from the repository based on the collected IDs
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectList(invWarehouse);
        Map<String, InvWarehouse> warehouseMap = new HashMap<>();

        // Populate a nested map to easily access warehouses by tenant and warehouse IDs
        for (InvWarehouse warehouse : invWarehouses) {
            warehouseMap.put(warehouse.getTenantId() + "_" + warehouse.getWarehouseId(), warehouse);
        }
        return warehouseMap;
    }

    public void syncWmsOperation(InvCountHeaderDTO headerDTO, UserVO userSelf, StringBuilder errMsg, InvCountExtra syncStatusExtra, InvCountExtra syncMsgExtra) {
        // Set the employee number for the sync operation
        headerDTO.setEmployeeNumber(userSelf.getLoginName());
        try {
            // Convert the header DTO to a JSON string for the WMS sync request
            String jsonHeaderDTO = objectMapper.writeValueAsString(headerDTO);

            // Invoke the WMS translation service
            //consider memakai tokenutils.getToken
            ResponsePayloadDTO responsePayloadDTO = utils.invokeTranslation(
                    jsonHeaderDTO,
                    Constants.NAMESPACE,
                    Constants.CODE_SERVER,
                    Constants.INTERFACE_CODE,
                    userSelf.get_token()
            );

            // Parse the response payload
            String payload = responsePayloadDTO.getPayload();
            Map<String, String> map = objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {
            });

            // Check the return status from the WMS response
            if (map.get("returnStatus").equals("S")) {
                // Sync was successful
                syncStatusExtra.setProgramValue("SUCCESS");
                syncMsgExtra.setProgramValue("");

                //put code to the header doc
                headerDTO.setRelatedWmsOrderCode(map.get("code"));
                log.info("Successfully synced WMS for Count Header ID: {}", headerDTO.getCountHeaderId());
            } else {
                // Sync encountered an error
                syncStatusExtra.setProgramValue("ERROR");
                syncMsgExtra.setProgramValue(map.get("returnMsg"));
                errMsg.append(map.get("returnMsg")).append(".");
                log.error("Error syncing WMS for Count Header ID: {}. Message: {}", headerDTO.getCountHeaderId(), map.get("returnMsg"));
            }

        } catch (Exception e) {
            // Handle JSON processing exceptions
            log.error("Failed to parse headerDTO to JSON for Count Header ID: {}", headerDTO.getCountHeaderId(), e);
            errMsg.append("ERROR: There is an error in a program: ").append(e.getMessage()).append(".");
            syncStatusExtra.setProgramValue("ERROR");
            syncMsgExtra.setProgramValue("There is an error in the interface");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> headerDTOS) {
        // Check if the input list is empty; if so, log a warning and return new DTO
        if (headerDTOS.isEmpty()) {
            log.warn("Input list is empty. Returning new InvCountInfoDTO.");
            return new InvCountInfoDTO();
        }

        // Retrieve the current user information
        UserVO userSelf = getUserSelf();

        // Initialize sets to collect unique warehouse IDs, tenant IDs, and source IDs
        Set<Long> wareHouseIds = new HashSet<>();
        Set<Long> tenantIds = new HashSet<>();
        List<Long> sourcesId = new ArrayList<>();

        // Populate the sets with IDs from the header DTOs
        headerDTOS.forEach(headerDTO -> {
            wareHouseIds.add(headerDTO.getWarehouseId());
            tenantIds.add(headerDTO.getTenantId());
            sourcesId.add(headerDTO.getCountHeaderId());
        });

        //consider about concat tenantid + idwarehouse using underline to string
        Map<String, InvWarehouse> warehouseMap = getWarehouseMap(wareHouseIds, tenantIds);

        // Prepare to fetch InvCountExtra records and Map according sources id
        Map<Long, List<InvCountExtra>> extraMap = getExtraMappingSource(sourcesId);

        // Process each header DTO to sync with WMS
        headerDTOS.forEach(headerDTO -> {
            StringBuilder errMsg = new StringBuilder(); // To accumulate error messages

            // Retrieve the corresponding warehouse for the current header DTO
            InvWarehouse invWarehouse1 = warehouseMap.get(headerDTO.getTenantId() + "_" + headerDTO.getWarehouseId());
            if (invWarehouse1 == null) {
                errMsg.append("Warehouse is not found. according tenant.");
            }

            // Retrieve the InvCountExtra records for the current header DTO
            List<InvCountExtra> invCountExtras1 = extraMap.get(headerDTO.getCountHeaderId());
            if (CollectionUtils.isEmpty(invCountExtras1)) {
                // If no records found, create new InvCountExtra objects for sync status and message
                invCountExtras1 = new ArrayList<>();
                InvCountExtra syncStatusExtra = new InvCountExtra();
                invCountExtras1.add(syncStatusExtra);
                InvCountExtra syncMsgExtra = new InvCountExtra();
                invCountExtras1.add(syncMsgExtra);
            }

            // Set up the sync status and message objects
            InvCountExtra syncStatusExtra = invCountExtras1.get(0);
            syncStatusExtra.setEnabledFlag(1);
            syncStatusExtra.setTenantId(headerDTO.getTenantId());
            syncStatusExtra.setSourceId(headerDTO.getCountHeaderId());
            syncStatusExtra.setProgramKey("wms_sync_status");

            InvCountExtra syncMsgExtra = invCountExtras1.get(1);
            syncMsgExtra.setEnabledFlag(1);
            syncMsgExtra.setTenantId(headerDTO.getTenantId());
            syncMsgExtra.setSourceId(headerDTO.getCountHeaderId());
            syncMsgExtra.setProgramKey("wms_sync_error_message");

            // Check if the warehouse is a WMS warehouse
            if (invWarehouse1 != null) {
                Integer isWmsWarehouse = invWarehouse1.getIsWmsWarehouse();
                if (isWmsWarehouse == 1) {
                    // Calling External Interface in helper method
                    syncWmsOperation(headerDTO, userSelf, errMsg, syncStatusExtra, syncMsgExtra);
                } else {
                    // If the warehouse is not a WMS warehouse, set the appropriate status
                    syncStatusExtra.setProgramValue("SKIP");
                    syncMsgExtra.setProgramValue("Warehouse is not WMS");
                    log.info("Skipping WMS sync for Count Header ID: {}. Reason: Warehouse is not WMS.", headerDTO.getCountHeaderId());
                }
                // Save the InvCountExtra records for the current header DTO
                invCountExtraService.saveData(invCountExtras1);
            }

            // Set the accumulated error message for the current header DTO
            headerDTO.setErrorMsg(errMsg.toString());
        });

        // Retrieve the overall information after processing all header DTOs
        InvCountInfoDTO theInfo = getTheInfo(headerDTOS);
        if (CollectionUtils.isEmpty(theInfo.getErrorList())) {
            log.info("WMS sync completed successfully with no errors.");
            return theInfo; // Return the info if there are no errors
        }

        // If there are errors, log the total error message and throw an exception
        log.error("WMS sync completed with errors: {}", theInfo.getTotalErrorMsg());
        throw new CommonException(theInfo.getTotalErrorMsg());
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO headerDTO) {
        // Create a request object for the warehouse based on the header DTO
        InvCountHeaderDTO req = new InvCountHeaderDTO();
        req.setCountNumber(headerDTO.getCountNumber());
        req.setTenantId(headerDTO.getTenantId());
        InvCountHeader invCountHeader = invCountHeaderRepository.selectOne(req);

        InvWarehouse invWarehouseReq = new InvWarehouse();
        invWarehouseReq.setWarehouseId(headerDTO.getWarehouseId());
        invWarehouseReq.setTenantId(headerDTO.getTenantId());

        // Fetch the warehouse information from the repository
        InvWarehouse invWarehouse = invWarehouseRepository.selectOne(invWarehouseReq);
        int isWarehouseWms = 1;
        if(Objects.isNull(invWarehouse)){
            isWarehouseWms = 0;
        }else {
            isWarehouseWms = invWarehouse.getIsWmsWarehouse();
        }

        // Initialize a StringBuilder to accumulate error messages
        StringBuilder errMsg = new StringBuilder();
        headerDTO.setStatus("S"); // Set initial status to "S" (Success)

        if (isWarehouseWms != 1) {
            errMsg.append("The current warehouse is not a WMS warehouse, operations are not allowed.");
            headerDTO.setStatus("E"); // Set status to "E" (Error)
        }

        // Prepare to fetch count line data associated with the header
        InvCountLineDTO invCountLine = new InvCountLineDTO();
        invCountLine.setCountHeaderId(invCountHeader.getCountHeaderId());
        List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectList(invCountLine);



        // Create a map to associate count line IDs with their DTOs for quick access
        Map<Long, InvCountLineDTO> mapLines = new HashMap<>();
        for (InvCountLineDTO invCountLineDTO : invCountLineDTOS) {
            mapLines.put(invCountLineDTO.getCountLineId(), invCountLineDTO);
        }

        // Check if the size of the count order line list matches the size of the fetched count lines
        int size = invCountLineDTOS.size();
        if(CollectionUtils.isEmpty(headerDTO.getCountOrderLineList())){
            errMsg.append("The counting order line data is null, please check the data.");
            headerDTO.setStatus("E");
        }else {
            if (size != headerDTO.getCountOrderLineList().size()) {
                errMsg.append("The counting order line data is inconsistent with the INV system, please check the data.");
                headerDTO.setStatus("E"); // Set status to "E" (Error)
            }

            Set<Long> request = headerDTO.getCountOrderLineList().stream().map(InvCountLine::getCountLineId).collect(Collectors.toSet());
            Set<Long> result = invCountLineDTOS.stream().map(InvCountLine::getCountLineId).collect(Collectors.toSet());
            boolean equal = request.containsAll(result);
            if(!equal){
                errMsg.append("The counting order line data is inconsistent, please check the data.");
                headerDTO.setStatus("E");
            }
        }

        // Set the accumulated error message in the header DTO
        headerDTO.setErrorMsg(errMsg.toString());

        // If there are no errors, proceed to update the line data
        if (headerDTO.getStatus().equals("S")) {
            // Update the line data (including unitQty, unitDiffQty, remark)
            for (InvCountLineDTO invCountLineDTO : headerDTO.getCountOrderLineList()) {
                InvCountLineDTO invCountLineDTO1 = mapLines.get(invCountLineDTO.getCountLineId());
                BigDecimal snapshotUnitQty = invCountLineDTO1.getSnapshotUnitQty();
                if (invCountLineDTO.getUnitQty() != null) {
                    BigDecimal unitQty = invCountLineDTO.getUnitQty();
                    BigDecimal result = unitQty.subtract(snapshotUnitQty);
                    invCountLineDTO1.setUnitQty(unitQty);
                    invCountLineDTO1.setUnitDiffQty(result); // Calculate the difference
                }
                invCountLineDTO1.setRemark(invCountLineDTO.getRemark());
            }
            // Save the updated count order line data
            invCountLineRepository.batchUpdateOptional(new ArrayList<>(mapLines.values()),
                    InvCountLine.FIELD_UNIT_QTY,
                    InvCountLine.FIELD_UNIT_DIFF_QTY,
                    InvCountLine.FIELD_REMARK);
        }

        // Return the updated header DTO
        return headerDTO;
    }


    private Map<Long, Map<Long, InvCountLineDTO>> mappingLine(List<InvCountHeaderDTO> headerDTOS) {
        List<Long> headerIds = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            headerIds.add(headerDTO.getCountHeaderId());
        }
        InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
        invCountLineDTO.setIdsHeader(headerIds);
        List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectList(invCountLineDTO);
        Map<Long, Map<Long, InvCountLineDTO>> linesMap = new HashMap<>();
        for (InvCountLineDTO lineDTO : invCountLineDTOS) {
            linesMap
                    .computeIfAbsent(lineDTO.getCountHeaderId(), k -> new HashMap<>())
                    .put(lineDTO.getCountLineId(), lineDTO);
        }
        return linesMap;
    }

    /**
     * Submits the inventory count checks for the provided list of headers.
     *
     * @param headerDTOS List of inventory count header DTOs to be validated and processed.
     * @return An InvCountInfoDTO containing information about the processed headers.
     */
    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> headerDTOS) {
        // Get details of the currently logged-in user.
        CustomUserDetails currentUserDetails = DetailsHelper.getUserDetails();

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            boolean isReasonRequired = false; // Flag to check if a reason is required.
            StringBuilder errorMessages = new StringBuilder(); // Collect error messages for this header.

            // Validate header status.
            String countStatus = headerDTO.getCountStatus();
            if (!isValidStatus(countStatus)) {
                errorMessages.append("The operation is allowed only when the status is in counting, processing, rejected, or withdrawn.");
            }

            // Validate that the current user is a supervisor of this document.
            if (!isCurrentUserSupervisor(headerDTO.getSupervisorIds(), currentUserDetails.getUserId())) {
                errorMessages.append("Only the current logged-in user who is a supervisor can submit the document.");
            }

            // Validate lines within the header.
            for (InvCountLineDTO lineDTO : headerDTO.getCountOrderLineList()) {
                // Ensure the count quantity is not null.
                if (lineDTO.getUnitQty() == null) {
                    errorMessages.append("There are data rows with empty count quantity. Please check the data.");
                }

                // Check if there is a difference in quantities requiring a reason.
                if (!lineDTO.getUnitDiffQty().equals(BigDecimal.ZERO)) {
                    isReasonRequired = true;
                }
            }

            // Validate that a reason is provided if differences exist.
            if (isReasonRequired && (StringUtil.isBlank(headerDTO.getReason()))) {
                errorMessages.append("When there is a difference in counting, the reason field must be entered.");
            }

            // Set accumulated error messages for this header.
            headerDTO.setErrorMsg(errorMessages.toString());
        }

        // Return the processed information.
        return getTheInfo(headerDTOS);
    }

    /**
     * Checks if the given status is valid for the operation.
     *
     * @param status The status to check.
     * @return true if the status is valid, false otherwise.
     */
    private boolean isValidStatus(String status) {
        return "PROCESSING".equals(status) || "WITHDRAWN".equals(status)
                || "INCOUNTING".equals(status) || "REJECTED".equals(status);
    }

    /**
     * Checks if the current user is a supervisor of the given document.
     *
     * @param supervisorIds Comma-separated supervisor IDs.
     * @param currentUserId The ID of the current logged-in user.
     * @return true if the current user is a supervisor, false otherwise.
     */
    private boolean isCurrentUserSupervisor(String supervisorIds, Long currentUserId) {
        String[] supervisorIdArray = supervisorIds.split(",");
        for (String supervisorId : supervisorIdArray) {
            if (Long.valueOf(supervisorId).equals(currentUserId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Submits a list of inventory count headers and triggers the workflow if required.
     *
     * @param headerDTOS List of inventory count header DTOs to process.
     * @return The updated list of inventory count header DTOs.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> headerDTOS) {
        // Fetch profile configuration for workflow handling.
        String profileValue = profileClient.getProfileValueByOptions(0L, null, null, Constants.PROFILE_CONTENT_CODE);
        int isWorkflowEnabled = Integer.parseInt(profileValue);

        // Fetch department mapping for the provided headers.
        Map<Long, IamDepartment> departmentMap = iamDepartmentService.getFromHeaders(headerDTOS);

        if (isWorkflowEnabled == 1) {
            // Workflow is enabled. Trigger workflows for each header.
            processWorkflow(headerDTOS, departmentMap);

        } else {
            // Workflow is disabled. Directly confirm all headers.
            confirmHeaders(headerDTOS);
        }

        return headerDTOS;
    }

    /**
     * Processes the workflow for the given headers.
     *
     * @param headerDTOS    List of inventory count header DTOs.
     * @param departmentMap Mapping of department IDs to department details.
     */
    private void processWorkflow(List<InvCountHeaderDTO> headerDTOS, Map<Long, IamDepartment> departmentMap) {
        headerDTOS.forEach(headerDTO -> {
            WorkFlowEventDTO workFlowEventDTO = new WorkFlowEventDTO();
            workFlowEventDTO.setBusinessKey(headerDTO.getCountNumber());

            IamDepartment department = departmentMap.get(headerDTO.getDepartmentId());
            workflowService.startWorkFlow(
                    headerDTO.getTenantId(),
                    workFlowEventDTO,
                    department.getDepartmentCode()
            );
        });
    }

    /**
     * Updates the header DTOs with data fetched from the database after workflow initiation.
     *
     * @param headerDTOS    List of inventory count header DTOs to update.
     * @param headerDataMap Mapping of header IDs to database entity data.
     */
    private void updateHeaderDTOsFromDatabase(List<InvCountHeaderDTO> headerDTOS, Map<Long, InvCountHeader> headerDataMap) {
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            InvCountHeader dbHeader = headerDataMap.get(headerDTO.getCountHeaderId());

            headerDTO.setStatus(dbHeader.getCountStatus());
            if (dbHeader.getApprovedTime() != null) {
                headerDTO.setApprovedTime(dbHeader.getApprovedTime());
            }
            headerDTO.setWorkflowId(dbHeader.getWorkflowId());
            headerDTO.setSupervisorIds(dbHeader.getSupervisorIds());
        }
    }

    /**
     * Confirms all headers by setting their status to "CONFIRMED" and updating the database.
     *
     * @param headerDTOS List of inventory count header DTOs to confirm.
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void confirmHeaders(List<InvCountHeaderDTO> headerDTOS) {
        headerDTOS.forEach(headerDTO -> headerDTO.setCountStatus("CONFIRMED"));
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(headerDTOS));
    }


    /**
     * Saves inventory count orders after performing manual checks and validations.
     * <p></p>
     * This method is transactional, ensuring that all operations are rolled back in case of an exception.
     *
     * @param headerDTOS List of inventory count header DTOs to save.
     * @return InvCountInfoDTO containing the results of the operation.
     * @throws CommonException if there are validation errors during the process.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO orderSave(List<InvCountHeaderDTO> headerDTOS) {
        // Perform manual validation checks on the input headers.
        InvCountInfoDTO validationInfo = manualSaveCheck(headerDTOS);

        // If there are no validation errors, proceed to save the headers.
        if (CollectionUtils.isEmpty(validationInfo.getErrorList())) {
            List<InvCountHeaderDTO> savedHeaders = self().manualSave(headerDTOS);

            // Populate the success list in the response DTO.
            validationInfo.setSuccessList(savedHeaders);
            return validationInfo;
        }

        // If validation errors are present, throw an exception with the aggregated error messages.
        throw new CommonException(validationInfo.getTotalErrorMsg());
    }

    /**
     * Executes the order processing for inventory count headers.
     * This method saves, validates, executes, and synchronizes the orders with WMS if all checks pass.
     * <p></p>
     * The operation is transactional to ensure atomicity, rolling back on any exceptions.
     *
     * @param headerDTOS List of inventory count header DTOs to process.
     * @return InvCountInfoDTO containing success or error details.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> headerDTOS) {
        // Save the inventory count headers and validate them.
        InvCountInfoDTO saveResult = self().orderSave(headerDTOS);

        // Perform additional checks on the successfully saved headers to ensure readiness for execution.
        InvCountInfoDTO executionCheckResult = executeCheck(saveResult.getSuccessList());

        // Return with errors if the execution readiness checks fail.
        // and about this one is not always with throw, because it up to customer
        // it can be that the customer want roll back just in execute and not up to the save method
        if (CollectionUtils.isNotEmpty(executionCheckResult.getErrorList())) {
//            saveResult.setErrorList(executionCheckResult.getErrorList());
//            saveResult.setSuccessList(executionCheckResult.getSuccessList());
//            saveResult.setTotalErrorMsg(executionCheckResult.getTotalErrorMsg());
//            return saveResult;
            throw new CommonException(executionCheckResult.getTotalErrorMsg());

        }

        // Execute the validated headers and update the success list.
        List<InvCountHeaderDTO> executedHeaders = self().execute(executionCheckResult.getSuccessList());
        saveResult.setSuccessList(executedHeaders);

        // Synchronize the executed headers with WMS and return the final result.
        return self().countSyncWms(executedHeaders);
    }


    /**
     * Handles the submission of inventory count orders.
     * This method saves, validates, and submits the orders, updating the response with both success and error details.
     * <p></p>
     * The operation is transactional to ensure atomicity, rolling back on any exceptions.
     *
     * @param headerDTOS List of inventory count header DTOs to process.
     * @return InvCountInfoDTO containing success and/or error details.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO orderSubmit(List<InvCountHeaderDTO> headerDTOS) {
        // Save the inventory count headers and validate them.
        InvCountInfoDTO invCountInfoDTO = self().orderSave(headerDTOS);

        // Perform submission checks on the successfully saved headers.
        InvCountInfoDTO submitCheckResult = submitCheck(invCountInfoDTO.getSuccessList());

        // If submission checks pass, submit the headers.
        if (CollectionUtils.isEmpty(submitCheckResult.getErrorList())) {
            List<InvCountHeaderDTO> submittedHeaders = self().submit(submitCheckResult.getSuccessList());
            invCountInfoDTO.setSuccessList(submittedHeaders);
            return invCountInfoDTO;
        }
//        else {
//            // If submission checks fail, update the response with errors.
//            invCountInfoDTO.setErrorList(submitCheckResult.getErrorList());
//
//            // Include successfully processed headers, if any, in the response.
//            if (!CollectionUtils.isEmpty(submitCheckResult.getSuccessList())) {
//                invCountInfoDTO.setSuccessList(submitCheckResult.getSuccessList());
//            }
//        }
        throw new CommonException(submitCheckResult.getTotalErrorMsg());

    }


    private List<SnapShotMaterialDTO> getMaterials(String materialIds, Map<Long, InvMaterial> invMaterialMap) {
        // Convert the materialIds string into a Set<Long> to ensure uniqueness and avoid duplicates
        Set<Long> idsMats = Arrays.stream(materialIds.split(","))
                .map(Long::valueOf) // Convert each material ID string to Long
                .collect(Collectors.toSet()); // Collect into a Set to ensure uniqueness

        // Prepare the result list to hold SnapShotMaterialDTOs
        List<SnapShotMaterialDTO> result = new ArrayList<>();

        // Iterate over the unique material IDs
        for (Long id : idsMats) {
            // Retrieve the InvMaterial from the map based on the current material ID
            InvMaterial invMaterial = invMaterialMap.get(id);

            // If the InvMaterial is found, create a SnapShotMaterialDTO and populate its fields
            if (invMaterial != null) {
                SnapShotMaterialDTO snapShotMaterialDTO = new SnapShotMaterialDTO();
                snapShotMaterialDTO.setId(id); // Set the material ID
                snapShotMaterialDTO.setCode(invMaterial.getMaterialCode()); // Set the material code from InvMaterial

                // Add the populated SnapShotMaterialDTO to the result list
                result.add(snapShotMaterialDTO);
            }
        }

        // Return the list of SnapShotMaterialDTOs
        return result;
    }


    private List<SnapShotBatchDTO> getBatches(String batchIds, Map<Long, InvBatch> invBatchMap) {
        // Split the batchIds string into individual batch ID strings and convert them into a Set<Long> to ensure uniqueness
        Set<Long> idsBatch = Arrays.stream(batchIds.split(","))
                .map(Long::valueOf) // Convert each batch ID string to Long
                .collect(Collectors.toSet());

        // Prepare the result list to hold SnapShotBatchDTOs
        List<SnapShotBatchDTO> result = new ArrayList<>();

        // Iterate over the unique batch IDs
        for (Long id : idsBatch) {
            // Retrieve the InvBatch from the map based on the current batch ID
            InvBatch invBatch = invBatchMap.get(id);

            // If the InvBatch is found, create a SnapShotBatchDTO and populate its fields
            if (invBatch != null) {
                SnapShotBatchDTO snapShotBatchDTO = new SnapShotBatchDTO();
                snapShotBatchDTO.setId(id); // Set the batch ID
                snapShotBatchDTO.setCode(invBatch.getBatchCode()); // Set the batch code from InvBatch

                // Add the populated SnapShotBatchDTO to the result list
                result.add(snapShotBatchDTO);
            }
        }

        // Return the list of SnapShotBatchDTOs
        return result;
    }
    //trying using @Async for threading
//    @Async
//    public CompletableFuture<IamCompany> getIamCompanyAsync(String companyCode) {
//        return CompletableFuture.completedFuture(iamCompanyRepository.selectOne(new IamCompany().setCompanyCode(companyCode)));
//    }
//
//    @Async
//    public CompletableFuture<IamDepartment> getIamDepartmentAsync(String departmentCode) {
//        return CompletableFuture.completedFuture(iamDepartmentRepository.selectOne(new IamDepartment().setDepartmentCode(departmentCode)));
//    }
//
//    @Async
//    public CompletableFuture<InvWarehouse> getInvWarehouseAsync(String warehouseCode) {
//        return CompletableFuture.completedFuture(invWarehouseRepository.selectOne(new InvWarehouse().setWarehouseCode(warehouseCode)));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, IamDepartment>> getIamDepartmentMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(iamDepartmentService.getFromHeaders(invCountHeaderDTOS));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, InvWarehouse>> getWarehouseMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(invWarehouseService.getFromOrders(invCountHeaderDTOS));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, IamCompany>> getCompanyMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(iamCompanyService.byIdsFromHeader(invCountHeaderDTOS));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, InvBatch>> getInvBatchMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(invBatchService.getFromHeaders(invCountHeaderDTOS));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, InvMaterial>> getInvMaterialMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(invMaterialService.getFromHeaders(invCountHeaderDTOS));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, InvCountHeader>> getInvCountHeaderAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(selectByIds(invCountHeaderDTOS));
//    }
//
//    @Async
//    public CompletableFuture<Map<Long, Map<Long, InvCountLineDTO>>> getLineMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
//        return CompletableFuture.completedFuture(mappingLine(invCountHeaderDTOS));
//    }


    private Map<Long, Map<Long, InvCountLineDTO>> mappingLineReport(List<InvCountHeaderDTO> headerDTOS) {
        List<Long> headerIds = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            headerIds.add(headerDTO.getCountHeaderId());
        }
        InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
        invCountLineDTO.setIdsHeader(headerIds);
        List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectReport(invCountLineDTO);
        Map<Long, Map<Long, InvCountLineDTO>> linesMap = new HashMap<>();
        for (InvCountLineDTO lineDTO : invCountLineDTOS) {
            linesMap
                    .computeIfAbsent(lineDTO.getCountHeaderId(), k -> new HashMap<>())
                    .put(lineDTO.getCountLineId(), lineDTO);
        }
        return linesMap;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> report(InvCountHeaderDTO invCountHeaderDTO) {
        // Fetch invCountHeaderDTOs from the repository
        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountHeaderRepository.selectReport(invCountHeaderDTO);
        if (CollectionUtils.isEmpty(invCountHeaderDTOS)) {
            return invCountHeaderDTOS; // Return early if no data is found
        }

        // Retrieve the results from the futures
        Map<Long, InvBatch> invBatchMap = invBatchService.getFromHeaders(invCountHeaderDTOS);
        Map<Long, InvMaterial> invMaterialMap = invMaterialService.getFromHeaders(invCountHeaderDTOS);
        Map<Long, Map<Long, InvCountLineDTO>> lineMap = mappingLineReport(invCountHeaderDTOS);

        // Prepare a list of statuses for processing
        List<String> statuses = new ArrayList<>();
        statuses.add(CountStatus.PROCESSING.name());
        statuses.add(CountStatus.REJECTED.name());
        statuses.add(CountStatus.APPROVED.name());
        statuses.add(CountStatus.WITHDRAWN.name());

        // Process each invCountHeaderDTO in parallel (for better performance)
        invCountHeaderDTOS.parallelStream().forEach(invCountHeaderDTO1 -> {
            // Check if snapshotBatchIds exist, and process them
            if (StringUtil.isNotBlank(invCountHeaderDTO1.getSnapshotBatchIds())) {
                List<SnapShotBatchDTO> batches = getBatches(invCountHeaderDTO1.getSnapshotBatchIds(), invBatchMap);
                invCountHeaderDTO1.setSnapshotBatchList(batches);

                // Join batch codes into a string
                String bCodes = batches.stream()
                        .map(SnapShotBatchDTO::getCode)
                        .collect(Collectors.joining(", "));
                invCountHeaderDTO1.setBatchCodes(bCodes);
            }

            // Check if snapshotMaterialIds exist, and process them
            if (StringUtil.isNotBlank(invCountHeaderDTO1.getSnapshotMaterialIds())) {
                List<SnapShotMaterialDTO> materials = getMaterials(invCountHeaderDTO1.getSnapshotMaterialIds(), invMaterialMap);
                invCountHeaderDTO1.setSnapshotMaterialList(materials);

                // Join material codes into a string
                String mCodes = materials.stream()
                        .map(SnapShotMaterialDTO::getCode)
                        .collect(Collectors.joining(", "));
                invCountHeaderDTO1.setMaterialCodes(mCodes);
            }

            String superVisorNames = invCountHeaderDTO1.getSupervisorList().stream()
                    .map(UserDTO::getRealName)
                    .collect(Collectors.joining(", "));
            String counterNames = invCountHeaderDTO1.getCounterList().stream()
                    .map(UserDTO::getRealName)
                    .collect(Collectors.joining(", "));

            invCountHeaderDTO1.setSuperVisorNames(superVisorNames);
            invCountHeaderDTO1.setCounterNames(counterNames);

            // If the count status is in the list of statuses, fetch approval history
            if (statuses.contains(invCountHeaderDTO1.getCountStatus())) {
                List<RunTaskHistory> history = workflowService.getHistory(invCountHeaderDTO1.getTenantId(), Constants.FLOW_KEY_CODE, invCountHeaderDTO1.getCountNumber());
                invCountHeaderDTO1.setHistoryApproval(history);
            }

            // Set count line details if available
            Map<Long, InvCountLineDTO> lineDTOMap = lineMap.get(invCountHeaderDTO1.getCountHeaderId());
            if(Objects.nonNull(lineDTOMap)){
                if (CollectionUtils.isNotEmpty(lineDTOMap.values())) {
                    List<InvCountLineDTO> collect = new ArrayList<>(lineMap.get(invCountHeaderDTO1.getCountHeaderId()).values());
                    invCountHeaderDTO1.setCountOrderLineList(collect);

                    // Process each line in parallel
                    List<InvCountLineDTO> countOrderLineList = invCountHeaderDTO1.getCountOrderLineList();
                    countOrderLineList.parallelStream().forEach(invCountLineDTO -> {
                        String counterLineNames = invCountLineDTO.getCounterList().stream()
                                .map(UserDTO::getRealName)
                                .collect(Collectors.joining(", "));
                        invCountLineDTO.setCounterLineNames(counterLineNames);
                    });
                }
            }
        });

        // Return the populated list of InvCountHeaderDTOs
        return invCountHeaderDTOS;
    }

    /**
     * Updates the inventory count header based on workflow callback events.
     * <p></p>
     * This method updates the count status, workflow ID, approval time, and supervisor IDs
     * based on the workflow event data provided in the callback.
     *
     * @param workFlowEventDTO The workflow event data used to update the inventory count header.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountHeader callbackHeader(WorkFlowEventDTO workFlowEventDTO) {
        // Create a DTO to search for the corresponding inventory count header.
        InvCountHeaderDTO searchCriteria = new InvCountHeaderDTO();
        searchCriteria.setCountNumber(workFlowEventDTO.getBusinessKey());
        InvCountHeader invCountHeader = invCountHeaderRepository.selectOne(searchCriteria);

        // Update the count status and workflow ID from the workflow event.
        invCountHeader.setCountStatus(workFlowEventDTO.getDocStatus());
        invCountHeader.setWorkflowId(workFlowEventDTO.getWorkflowId());

        // Set the approval time if the document status is "APPROVED".
        if (CountStatus.APPROVED.name().equals(workFlowEventDTO.getDocStatus())) {
            invCountHeader.setApprovedTime(workFlowEventDTO.getApprovedTime());
        }

        // Assign the current user's ID as the supervisor ID if the document status is "PROCESSING".
        if (CountStatus.PROCESSING.name().equals(workFlowEventDTO.getDocStatus())) {
            Long currentUserId = getUserSelf().getId();
            invCountHeader.setSupervisorIds(String.valueOf(currentUserId));
        }

        // Update the inventory count header in the repository.
        invCountHeaderRepository.updateByPrimaryKeySelective(invCountHeader);
        return invCountHeader;
    }


    /**
     * Populates and associates count line data with the given inventory count header.
     * <p></p>
     * This method iterates through the provided stock data to create inventory count lines
     * and assigns them to the inventory count header.
     *
     * @param invCountHeaderDTO The inventory count header DTO to associate the count lines with.
     * @param stocks            A list of inventory stock DTOs used to populate the count lines.
     */
    public void setCreateLineToHeader(InvCountHeaderDTO invCountHeaderDTO, List<InvStockDTO> stocks) {
        // Initialize a list to hold the created count lines.
        if (CollectionUtils.isEmpty(stocks)) {
            return;
        }

        List<InvCountLineDTO> saveLine = new ArrayList<>();
        AtomicInteger lineNumber = new AtomicInteger(1);

        // Iterate through the stock data to create and populate count lines.
        for (InvStockDTO stock : stocks) {
            InvCountLineDTO invCountLineDTO = new InvCountLineDTO();

            // Populate common fields from the header and stock data.
            invCountLineDTO.setTenantId(invCountHeaderDTO.getTenantId());
            invCountLineDTO.setLineNumber(lineNumber.getAndIncrement());
            invCountLineDTO.setWarehouseId(invCountHeaderDTO.getWarehouseId());
            invCountLineDTO.setMaterialId(stock.getMaterialId());
            invCountLineDTO.setBatchId(stock.getBatchId());


            // Populate additional fields from the header and stock data.
            invCountLineDTO.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
            invCountLineDTO.setUnitCode(stock.getUnitCode());
            invCountLineDTO.setMaterialCode(stock.getMaterialCode());

            // Set snapshot quantity and counter information.
            invCountLineDTO.setSnapshotUnitQty(stock.getSummary());
            invCountLineDTO.setCounterIds(invCountHeaderDTO.getCounterIds());

            // Add the created line to the list and increment the line number.
            saveLine.add(invCountLineDTO);
        }

        // Assign the created count lines to the header DTO.
        invCountHeaderDTO.setCountOrderLineList(saveLine);
    }


    /**
     * Retrieves a map of inventory count headers by their IDs.
     *
     * @param headerDTOS the list of inventory count header DTOs containing the IDs to query
     * @return a map where the key is the header ID and the value is the corresponding InvCountHeader object
     */
    public Map<Long, InvCountHeader> selectByIds(List<InvCountHeaderDTO> headerDTOS) {
        // Return an empty map if the input list is null or empty
        if (CollectionUtils.isEmpty(headerDTOS)) {
            return Collections.emptyMap();
        }
        List<InvCountHeaderDTO> nonNullHeaders = headerDTOS.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Validate and ensure all DTOs have a non-null CountHeaderId
        nonNullHeaders.forEach(headerDTO -> {
            if (headerDTO.getCountHeaderId() == null) {
                headerDTO.setErrorMsg("Header ID cannot be null");
            }
        });

        // Extract unique, non-null CountHeaderIds as a Set
        Set<Long> countIdSet = nonNullHeaders.stream()
                .map(InvCountHeaderDTO::getCountHeaderId)
                .filter(Objects::nonNull) // Filter out null IDs to avoid issues in queries
                .collect(Collectors.toSet());

        // If no valid IDs are found, return an empty map
        if (countIdSet.isEmpty()) {
            return Collections.emptyMap();
        }

        // Convert the Set of IDs to a comma-separated string for query purposes
        String countIds = countIdSet.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        // Query the repository to fetch the headers by their IDs
        List<InvCountHeader> invCountHeaders = invCountHeaderRepository.selectByIds(countIds);

        // Map the result by CountHeaderId for easy lookup
        return invCountHeaders.stream()
                .collect(Collectors.toMap(InvCountHeader::getCountHeaderId, header -> header));
    }


    /**
     * Validates the update list of inventory count headers.
     */
    //TODO: there is some constant literal variable that need to be changed and use
    public void updateVerification(List<InvCountHeaderDTO> headerDTOS) {
        if (CollectionUtils.isEmpty(headerDTOS)) return;

        // Map existing headers by ID for reference
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);

        if (invCountHeaderMap.isEmpty()) {
            return;
        }

        Long userId = DetailsHelper.getUserDetails().getUserId();
        // Fetch warehouse information and map lines for validation
        Map<Long, InvWarehouse> warehouseMap = invWarehouseService.getFromOrders(headerDTOS);
        Map<Long, Map<Long, InvCountLineDTO>> lineMap = mappingLine(headerDTOS);
        Map<Long, IamCompany> iamCompanyMap = iamCompanyService.byIdsFromHeader(headerDTOS);


        // Define allowed statuses for updates
        List<String> statuses = Arrays.asList(CountStatus.DRAFT.name(), CountStatus.INCOUNTING.name(), CountStatus.REJECTED.name(), CountStatus.WITHDRAWN.name());

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();
            InvCountHeader countHeader = invCountHeaderMap.get(headerDTO.getCountHeaderId());

            // Check status validity
            String currentStatus = countHeader.getCountStatus();
            if (!statuses.contains(currentStatus)) {
                errMsg.append("Only draft, in counting, rejected, and withdrawn status can be modified.");
            }

            // Prevent unauthorized status changes
            if (!headerDTO.getCountStatus().equals(countHeader.getCountStatus())) {
                errMsg.append("The status is different from the database.");
            }

            if (iamCompanyMap.get(headerDTO.getCompanyId()) == null) {
                errMsg.append("Company cannot be found.");
            }
            if (warehouseMap.get(headerDTO.getWarehouseId()) == null) {
                errMsg.append("Warehouse cannot be found.");
            }
            // Ensure draft documents are only modifiable by their creator
            if (CountStatus.DRAFT.name().equals(currentStatus) && !Objects.equals(countHeader.getCreatedBy(), userId)) {
                errMsg.append("Draft documents can only be modified by their creator.");
            }

            // Validate permissions for other statuses
            if (!CountStatus.DRAFT.name().equals(currentStatus) && statuses.contains(currentStatus)) {
                List<String> supervisors = Arrays.asList(countHeader.getSupervisorIds().split(","));
                List<String> counters = Arrays.asList(countHeader.getCounterIds().split(","));
                boolean isWMSWarehouse = warehouseMap.containsKey(headerDTO.getWarehouseId()) && warehouseMap.get(headerDTO.getWarehouseId()).getIsWmsWarehouse() == 1;
                if (isWMSWarehouse && !supervisors.contains(userId.toString())) {
                    errMsg.append("Only supervisors can operate on WMS warehouses.");
                }
                if (!counters.contains(userId.toString()) && !supervisors.contains(userId.toString()) && !Objects.equals(countHeader.getCreatedBy(), userId)) {
                    errMsg.append("Only authorized personnel can modify documents in non-draft statuses.");
                }
            }

            Set<Long> idsCounter = new HashSet<>();
            Arrays.stream(headerDTO.getCounterIds().split(",")).forEach(
                    s -> idsCounter.add(Long.parseLong(s))
            );

            // Validate associated count lines
            List<InvCountLineDTO> invCountLineDTOList = headerDTO.getCountOrderLineList();
            if (!CollectionUtils.isEmpty(invCountLineDTOList)) {
                for (InvCountLineDTO invCountLineDTO : invCountLineDTOList) {
                    // Calculate unit differences for "INCOUNTING" status
                    if (CountStatus.INCOUNTING.name().equals(currentStatus)
                            && invCountLineDTO.getUnitQty() != null && idsCounter.contains(userId)) {
                        BigDecimal difference = invCountLineDTO.getUnitQty().subtract(lineMap.get(headerDTO.getCountHeaderId()).get(invCountLineDTO.getCountLineId()).getSnapshotUnitQty());
                        invCountLineDTO.setUnitDiffQty(difference);
                        invCountLineDTO.setCounterIds(userId.toString());
                    }
                }
            }

            // Assign accumulated error messages
            headerDTO.setErrorMsg(errMsg.toString());
        }
    }


    /**
     * Validates the insert list of inventory count headers.
     */
    //TODO need to be full understanding using @async
    public void insertVerification(List<InvCountHeaderDTO> headerDTOS) {
        if (CollectionUtils.isEmpty(headerDTOS)) return;
        // Retrieve the results from the futures
        Map<Long, IamCompany> iamCompanyMap = iamCompanyService.byIdsFromHeader(headerDTOS);
        Map<Long, InvWarehouse> warehouseMap = invWarehouseService.getFromOrders(headerDTOS);

        // Validate and append error messages for missing data
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();

            if (iamCompanyMap.get(headerDTO.getCompanyId()) == null) {
                errMsg.append("Company cannot be found.");
            }
            if (warehouseMap.get(headerDTO.getWarehouseId()) == null) {
                errMsg.append("Warehouse cannot be found.");
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }
    }
}


