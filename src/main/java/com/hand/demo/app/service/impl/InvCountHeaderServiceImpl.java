package com.hand.demo.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.dto.*;
import com.hand.demo.app.service.*;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.Constants;
import com.hand.demo.infra.util.Utils;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.boot.apaas.common.userinfo.domain.UserVO;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.base.BaseAppService;
import org.hzero.core.cache.ProcessCacheValue;
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
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */
@Service
@Slf4j
public class InvCountHeaderServiceImpl extends BaseAppService implements InvCountHeaderService {
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
                    .filter(header -> "DRAFT".equals(header.getCountStatus()))
                    .collect(Collectors.toList());

            List<InvCountHeaderDTO> inCounting = updateList.stream()
                    .filter(header -> "INCOUNTING".equals(header.getCountStatus()))
                    .collect(Collectors.toList());

            // Batch update for draft records
            if (!CollectionUtils.isEmpty(drafts)) {
                invCountHeaderRepository.batchUpdateOptional(
                        new ArrayList<>(drafts),
                        InvCountHeaderDTO.FIELD_COMPANY_ID,
                        InvCountHeaderDTO.FIELD_DEPARTMENT_ID,
                        InvCountHeaderDTO.FIELD_WAREHOUSE_ID,
                        InvCountHeaderDTO.FIELD_COUNT_DIMENSION,
                        InvCountHeaderDTO.FIELD_COUNT_TYPE,
                        InvCountHeaderDTO.FIELD_COUNT_MODE,
                        InvCountHeaderDTO.FIELD_COUNT_TIME_STR,
                        InvCountHeaderDTO.FIELD_COUNTER_IDS,
                        InvCountHeaderDTO.FIELD_SUPERVISOR_IDS,
                        InvCountHeaderDTO.FIELD_SNAPSHOT_MATERIAL_IDS,
                        InvCountHeaderDTO.FIELD_SNAPSHOT_BATCH_IDS,
                        InvCountHeaderDTO.FIELD_REMARK
                );
            }

            // Batch update for in-counting records and their associated lines
            if (!CollectionUtils.isEmpty(inCounting)) {
                invCountHeaderRepository.batchUpdateOptional(
                        new ArrayList<>(inCounting),
                        InvCountHeaderDTO.FIELD_REMARK,
                        InvCountHeaderDTO.FIELD_REASON
                );

                inCounting.forEach(header -> {
                    if (!CollectionUtils.isEmpty(header.getCountOrderLineList())) {
                        invCountLineRepository.batchUpdateOptional(
                                new ArrayList<>(header.getCountOrderLineList()),
                                InvCountLineDTO.FIELD_REMARK,
                                InvCountLineDTO.FIELD_COUNTER_IDS,
                                InvCountLineDTO.FIELD_UNIT_DIFF_QTY,
                                InvCountLineDTO.FIELD_UNIT_QTY
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
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
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

        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            if (invCountHeaderDTO.getErrorMsg().isEmpty()) {
                success.add(invCountHeaderDTO);
            } else {
                failed.add(invCountHeaderDTO);
                tErrMsg.append(invCountHeaderDTO.getErrorMsg());
            }
        }

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
        for (InvCountHeaderDTO invCountHeader : invCountHeaderDTOS) {
            StringBuilder errMsg = new StringBuilder();

            // Ensure the status is "DRAFT" before allowing deletion
            if (!"DRAFT".equals(invCountHeaderMap.get(invCountHeader.getCountHeaderId()).getCountStatus())) {
                errMsg.append("Only allow draft status to be deleted");
            }

            // Verify the current user is the creator of the document
            if (!Objects.equals(userDetails.getUserId(), invCountHeaderMap.get(invCountHeader.getCountHeaderId()).getCreatedBy())) {
                errMsg.append("Only the document creator is allowed to delete the document");
            }

            // Assign error messages, if any, to the DTO
            invCountHeader.setErrorMsg(errMsg.toString());
        }

        // Collect validated information into success and error lists
        InvCountInfoDTO theInfo = getTheInfo(invCountHeaderDTOS);

        // Proceed with deletion if there are no errors
        if (CollectionUtils.isEmpty(theInfo.getErrorList())) {
            // Batch delete successfully validated headers
            invCountHeaderRepository.batchDeleteByPrimaryKey(new ArrayList<>(theInfo.getSuccessList()));

            // Extract header IDs from the success list
            List<InvCountHeaderDTO> successList = theInfo.getSuccessList();
            List<Long> idsHeader = new ArrayList<>();
            successList.forEach(invCountHeaderDTO -> idsHeader.add(invCountHeaderDTO.getCountHeaderId()));

            // Fetch and delete associated inventory count lines
            InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
            invCountLineDTO.setIdsHeader(idsHeader);
            List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectList(invCountLineDTO);
            invCountLineRepository.batchDeleteByPrimaryKey(new ArrayList<>(invCountLineDTOS));
        }

        // Return the result, including any errors
        return theInfo;
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
        // Fetch inventory count header by its primary ID
        InvCountHeader invCountHeader = invCountHeaderRepository.selectByPrimary(countHeaderId);
        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();

        // Throw exception if no header is found
        if (invCountHeader == null) {
            throw new CommonException("Order Not found");
        }

        // Copy properties from the entity to the DTO
        BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);

        // Populate snapshot material list if material IDs are present
        if (invCountHeader.getSnapshotMaterialIds() != null && !invCountHeader.getSnapshotMaterialIds().isEmpty()) {
            List<InvMaterial> invMaterials = invMaterialRepository.selectByIds(invCountHeader.getSnapshotMaterialIds());
            List<SnapShotMaterialDTO> materials = invMaterials.stream().map(invMaterial -> {
                SnapShotMaterialDTO materialDTO = new SnapShotMaterialDTO();
                materialDTO.setId(invMaterial.getMaterialId());
                materialDTO.setCode(invMaterial.getMaterialCode());
                return materialDTO;
            }).collect(Collectors.toList());
            invCountHeaderDTO.setSnapshotMaterialList(materials);
        }

        // Populate snapshot batch list if batch IDs are present
        if (invCountHeader.getSnapshotBatchIds() != null && !invCountHeader.getSnapshotBatchIds().isEmpty()) {
            List<InvBatch> invBatches = invBatchRepository.selectByIds(invCountHeader.getSnapshotBatchIds());
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
        reqList.setUserId(userSelf.getId());
        reqList.setTenantId(userSelf.getTenantId());
        reqList.setSupervisorIds(invCountHeaderDTO.getSupervisorIds());

        // Include tenant admin flag if present
        if (Boolean.TRUE.equals(userSelf.getTenantAdminFlag())) {
            reqList.setTenantAdminFlag(userSelf.getTenantAdminFlag());
        }

        // Fetch and map inventory count lines
        List<InvCountLineDTO> invCountLines = invCountLineRepository.selectList(reqList);
        if(!CollectionUtils.isEmpty(invCountLines)){
            List<InvCountLineDTO> collect = invCountLines.stream().map(invCountLine -> {
                InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
                BeanUtils.copyProperties(invCountLine, invCountLineDTO);
                return invCountLineDTO;
            }).collect(Collectors.toList());
            invCountHeaderDTO.setCountOrderLineList(collect);
        }

        // Fetch warehouse information and set WMS warehouse flag
        InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(invCountHeader.getWarehouseId());
        invCountHeaderDTO.setWMSWarehouse(invWarehouse.getIsWmsWarehouse() == 1);
        return invCountHeaderDTO;
    }

    public void validateLOVs(InvCountHeaderDTO invCountHeaderDTO) {
        // Determine the expected date format based on the count type
        String limitFormat = "MONTH".equals(invCountHeaderDTO.getCountType()) ? "yyyy-MM" : "yyyy";
        String time = invCountHeaderDTO.getCountTimeStr();
        try {
            // Parse the input time string
            LocalDate dateTime = LocalDate.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            // Format the time string based on the limitFormat
            String formattedTime = dateTime.format(DateTimeFormatter.ofPattern(limitFormat));
            invCountHeaderDTO.setCountTimeStr(formattedTime);
        } catch (DateTimeParseException e) {
            // Handle invalid date format exception
            throw new CommonException("Invalid date format: " + time);
        }
    }


    public void validateComDepaWare(InvCountHeaderDTO invCountHeaderDTO,
                                    StringBuilder errMsg,
                                    Map<Long, IamDepartment> iamDepartmentMap,
                                    Map<Long, IamCompany> iamCompanyMap,
                                    Map<Long, InvWarehouse> warehouseMap) {
        // Fetch related data for validation
        InvWarehouse invWarehouse = warehouseMap.get(invCountHeaderDTO.getWarehouseId());
        IamCompany iamCompany = iamCompanyMap.get(invCountHeaderDTO.getCompanyId());
        IamDepartment iamDepartment = iamDepartmentMap.get(invCountHeaderDTO.getDepartmentId());

        // Validate warehouse, department, and company existence
        if (Objects.isNull(invWarehouse)) {
            errMsg.append("Warehouse Cannot Be Found");
        }
        if (Objects.isNull(iamDepartment)) {
            errMsg.append("Department Cannot Be Found");
        }
        if (Objects.isNull(iamCompany)) {
            errMsg.append("Company Cannot Be Found");
        }
    }

    public void validateAvailability(InvCountHeaderDTO invCountHeaderDTO, StringBuilder errMsg) {
        // Parse and extract batch IDs from the input string
        String snapshotBatchIds = invCountHeaderDTO.getSnapshotBatchIds();
        List<Long> batchIds = Arrays.stream(snapshotBatchIds.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // Parse and extract material IDs from the input string
        String snapshotMaterialIds = invCountHeaderDTO.getSnapshotMaterialIds();
        List<Long> materialIds = Arrays.stream(snapshotMaterialIds.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // Build an inventory stock query object
        InvStock invStock = new InvStock()
                .setTenantId(invCountHeaderDTO.getTenantId())
                .setCompanyId(invCountHeaderDTO.getCompanyId())
                .setDepartmentId(invCountHeaderDTO.getDepartmentId())
                .setWarehouseId(invCountHeaderDTO.getWarehouseId())
                .setBatchIds(batchIds)
                .setMaterialsId(materialIds);

        // Fetch inventory stocks from the repository
        List<InvStock> invStocks = invStockRepository.selectList(invStock);

        // Validate that stock data is available
        if (invStocks.isEmpty()) {
            errMsg.append("Unable to query on hand quantity data.");
        }

        // Validate each stock entry for availability
        invStocks.forEach(invStock1 -> {
            if (invStock1.getAvailableQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                errMsg.append("Stock is empty! for id: ").append(invStock1.getStockId()).append(".");
            }
        });
    }


    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOS) {
        // Check if the input list is null or empty
        if (CollectionUtils.isEmpty(headerDTOS)) {
            return new InvCountInfoDTO();
        }

        // Validate the list against the UpdateCheck class
        validList(headerDTOS, InvCountHeaderDTO.UpdateCheck.class);

        // Fetch related data from repositories and services
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();
        Map<Long, IamCompany> iamCompanyMap = iamCompanyService.byIdsFromHeader(headerDTOS);
        Map<Long, InvWarehouse> invWarehouseMap = invWarehouseService.getFromOrders(headerDTOS);
        Map<Long, IamDepartment> iamDepartmentMap = iamDepartmentService.getFromHeaders(headerDTOS);

        // Iterate through each header DTO for validation
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();

            // Validate that the status is DRAFT
            if (!"DRAFT".equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus())) {
                errMsg.append("Only draft status can execute.");
            }

            // Validate that the user is the creator of the document
            if (!Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userDetails.getUserId())) {
                errMsg.append("Only the document creator can execute.");
            }

            // Perform specific validations
            validateLOVs(headerDTO);
            validateComDepaWare(headerDTO, errMsg, iamDepartmentMap, iamCompanyMap, invWarehouseMap);
            validateAvailability(headerDTO, errMsg);

            // Ensure supervisors and counters are not empty
            if (headerDTO.getSupervisorIds().isEmpty()) {
                errMsg.append("Supervisor cannot be empty.");
            }
            if (headerDTO.getCounterIds().isEmpty()) {
                errMsg.append("Counter cannot be empty.");
            }

            // If there are validation errors, throw an exception
            if (errMsg.length() > 0) {
                throw new CommonException(errMsg.toString());
            }
        }

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
        headerDTOS.forEach(invCountHeaderDTO -> invCountHeaderDTO.setCountStatus("INCOUNTING"));

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

    public UserVO getUserSelf()
    {
        try{
            ResponseEntity<String> stringResponseEntity = iamRemoteService.selectSelf();
            return objectMapper.readValue(stringResponseEntity.getBody(), UserVO.class);
        }catch (Exception e){
            throw new CommonException("Failed to get current user");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO countSyncWms(List<InvCountHeaderDTO> headerDTOS) {
        // Check if the input list is empty; if so, log a warning and return null
        if (headerDTOS.isEmpty()) {
            log.warn("Input list is empty. Returning null.");
            return null;
        }

        // Retrieve the current user information
        UserVO userSelf = getUserSelf();
        log.info("User  {} is syncing WMS for {} headers.", userSelf.getLoginName(), headerDTOS.size());

        // Initialize sets to collect unique warehouse IDs, tenant IDs, and source IDs
        Set<Long> wareHouseIds = new HashSet<>();
        Set<Long> tenantIds = new HashSet<>();
        List<Long> sourcesId = new ArrayList<>();

        // Populate the sets with IDs from the header DTOs
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            wareHouseIds.add(headerDTO.getWarehouseId());
            tenantIds.add(headerDTO.getTenantId());
            sourcesId.add(headerDTO.getCountHeaderId());
        }

        // Convert sets to lists for further processing
        List<Long> wareIds = new ArrayList<>(wareHouseIds);
        List<Long> teIds = new ArrayList<>(tenantIds);

        // Create an InvWarehouse object to hold the warehouse and tenant IDs
        InvWarehouse invWarehouse = new InvWarehouse();
        invWarehouse.setIdsWarehouse(wareIds);
        invWarehouse.setTenantIds(teIds);

        // Fetch the list of warehouses from the repository based on the collected IDs
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectList(invWarehouse);
        Map<Long, Map<Long, InvWarehouse>> wareHouses = new HashMap<>();

        // Populate a nested map to easily access warehouses by tenant and warehouse IDs
        for (InvWarehouse warehouse : invWarehouses) {
            wareHouses
                    .computeIfAbsent(warehouse.getTenantId(), k -> new HashMap<>())
                    .put(warehouse.getWarehouseId(), warehouse);
        }

        // Prepare to fetch InvCountExtra records based on source IDs
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

        // Process each header DTO to sync with WMS
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder(); // To accumulate error messages

            // Retrieve the corresponding warehouse for the current header DTO
            InvWarehouse invWarehouse1 = wareHouses.get(headerDTO.getTenantId()).get(headerDTO.getWarehouseId());
            if (invWarehouse1 == null) {
                errMsg.append("Warehouse is not found. ");
                log.warn("Warehouse not found for Tenant ID: {} and Warehouse ID: {}", headerDTO.getTenantId(), headerDTO.getWarehouseId());
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
                    // Set the employee number for the sync operation
                    headerDTO.setEmployeeNumber(userSelf.getLoginName());
                    try {
                        // Convert the header DTO to a JSON string for the WMS sync request
                        String jsonHeaderDTO = objectMapper.writeValueAsString(headerDTO);

                        // Invoke the WMS translation service
                        ResponsePayloadDTO responsePayloadDTO = utils.invokeTranslation(
                                jsonHeaderDTO,
                                Constants.NAMESPACE,
                                Constants.CODE_SERVER,
                                Constants.INTERFACE_CODE,
                                userSelf.get_token(),
                                null
                        );

                        // Parse the response payload
                        String payload = responsePayloadDTO.getPayload();
                        Map<String, String> map = objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {});

                        // Check the return status from the WMS response
                        if (map.get("returnStatus").equals("S")) {
                            // Sync was successful
                            syncStatusExtra.setProgramValue("SUCCESS");
                            syncMsgExtra.setProgramValue("-");
                            syncMsgExtra.setAttribute1(map.get("code"));
                            log.info("Successfully synced WMS for Count Header ID: {}", headerDTO.getCountHeaderId());
                        } else {
                            // Sync encountered an error
                            syncStatusExtra.setProgramValue("ERROR");
                            syncMsgExtra.setProgramValue(map.get("returnMsg"));
                            syncMsgExtra.setAttribute1(map.get("code"));
                            errMsg.append(map.get("returnMsg")).append(" ");
                            log.error("Error syncing WMS for Count Header ID: {}. Message: {}", headerDTO.getCountHeaderId(), map.get("returnMsg"));
                        }

                    } catch (JsonProcessingException e) {
                        // Handle JSON processing exceptions
                        log.error("Failed to parse headerDTO to JSON for Count Header ID: {}", headerDTO.getCountHeaderId(), e);
                        throw new CommonException("Failed to parse from object to string");
                    }
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
            if (errMsg.length() > 0) {
                log.warn("Count Header ID: {} has errors: {}", headerDTO.getCountHeaderId(), errMsg);
            }
        }

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
        InvWarehouse invWarehouseReq = new InvWarehouse();
        invWarehouseReq.setWarehouseId(headerDTO.getWarehouseId());
        invWarehouseReq.setTenantId(headerDTO.getTenantId());

        // Fetch the warehouse information from the repository
        InvWarehouse invWarehouse = invWarehouseRepository.selectOne(invWarehouseReq);

        // Initialize a StringBuilder to accumulate error messages
        StringBuilder errMsg = new StringBuilder();
        headerDTO.setStatus("S"); // Set initial status to "S" (Success)

        // Check if the warehouse is a WMS warehouse
        if (invWarehouse.getIsWmsWarehouse() != 1) {
            errMsg.append("The current warehouse is not a WMS warehouse, operations are not allowed.");
            headerDTO.setStatus("E"); // Set status to "E" (Error)
        }

        // Prepare to fetch count line data associated with the header
        InvCountLineDTO invCountLine = new InvCountLineDTO();
        invCountLine.setCountHeaderId(headerDTO.getCountHeaderId());
        List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectList(invCountLine);

        // Create a map to associate count line IDs with their DTOs for quick access
        Map<Long, InvCountLineDTO> mapLines = new HashMap<>();
        for (InvCountLineDTO invCountLineDTO : invCountLineDTOS) {
            mapLines.put(invCountLineDTO.getCountLineId(), invCountLineDTO);
        }

        // Check if the size of the count order line list matches the size of the fetched count lines
        int size = invCountLineDTOS.size();
        if (size != headerDTO.getCountOrderLineList().size()) {
            errMsg.append("The counting order line data is inconsistent with the INV system, please check the data.");
            headerDTO.setStatus("E"); // Set status to "E" (Error)
        }

        // Set the accumulated error message in the header DTO
        headerDTO.setErrorMsg(errMsg.toString());

        // If there are no errors, proceed to update the line data
        if (headerDTO.getStatus().equals("S")) {
            // Update the line data (including unitQty, unitDiffQty, remark)
            for (InvCountLineDTO invCountLineDTO : headerDTO.getCountOrderLineList()) {
                BigDecimal snapshotUnitQty = invCountLineDTO.getSnapshotUnitQty();
                if (invCountLineDTO.getUnitQty() != null) {
                    BigDecimal unitQty = invCountLineDTO.getUnitQty();
                    BigDecimal result = unitQty.subtract(snapshotUnitQty);
                    invCountLineDTO.setUnitDiffQty(result); // Calculate the difference
                }

                // Update the object version number from the map
                invCountLineDTO.setObjectVersionNumber(mapLines.get(invCountLineDTO.getCountLineId()).getObjectVersionNumber());
            }
            // Save the updated count order line data
            invCountLineService.saveData(headerDTO.getCountOrderLineList());
        }

        // Return the updated header DTO
        return headerDTO;
    }


    private Map<Long, Map<Long, InvCountLineDTO>> mappingLine(List<InvCountHeaderDTO> headerDTOS){
        List<Long> headerIds = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            headerIds.add(headerDTO.getCountHeaderId());
        }
        InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
        invCountLineDTO.setIdsHeader(headerIds);
        List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectList(invCountLineDTO);
        Map<Long, Map<Long, InvCountLineDTO>> linesMap = new HashMap<>();
        for (InvCountLineDTO warehouse : invCountLineDTOS) {
            linesMap
                    .computeIfAbsent(warehouse.getCountHeaderId(), k -> new HashMap<>())
                    .put(warehouse.getCountLineId(), warehouse);
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

        // Map lines data for efficient access.
        Map<Long, Map<Long, InvCountLineDTO>> linesMap = mappingLine(headerDTOS);

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
                if (!Objects.equals(
                        lineDTO.getUnitDiffQty(),
                        linesMap.get(headerDTO.getCountHeaderId()).get(lineDTO.getCountLineId()).getUnitDiffQty()
                )) {
                    isReasonRequired = true;
                }
            }

            // Validate that a reason is provided if differences exist.
            if (isReasonRequired && (headerDTO.getReason() == null || headerDTO.getReason().isEmpty())) {
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

            // Retrieve updated header data from the database.
            Map<Long, InvCountHeader> headerDataMap = selectByIds(headerDTOS);

            // Update DTOs with workflow-related details.
            updateHeaderDTOsFromDatabase(headerDTOS, headerDataMap);
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
            if(dbHeader.getApprovedTime() != null){
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
     *<p></p>
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
        InvCountInfoDTO validationInfo = self().manualSaveCheck(headerDTOS);

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
     *<p></p>
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

        // Return immediately if there are errors in the save process.
        if (!CollectionUtils.isEmpty(saveResult.getErrorList())) {
            return saveResult;
        }

        // Perform additional checks on the successfully saved headers to ensure readiness for execution.
        InvCountInfoDTO executionCheckResult = self().executeCheck(saveResult.getSuccessList());

        // Return with errors if the execution readiness checks fail.
        if (!CollectionUtils.isEmpty(executionCheckResult.getErrorList())) {
            saveResult.setErrorList(executionCheckResult.getErrorList());
            return saveResult;
        }

        // Execute the validated headers and update the success list.
        List<InvCountHeaderDTO> executedHeaders = self().execute(executionCheckResult.getSuccessList());
        saveResult.setSuccessList(executedHeaders);

        // Synchronize the executed headers with WMS and return the final result.
        return countSyncWms(executedHeaders);
    }


    /**
     * Handles the submission of inventory count orders.
     * This method saves, validates, and submits the orders, updating the response with both success and error details.
     *<p></p>
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

        // Proceed only if there are no errors in the save process.
        if (CollectionUtils.isEmpty(invCountInfoDTO.getErrorList())) {
            // Perform submission checks on the successfully saved headers.
            InvCountInfoDTO submitCheckResult = self().submitCheck(invCountInfoDTO.getSuccessList());

            // If submission checks pass, submit the headers.
            if (CollectionUtils.isEmpty(submitCheckResult.getErrorList())) {
                List<InvCountHeaderDTO> submittedHeaders = self().submit(submitCheckResult.getSuccessList());
                invCountInfoDTO.setSuccessList(submittedHeaders);
            } else {
                // If submission checks fail, update the response with errors.
                invCountInfoDTO.setErrorList(submitCheckResult.getErrorList());

                // Include successfully processed headers, if any, in the response.
                if (!CollectionUtils.isEmpty(submitCheckResult.getSuccessList())) {
                    invCountInfoDTO.setSuccessList(submitCheckResult.getSuccessList());
                }
            }
        }

        return invCountInfoDTO;
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
                .collect(Collectors.toSet()); // Collect into a Set to avoid duplicates

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
    @Async
    public CompletableFuture<IamCompany> getIamCompanyAsync(String companyCode) {
        return CompletableFuture.completedFuture(iamCompanyRepository.selectOne(new IamCompany().setCompanyCode(companyCode)));
    }

    @Async
    public CompletableFuture<IamDepartment> getIamDepartmentAsync(String departmentCode) {
        return CompletableFuture.completedFuture(iamDepartmentRepository.selectOne(new IamDepartment().setDepartmentCode(departmentCode)));
    }

    @Async
    public CompletableFuture<InvWarehouse> getInvWarehouseAsync(String warehouseCode) {
        return CompletableFuture.completedFuture(invWarehouseRepository.selectOne(new InvWarehouse().setWarehouseCode(warehouseCode)));
    }

    @Async
    public CompletableFuture<Map<Long, IamDepartment>> getIamDepartmentMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        return CompletableFuture.completedFuture(iamDepartmentService.getFromHeaders(invCountHeaderDTOS));
    }

    @Async
    public CompletableFuture<Map<Long, InvWarehouse>> getWarehouseMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        return CompletableFuture.completedFuture(invWarehouseService.getFromOrders(invCountHeaderDTOS));
    }

    @Async
    public CompletableFuture<Map<Long, InvBatch>> getInvBatchMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        return CompletableFuture.completedFuture(invBatchService.getFromHeaders(invCountHeaderDTOS));
    }

    @Async
    public CompletableFuture<Map<Long, InvMaterial>> getInvMaterialMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        return CompletableFuture.completedFuture(invMaterialService.getFromHeaders(invCountHeaderDTOS));
    }

    @Async
    public CompletableFuture<Map<Long, Map<Long, InvCountLineDTO>>> getLineMapAsync(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        return CompletableFuture.completedFuture(mappingLine(invCountHeaderDTOS));
    }

    @Override
    @ProcessCacheValue
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> report(InvCountHeaderDTO invCountHeaderDTO) {
        // List to hold CompletableFutures for asynchronous operations
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Fetching data asynchronously
        CompletableFuture<IamCompany> companyFuture = null;
        if (invCountHeaderDTO.getWareHouseCode() != null) {
            // Asynchronously fetch company details if warehouse code is provided
            companyFuture = getIamCompanyAsync(invCountHeaderDTO.getCompanyCode());
            futures.add(companyFuture); // Add to the list of futures
        }

        CompletableFuture<IamDepartment> departmentFuture = null;
        if (invCountHeaderDTO.getWareHouseCode() != null) {
            // Asynchronously fetch department details if warehouse code is provided
            departmentFuture = getIamDepartmentAsync(invCountHeaderDTO.getDepartmentCode());
            futures.add(departmentFuture); // Add to the list of futures
        }

        // Note: You probably don't need to add departmentFuture twice, so removing this duplicate.
        CompletableFuture<InvWarehouse> warehouseFuture = null;
        if (invCountHeaderDTO.getWareHouseCode() != null) {
            // Asynchronously fetch warehouse details if warehouse code is provided
            warehouseFuture = getInvWarehouseAsync(invCountHeaderDTO.getWareHouseCode());
            futures.add(warehouseFuture); // Add to the list of futures
        }

        // Filter out null values from futures list before passing to allOf
        futures.removeIf(Objects::isNull);  // Remove null values to avoid issues

        // Wait for all futures to complete (fetching company, department, and warehouse details)
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // Set values after all async tasks are completed, checking for errors and null results
        if (companyFuture != null && companyFuture.isDone() && !companyFuture.isCompletedExceptionally()) {
            IamCompany company = companyFuture.join();  // Using join() to avoid try-catch
            if (company != null) {
                invCountHeaderDTO.setCompanyId(company.getCompanyId());
            }
        }

        if (departmentFuture != null && departmentFuture.isDone() && !departmentFuture.isCompletedExceptionally()) {
            IamDepartment department = departmentFuture.join();  // Using join() to avoid try-catch
            if (department != null) {
                invCountHeaderDTO.setDepartmentId(department.getDepartmentId());
            }
        }

        if (warehouseFuture != null && warehouseFuture.isDone() && !warehouseFuture.isCompletedExceptionally()) {
            InvWarehouse warehouse = warehouseFuture.join();  // Using join() to avoid try-catch
            if (warehouse != null) {
                invCountHeaderDTO.setWarehouseId(warehouse.getWarehouseId());
            }
        }

        // Fetch invCountHeaderDTOs from the repository
        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountHeaderRepository.selectList(invCountHeaderDTO);
        if (CollectionUtils.isEmpty(invCountHeaderDTOS)) {
            return invCountHeaderDTOS; // Return early if no data is found
        }

        // Fetch maps asynchronously for department, warehouse, batch, material, and count lines
        CompletableFuture<Map<Long, IamDepartment>> iamDepartmentFuture = getIamDepartmentMapAsync(invCountHeaderDTOS);
        CompletableFuture<Map<Long, InvWarehouse>> warehouseFutureMap = getWarehouseMapAsync(invCountHeaderDTOS);
        CompletableFuture<Map<Long, InvBatch>> invBatchFuture = getInvBatchMapAsync(invCountHeaderDTOS);
        CompletableFuture<Map<Long, InvMaterial>> invMaterialFuture = getInvMaterialMapAsync(invCountHeaderDTOS);
        CompletableFuture<Map<Long, Map<Long, InvCountLineDTO>>> lineMapFuture = getLineMapAsync(invCountHeaderDTOS);

        // Add all these futures to the list
        futures.add(iamDepartmentFuture);
        futures.add(warehouseFutureMap);
        futures.add(invBatchFuture);
        futures.add(invMaterialFuture);
        futures.add(lineMapFuture);

        // Wait for all these futures to complete
        CompletableFuture.allOf(iamDepartmentFuture, warehouseFutureMap, invBatchFuture, invMaterialFuture, lineMapFuture).join();

        // Retrieve the results from the futures
        Map<Long, IamDepartment> iamDepartmentMap = iamDepartmentFuture.join();
        Map<Long, InvWarehouse> warehouseMap = warehouseFutureMap.join();
        Map<Long, InvBatch> invBatchMap = invBatchFuture.join();
        Map<Long, InvMaterial> invMaterialMap = invMaterialFuture.join();
        Map<Long, Map<Long, InvCountLineDTO>> lineMap = lineMapFuture.join();

        // Prepare a list of statuses for processing
        List<String> statueses = new ArrayList<>();
        statueses.add("PROCESSING");
        statueses.add("REJECTED");
        statueses.add("APPROVED");
        statueses.add("WITHDRAWN");

        // Process each invCountHeaderDTO in parallel (for better performance)
        invCountHeaderDTOS.parallelStream().forEach(invCountHeaderDTO1 -> {
            // Check if snapshotBatchIds exist, and process them
            if (invCountHeaderDTO1.getSnapshotBatchIds() != null && !invCountHeaderDTO1.getSnapshotBatchIds().isEmpty()) {
                List<SnapShotBatchDTO> batches = getBatches(invCountHeaderDTO1.getSnapshotBatchIds(), invBatchMap);
                invCountHeaderDTO1.setSnapshotBatchList(batches);

                // Join batch codes into a string
                String bCodes = batches.stream()
                        .map(SnapShotBatchDTO::getCode)
                        .collect(Collectors.joining(", "));
                invCountHeaderDTO1.setBatchCodes(bCodes);
            }

            // Check if snapshotMaterialIds exist, and process them
            if (invCountHeaderDTO1.getSnapshotMaterialIds() != null && !invCountHeaderDTO1.getSnapshotMaterialIds().isEmpty()) {
                List<SnapShotMaterialDTO> materials = getMaterials(invCountHeaderDTO1.getSnapshotMaterialIds(), invMaterialMap);
                invCountHeaderDTO1.setSnapshotMaterialList(materials);

                // Join material codes into a string
                String mCodes = materials.stream()
                        .map(SnapShotMaterialDTO::getCode)
                        .collect(Collectors.joining(", "));
                invCountHeaderDTO1.setMaterialCodes(mCodes);
            }

            // Set department name using department ID
            String departmentName = iamDepartmentMap.get(invCountHeaderDTO1.getDepartmentId()).getDepartmentName();
            invCountHeaderDTO1.setDepartmentName(departmentName);

            // Set warehouse code using warehouse ID
            invCountHeaderDTO1.setWareHouseCode(warehouseMap.get(invCountHeaderDTO1.getWarehouseId()).getWarehouseCode());

            // If the count status is in the list of statuses, fetch approval history
            if (statueses.contains(invCountHeaderDTO1.getCountStatus())) {
                List<RunTaskHistory> history = workflowService.getHistory(invCountHeaderDTO1.getTenantId(), Constants.FLOW_KEY_CODE, invCountHeaderDTO1.getCountNumber());
                invCountHeaderDTO1.setHistoryApproval(history);
            }

            // Set count line details if available
            if (lineMap.get(invCountHeaderDTO1.getCountHeaderId()) != null && !lineMap.get(invCountHeaderDTO1.getCountHeaderId()).isEmpty()) {
                List<InvCountLineDTO> collect = new ArrayList<>(lineMap.get(invCountHeaderDTO1.getCountHeaderId()).values());
                invCountHeaderDTO1.setCountOrderLineList(collect);

                // Process each line in parallel
                List<InvCountLineDTO> countOrderLineList = invCountHeaderDTO1.getCountOrderLineList();
                countOrderLineList.parallelStream().forEach(invCountLineDTO -> {
                    invCountLineDTO.setItemName(invMaterialMap.get(invCountLineDTO.getMaterialId()).getMaterialName());
                    invCountLineDTO.setItemCode(invMaterialMap.get(invCountLineDTO.getMaterialId()).getMaterialCode());
                    if (invCountLineDTO.getBatchId() != null) {
                        invCountLineDTO.setBatchCode(invBatchMap.get(invCountLineDTO.getBatchId()).getBatchCode());
                    }
                });
            }
        });

        // Return the populated list of InvCountHeaderDTOs
        return invCountHeaderDTOS;
    }

    /**
     * Updates the inventory count header based on workflow callback events.
     *<p></p>
     * This method updates the count status, workflow ID, approval time, and supervisor IDs
     * based on the workflow event data provided in the callback.
     *
     * @param workFlowEventDTO The workflow event data used to update the inventory count header.
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void callbackHeader(WorkFlowEventDTO workFlowEventDTO) {
        // Create a DTO to search for the corresponding inventory count header.
        InvCountHeaderDTO searchCriteria = new InvCountHeaderDTO();
        searchCriteria.setCountNumber(workFlowEventDTO.getBusinessKey());
        InvCountHeader invCountHeader = invCountHeaderRepository.selectOne(searchCriteria);

        // Update the count status and workflow ID from the workflow event.
        invCountHeader.setCountStatus(workFlowEventDTO.getDocStatus());
        invCountHeader.setWorkflowId(workFlowEventDTO.getWorkflowId());

        // Set the approval time if the document status is "APPROVED".
        if ("APPROVED".equals(workFlowEventDTO.getDocStatus())) {
            invCountHeader.setApprovedTime(workFlowEventDTO.getApprovedTime());
        }

        // Assign the current user's ID as the supervisor ID if the document status is "PROCESSING".
        if ("PROCESSING".equals(workFlowEventDTO.getDocStatus())) {
            Long currentUserId = getUserSelf().getId();
            invCountHeader.setSupervisorIds(String.valueOf(currentUserId));
        }

        // Update the inventory count header in the repository.
        invCountHeaderRepository.updateByPrimaryKeySelective(invCountHeader);
    }


    /**
     * Populates and associates count line data with the given inventory count header.
     *<p></p>
     * This method iterates through the provided stock data to create inventory count lines
     * and assigns them to the inventory count header.
     *
     * @param invCountHeaderDTO The inventory count header DTO to associate the count lines with.
     * @param stocks A list of inventory stock DTOs used to populate the count lines.
     */
    public void setCreateLineToHeader(InvCountHeaderDTO invCountHeaderDTO, List<InvStockDTO> stocks) {
        // Initialize a list to hold the created count lines.
        List<InvCountLineDTO> saveLine = new ArrayList<>();
        int lineNumber = 1;

        // Iterate through the stock data to create and populate count lines.
        for (InvStockDTO stock : stocks) {
            InvCountLineDTO invCountLineDTO = new InvCountLineDTO();

            // Populate common fields from the header and stock data.
            invCountLineDTO.setTenantId(invCountHeaderDTO.getTenantId());
            invCountLineDTO.setLineNumber(lineNumber);
            invCountLineDTO.setWarehouseId(invCountHeaderDTO.getWarehouseId());
            invCountLineDTO.setMaterialId(stock.getMaterialId());

            // Set optional batch information if available.
            if (stock.getBatchId() != null) {
                invCountLineDTO.setBatchId(stock.getBatchId());
            }

            // Populate additional fields from the header and stock data.
            invCountLineDTO.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
            invCountLineDTO.setUnitCode(stock.getUnitCode());
            invCountLineDTO.setMaterialCode(stock.getMaterialCode());

            // Set snapshot quantity and counter information.
            invCountLineDTO.setSnapshotUnitQty(stock.getSummary());
            invCountLineDTO.setCounterIds(invCountHeaderDTO.getCounterIds());

            // Add the created line to the list and increment the line number.
            saveLine.add(invCountLineDTO);
            lineNumber++;
        }

        // Assign the created count lines to the header DTO.
        invCountHeaderDTO.setCountOrderLineList(saveLine);
    }


    public Map<Long, InvCountHeader> selectByIds(List<InvCountHeaderDTO> headerDTOS)
    {
        if(CollectionUtils.isEmpty(headerDTOS)){
            return new HashMap<>();
        }
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            if(headerDTO.getCountHeaderId() == null)
            {
                headerDTO.setErrorMsg("Header id cannot be null");
            }
        }
        Set<String> countIdSet = headerDTOS.stream()
                .map(header -> header.getCountHeaderId().toString())
                .collect(Collectors.toSet());
        String countIds = String.join(",", countIdSet);
        List<InvCountHeader> invCountHeaders = invCountHeaderRepository.selectByIds(countIds);
        Map<Long, InvCountHeader> invCountHeaderMap = new HashMap<>();
        for (InvCountHeader invCountHeader : invCountHeaders) {
            invCountHeaderMap.put(invCountHeader.getCountHeaderId(), invCountHeader);
        }
        return invCountHeaderMap;
    }

    /**
     * Validates the update list of inventory count headers.
     */
    public void updateVerification(List<InvCountHeaderDTO> headerDTOS) {
        if (CollectionUtils.isEmpty(headerDTOS)) return;

        // Validate the list of headers against update-specific constraints
        validList(headerDTOS, InvCountHeader.UpdateCheck.class);

        // Map existing headers by ID for reference
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);
        Long userId = DetailsHelper.getUserDetails().getUserId();

        // Define allowed statuses for updates
        List<String> statuses = Arrays.asList("DRAFT", "INCOUNTING", "REJECTED", "WITHDRAWN");

        // Fetch warehouse information and map lines for validation
        Map<Long, InvWarehouse> invWarehouseMap = invWarehouseService.getFromOrders(headerDTOS);
        Map<Long, Map<Long, InvCountLineDTO>> lineMap = mappingLine(headerDTOS);

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();

            // Populate missing company and warehouse IDs from the database
            if (headerDTO.getCompanyId() == null) {
                headerDTO.setCompanyId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCompanyId());
            }
            if (headerDTO.getWarehouseId() == null) {
                headerDTO.setWarehouseId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getWarehouseId());
            }

            // Check status validity
            String currentStatus = invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus();
            if (!statuses.contains(currentStatus)) {
                errMsg.append("Only draft, in counting, rejected, and withdrawn status can be modified.");
            }

            // Prevent unauthorized status changes
            if (headerDTO.getCountStatus() != null) {
                errMsg.append("The status cannot be changed.");
            } else {
                headerDTO.setCountStatus(currentStatus);
            }

            // Ensure draft documents are only modifiable by their creator
            if ("DRAFT".equals(currentStatus) && !Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userId)) {
                errMsg.append("Draft documents can only be modified by their creator.");
            }

            // Validate permissions for other statuses
            if (!"DRAFT".equals(currentStatus) && statuses.contains(currentStatus)) {
                List<String> supervisors = Arrays.asList(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSupervisorIds().split(","));
                List<String> counters = Arrays.asList(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCounterIds().split(","));

                if (invWarehouseMap.get(headerDTO.getWarehouseId()).getIsWmsWarehouse() == 1 && !supervisors.contains(userId.toString())) {
                    errMsg.append("Only supervisors can operate on WMS warehouses.");
                }
                if (!counters.contains(userId.toString()) && !supervisors.contains(userId.toString()) && !Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userId)) {
                    errMsg.append("Only authorized personnel can modify documents in non-draft statuses.");
                }
            }

            // Restrict reason changes to specific statuses
            if ("DRAFT".equals(currentStatus) && statuses.contains(currentStatus) && !headerDTO.getReason().isEmpty()) {
                errMsg.append("Reasons can only be modified for non-draft statuses.");
            }

            // Populate missing snapshot material and batch IDs
            if (headerDTO.getSnapshotMaterialIds() == null) {
                headerDTO.setSnapshotMaterialIds(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSnapshotMaterialIds());
            }
            if (headerDTO.getSnapshotBatchIds() == null) {
                headerDTO.setSnapshotBatchIds(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSnapshotBatchIds());
            }

            Set<Long> idsCounter = new HashSet<>();
            Arrays.stream(headerDTO.getCounterIds().split(",")).forEach(
                    s -> idsCounter.add(Long.parseLong(s))
            );
            // Validate associated count lines
            List<InvCountLineDTO> invCountLineDTOList = headerDTO.getCountOrderLineList();
            if (!CollectionUtils.isEmpty(invCountLineDTOList)) {
                for (InvCountLineDTO invCountLineDTO : invCountLineDTOList) {
                    if (invCountLineDTO.getTenantId() == null) {
                        invCountLineDTO.setTenantId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getTenantId());
                    }
                    if (invCountLineDTO.getCountHeaderId() == null) {
                        invCountLineDTO.setCountHeaderId(headerDTO.getCountHeaderId());
                    }

                    // Calculate unit differences for "INCOUNTING" status
                    if ("INCOUNTING".equals(currentStatus)
                            && invCountLineDTO.getUnitQty() != null && idsCounter.contains(userId)) {
                        BigDecimal difference = invCountLineDTO.getUnitQty().subtract(lineMap.get(headerDTO.getCountHeaderId()).get(invCountLineDTO.getCountLineId()).getSnapshotUnitQty());
                        invCountLineDTO.setUnitDiffQty(difference);
                    }
                }

                // Validate count lines
                validList(headerDTO.getCountOrderLineList(), InvCountLine.UpdateCheck.class);
            }

            // Assign accumulated error messages
            headerDTO.setErrorMsg(errMsg.toString());
        }
    }


    /**
     * Validates the insert list of inventory count headers.
     */
    public void insertVerification(List<InvCountHeaderDTO> headerDTOS) {
        if (CollectionUtils.isEmpty(headerDTOS)) return;

        // Validate the list of headers against creation-specific constraints
        validList(headerDTOS, InvCountHeader.CreateCheck.class);

        // Fetch related company and warehouse information
        Map<Long, IamCompany> iamCompanyMap = iamCompanyService.byIdsFromHeader(headerDTOS);
        Map<Long, InvWarehouse> invWarehouseMap = invWarehouseService.getFromOrders(headerDTOS);

        // Validate and append error messages for missing data
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();

            if (iamCompanyMap.get(headerDTO.getCompanyId()) == null) {
                errMsg.append("Company cannot be found.");
            }
            if (invWarehouseMap.get(headerDTO.getWarehouseId()) == null) {
                errMsg.append("Warehouse cannot be found.");
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }
    }
}


