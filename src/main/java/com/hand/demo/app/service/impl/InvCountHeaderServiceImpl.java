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
import org.hzero.boot.apaas.common.userinfo.domain.UserVO;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.boot.platform.profile.ProfileClient;
import org.hzero.boot.workflow.dto.RunInstance;
import org.hzero.boot.workflow.dto.RunTaskHistory;
import org.hzero.core.base.BaseAppService;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.cache.ProcessCacheValue;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final static String CODE_SERVER = "FEXAM_WMS";
    private final static String NAMESPACE = "HZERO";
    private final static String INTERFACE_CODE = "fexam-wms-api.thirdAddCounting";

    @Autowired
    private Utils utils;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileClient profileClient;

    @Autowired
    private IamCompanyRepository iamCompanyRepository;

    @Autowired
    private LovAdapter lovAdapter;

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

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> manualSave(List<InvCountHeaderDTO> invCountHeaders) {
        if(invCountHeaders == null || invCountHeaders.isEmpty())
        {
            return null;
        }
        List<InvCountHeaderDTO> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());

        insertList.forEach(
                invCountHeader -> {
                    Map<String, String> args = new HashMap<>();
                    args.put("customSegment", invCountHeader.getTenantId().toString());
                    String s = codeRuleBuilder.generateCode(Constants.RULE_BUILDER_CODE, args);
                    invCountHeader.setCountNumber(s);
                }
        );

        if(!updateList.isEmpty()){
            List<InvCountHeaderDTO> draft = updateList.stream().filter(invCountHeaderDTO -> invCountHeaderDTO.getCountStatus().equals("DRAFT")).collect(Collectors.toList());
            List<InvCountHeaderDTO> incounting = updateList.stream().filter(invCountHeaderDTO -> invCountHeaderDTO.getCountStatus().equals("INCOUNTING")).collect(Collectors.toList());
            if(draft != null && !draft.isEmpty()){
                invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(draft)
                , InvCountHeaderDTO.FIELD_COMPANY_ID, InvCountHeaderDTO.FIELD_DEPARTMENT_ID,
                        InvCountHeaderDTO.FIELD_WAREHOUSE_ID, InvCountHeaderDTO.FIELD_COUNT_DIMENSION,
                        InvCountHeaderDTO.FIELD_COUNT_TYPE, InvCountHeaderDTO.FIELD_COUNT_MODE,
                        InvCountHeaderDTO.FIELD_COUNT_TIME_STR, InvCountHeaderDTO.FIELD_COUNTER_IDS,
                        InvCountHeaderDTO.FIELD_SUPERVISOR_IDS, InvCountHeaderDTO.FIELD_SNAPSHOT_MATERIAL_IDS,
                        InvCountHeaderDTO.FIELD_SNAPSHOT_BATCH_IDS, InvCountHeaderDTO.FIELD_REMARK);
            }

            if(incounting != null && !incounting.isEmpty()){
                invCountHeaderRepository.batchUpdateOptional(new ArrayList<>(incounting)
                        , InvCountHeaderDTO.FIELD_REMARK, InvCountHeaderDTO.FIELD_REASON);

                for (InvCountHeaderDTO invCountHeaderDTO : incounting) {
                    if(invCountHeaderDTO.getCountOrderLineList() != null){
                        invCountLineRepository.batchUpdateOptional(
                                new ArrayList<>(invCountHeaderDTO.getCountOrderLineList()),
                                InvCountLineDTO.FIELD_REMARK,
                                InvCountLineDTO.FIELD_COUNTER_IDS,
                                InvCountLineDTO.FIELD_UNIT_DIFF_QTY,
                                InvCountLineDTO.FIELD_UNIT_QTY
                        );
                    }
                }
            }
        }

        invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
        return invCountHeaders;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        if(invCountHeaderDTOS == null || invCountHeaderDTOS.isEmpty())
        {
            return new InvCountInfoDTO();
        }

        List<InvCountHeaderDTO> insertList = invCountHeaderDTOS.stream().filter(header -> header.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaderDTOS.stream().filter(header -> header.getCountHeaderId() != null).collect(Collectors.toList());

        insertVerification(insertList);
        updateVerification(updateList);

        return getTheInfo(invCountHeaderDTOS);
    }

    public InvCountInfoDTO getTheInfo(List<InvCountHeaderDTO> invCountHeaderDTOS){
        List<InvCountHeaderDTO> success = new ArrayList<>();
        List<InvCountHeaderDTO> failed = new ArrayList<>();
        InvCountInfoDTO invCountInfoDTO = new InvCountInfoDTO();
        StringBuilder tErrMsg = new StringBuilder();
        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            if(invCountHeaderDTO.getErrorMsg().isEmpty()){
                success.add(invCountHeaderDTO);
                continue;
            }
            failed.add(invCountHeaderDTO);
            tErrMsg.append(invCountHeaderDTO.getErrorMsg());
        }
        invCountInfoDTO.setTotalErrorMsg(tErrMsg.toString());
        invCountInfoDTO.setSuccessList(success);
        invCountInfoDTO.setErrorList(failed);
        return invCountInfoDTO;
    }


    @Override
    public InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(invCountHeaderDTOS);
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();
        for (InvCountHeaderDTO invCountHeader : invCountHeaderDTOS) {
            StringBuilder errMsg = new StringBuilder();
            if(!invCountHeaderMap.get(invCountHeader.getCountHeaderId()).getCountStatus().equals("DRAFT")) {
                errMsg.append("Only allow draft status to be deleted");
            }
            if(!Objects.equals(userDetails.getUserId(), invCountHeaderMap.get(invCountHeader.getCountHeaderId()).getCreatedBy())){
                errMsg.append("Only current user is document creator allow delete document");
            }
            invCountHeader.setErrorMsg(errMsg.toString());
        }
        invCountHeaderRepository.batchDelete(new ArrayList<>(invCountHeaderMap.values()));
        // Collect error information and throw
        return getTheInfo(invCountHeaderDTOS);
    }

    @Override
    public InvCountHeaderDTO detail(Long countHeaderId) {
            InvCountHeader invCountHeader =
                    invCountHeaderRepository.selectByPrimary(countHeaderId);
            InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
            if(invCountHeader == null) {
                throw new CommonException("Order Not found");
            }
            BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);

            List<InvMaterial> invMaterials = invMaterialRepository.selectByIds(invCountHeader.getSnapshotMaterialIds());
            List<SnapShotMaterialDTO> materials = new ArrayList<>();

            for (InvMaterial invMaterial : invMaterials) {
                SnapShotMaterialDTO materialDTO = new SnapShotMaterialDTO();
                materialDTO.setId(invMaterial.getMaterialId());
                materialDTO.setCode(invMaterial.getMaterialCode());
                materials.add(materialDTO);
            }

            List<InvBatch> invBatches = invBatchRepository.selectByIds(invCountHeader.getSnapshotBatchIds());
            List<SnapShotBatchDTO> batches = new ArrayList<>();
            for (InvBatch invBatch : invBatches) {
                SnapShotBatchDTO batchDTO = new SnapShotBatchDTO();
                batchDTO.setId(invBatch.getBatchId());
                batchDTO.setCode(invBatch.getBatchCode());
                batches.add(batchDTO);
            }
            UserVO userSelf = getUserSelf();
            invCountHeaderDTO.setSnapshotBatchList(batches);
            invCountHeaderDTO.setSnapshotMaterialList(materials);
            InvCountLineDTO reqList = new InvCountLineDTO();
            reqList.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
            reqList.setUserId(userSelf.getId());
            reqList.setTenantId(userSelf.getTenantId());
            reqList.setSupervisorIds(invCountHeaderDTO.getSupervisorIds());
//            reqList.setCounterIds(invCountHeaderDTO.getCounterIds());
            if(userSelf.getTenantAdminFlag() != null){
                reqList.setTenantAdminFlag(userSelf.getTenantAdminFlag());
            }
            List<InvCountLineDTO> invCountLines =
                    invCountLineRepository.selectList(reqList);
            List<InvCountLineDTO> collect = invCountLines.stream().map(invCountLine -> {
                InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
                BeanUtils.copyProperties(invCountLine, invCountLineDTO);
                return invCountLineDTO;
            }).collect(Collectors.toList());
            InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(invCountHeader.getWarehouseId());
            invCountHeaderDTO.setWMSWarehouse(invWarehouse.getIsWmsWarehouse() == 1);
            invCountHeaderDTO.setCountOrderLineList(collect);
            return invCountHeaderDTO;
    }

    public void validateLOVs(InvCountHeaderDTO invCountHeaderDTO) {
        if(invCountHeaderDTO.getCountType().equals("YEAR")) {
            invCountHeaderDTO.setCountTimeStr(String.valueOf(Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().getYear()));
        }else {
            Month month = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().getMonth();
            int value = month.getValue();
            int year = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().getYear();
            invCountHeaderDTO.setCountTimeStr(year + "-" + value);
        }
    }

    public void validateComDepaWare(InvCountHeaderDTO invCountHeaderDTO,
                                    StringBuilder errMsg,
                                    Map<Long, IamDepartment> iamDepartmentMap,
                                    Map<Long, IamCompany> iamCompanyMap,
                                    Map<Long, InvWarehouse> warehouseMap) {
        InvWarehouse invWarehouse = warehouseMap.get(invCountHeaderDTO.getWarehouseId());
        IamCompany iamCompany = iamCompanyMap.get(invCountHeaderDTO.getCompanyId());
        IamDepartment iamDepartment = iamDepartmentMap.get(invCountHeaderDTO.getDepartmentId());
        if(Objects.isNull(invWarehouse)) {
            errMsg.append("Warehouse Cannot Be Found");
        }
        if(Objects.isNull(iamDepartment)) {
            errMsg.append("Department Cannot Be Found");
        }
        if(Objects.isNull(iamCompany)){
            errMsg.append("Company Cannot Be Found");
        }
    }

    public void validateAvailability(InvCountHeaderDTO invCountHeaderDTO, StringBuilder errMsg) {

        String snapshotBatchIds = invCountHeaderDTO.getSnapshotBatchIds();
        String[] split = snapshotBatchIds.split(",");
        List<Long> batchIds = Arrays.stream(split).map(Long::parseLong).collect(Collectors.toList());
        String snapshotMaterialIds = invCountHeaderDTO.getSnapshotMaterialIds();
        String[] split1 = snapshotMaterialIds.split(",");
        List<Long> materialIds = Arrays.stream(split1).map(Long::parseLong).collect(Collectors.toList());

        InvStock invStock = new InvStock().setTenantId(invCountHeaderDTO.getTenantId())
                .setCompanyId(invCountHeaderDTO.getCompanyId())
                .setDepartmentId(invCountHeaderDTO.getDepartmentId())
                .setWarehouseId(invCountHeaderDTO.getWarehouseId())
                .setBatchIds(batchIds)
                .setMaterialsId(materialIds);
        List<InvStock> invStocks = invStockRepository.selectList(invStock);
        if(invStocks.isEmpty())
        {
            errMsg.append("Unable to query on hand quantity data.");
        }
        invStocks.forEach(
                invStock1 ->
                {
                    if (invStock1.getAvailableQuantity().compareTo(BigDecimal.ZERO) <= 0)
                    {
                        errMsg.append("Stock is empty! for id: ").append(invStock1.getStockId()).append(".");
                    }
                }
        );
    }


    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOS) {
        if(headerDTOS == null || headerDTOS.isEmpty()){
            return null;
        }
        validList(headerDTOS, InvCountHeaderDTO.UpdateCheck.class);
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);
//        Map<String, Map<String, String>> stringMapMap = lovStatus();
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();
        Map<Long, IamCompany> iamCompanyMap = iamCompanyService.byIdsFromHeader(headerDTOS);
        Map<Long, InvWarehouse> invWarehouseMap = invWarehouseService.getFromOrders(headerDTOS);
        Map<Long, IamDepartment> iamDepartmentMap = iamDepartmentService.getFromHeaders(headerDTOS);

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();
            if(!invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus().equals("DRAFT"))
            {
                errMsg.append("Only draft status can execute.");
            }
            if(!Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userDetails.getUserId()))
            {
                errMsg.append("Only the document creator can execute.");
            }
            validateLOVs(headerDTO);
            validateComDepaWare(headerDTO, errMsg, iamDepartmentMap, iamCompanyMap, invWarehouseMap);
            validateAvailability(headerDTO, errMsg);
            if(headerDTO.getSupervisorIds().isEmpty())
            {
                errMsg.append("Supervisor cannot be empty.");
            }
            if(headerDTO.getCounterIds().isEmpty())
            {
                errMsg.append("Counter cannot be empty.");
            }
        }
        return getTheInfo(headerDTOS);
    }


    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> execute(List<InvCountHeaderDTO> headerDTOS) {
        if(headerDTOS == null || headerDTOS.isEmpty())
        {
            return null;
        }
        headerDTOS.forEach(
                invCountHeaderDTO ->
                {
                    invCountHeaderDTO.setCountStatus("INCOUNTING");
                }
        );
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(headerDTOS));

        headerDTOS.forEach(
                invCountHeaderDTO -> {
                    List<InvStockDTO> summarizeStock = invStockService.getSummarizeStock(invCountHeaderDTO);
                    setCreateLineToHeader(invCountHeaderDTO, summarizeStock);
                    if(invCountHeaderDTO.getCountOrderLineList() != null && !invCountHeaderDTO.getCountOrderLineList().isEmpty())
                    {
                        invCountLineService.saveData(invCountHeaderDTO.getCountOrderLineList());
                    }
                }
        );

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
        if(headerDTOS.isEmpty()) {
            return null;
        }
        UserVO userSelf = getUserSelf();

        Set<Long> wareHouseIds = new HashSet<>();
        Set<Long> tenantIds = new HashSet<>();
        List<Long> sourcesId = new ArrayList<>();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            wareHouseIds.add(headerDTO.getWarehouseId());
            tenantIds.add(headerDTO.getTenantId());
            sourcesId.add(headerDTO.getCountHeaderId());
        }
        List<Long> wareIds = new ArrayList<>(wareHouseIds);
        List<Long> teIds = new ArrayList<>(tenantIds);
        InvWarehouse invWarehouse = new InvWarehouse();
        invWarehouse.setIdsWarehouse(wareIds);
        invWarehouse.setTenantIds(teIds);
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectList(invWarehouse);
        Map<Long, Map<Long, InvWarehouse>> wareHouses = new HashMap<>();
        // Populate the nested map
        for (InvWarehouse warehouse : invWarehouses) {
            wareHouses
                    .computeIfAbsent(warehouse.getTenantId(), k -> new HashMap<>())
                    .put(warehouse.getWarehouseId(), warehouse);
        }
        InvCountExtra invCountExtra = new InvCountExtra();

        invCountExtra.setSourceIds(sourcesId);
        invCountExtra.setEnabledFlag(1);
        List<InvCountExtra> invCountExtras = invCountExtraRepository.selectList(invCountExtra);

        Map<Long, List<InvCountExtra>> extraMap = new HashMap<>();
        for (InvCountExtra countExtra : invCountExtras) {
            extraMap
                    .computeIfAbsent(countExtra.getSourceId(), k -> new ArrayList<>())
                    .add(countExtra);
        }

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();

            InvWarehouse invWarehouse1 = wareHouses.get(headerDTO.getTenantId()).get(headerDTO.getWarehouseId());
            if(invWarehouse1 == null) {
                errMsg.append("Warehouse is not found");
            }

            List<InvCountExtra> invCountExtras1 = extraMap.get(headerDTO.getCountHeaderId());
            if(invCountExtras1 == null || invCountExtras1.isEmpty()){
                invCountExtras1 = new ArrayList<>();
                InvCountExtra syncStatusExtra = new InvCountExtra();
                invCountExtras1.add(syncStatusExtra);
                InvCountExtra syncMsgExtra = new InvCountExtra();
                invCountExtras1.add(syncMsgExtra);
            }
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

            if(invWarehouse1 != null) {
                Integer isWmsWarehouse = invWarehouse1.getIsWmsWarehouse();
                if(isWmsWarehouse == 1){
                    headerDTO.setEmployeeNumber(userSelf.getLoginName());
                    try{
                        String s = objectMapper.writeValueAsString(headerDTO);
                        ResponsePayloadDTO responsePayloadDTO = utils.invokeTranslation(
                                s,
                                NAMESPACE,
                                CODE_SERVER,
                                INTERFACE_CODE,
                                userSelf.get_token(),
                                null
                        );
                        String payload = responsePayloadDTO.getPayload();
                        Map<String, String> map = objectMapper.readValue(payload, new TypeReference<Map<String, String>>() {});
                        if(map.get("returnStatus").equals("S"))
                        {
                            syncStatusExtra.setProgramValue("SUCCESS");
                            syncMsgExtra.setProgramValue("");
                            syncMsgExtra.setAttribute1(map.get("code"));
                        }else {
                            syncStatusExtra.setProgramValue("ERROR");
                            syncMsgExtra.setProgramValue(map.get("returnMsg"));
                            syncMsgExtra.setAttribute1(map.get("code"));
                            errMsg.append(map.get("returnMsg"));
                        }

                    } catch (JsonProcessingException e) {
                        throw new CommonException("Failed to parse from object to string");
                    }
                }
            }else {
                syncStatusExtra.setProgramValue("SKIP");
            }
            headerDTO.setErrorMsg(errMsg.toString());
            invCountExtraService.saveData(invCountExtras1);
        }
        InvCountInfoDTO theInfo = getTheInfo(headerDTOS);
        if(theInfo.getErrorList() == null || theInfo.getErrorList().isEmpty())
        {
            return theInfo;
        }
        throw new CommonException(theInfo.getTotalErrorMsg());
    }

    @Override
    public InvCountHeaderDTO countResultSync(InvCountHeaderDTO headerDTO) {
        InvWarehouse invWarehouseReq = new InvWarehouse();
        invWarehouseReq.setWarehouseId(headerDTO.getWarehouseId());
        invWarehouseReq.setTenantId(headerDTO.getTenantId());
        InvWarehouse invWarehouse = invWarehouseRepository.selectOne(invWarehouseReq);

        StringBuilder errMsg = new StringBuilder();
        headerDTO.setStatus("S");
        if(invWarehouse.getIsWmsWarehouse() != 1)
        {
            errMsg.append("The current warehouse is not a WMS warehouse, operations are not allowed.");
            headerDTO.setStatus("E");
        }
        InvCountLineDTO invCountLine = new InvCountLineDTO();
        invCountLine.setCountHeaderId(headerDTO.getCountHeaderId());
        List<InvCountLineDTO> invCountLineDTOS = invCountLineRepository.selectList(invCountLine);
        Map<Long, InvCountLineDTO> mapLines = new HashMap<>();
        for (InvCountLineDTO invCountLineDTO : invCountLineDTOS) {
            mapLines.put(invCountLineDTO.getCountLineId(), invCountLineDTO);
        }

        int size = invCountLineDTOS.size();
        if(size != headerDTO.getCountOrderLineList().size())
        {
            errMsg.append("The counting order line data is inconsistent with the INV system, please check the data.");
            headerDTO.setStatus("E");
        }
        headerDTO.setErrorMsg(errMsg.toString());


        if(headerDTO.getStatus().equals("S")){
            // Update the line data
            //updateLines(including unitQty,unitDiffQty,remark);
            for (InvCountLineDTO invCountLineDTO : headerDTO.getCountOrderLineList()) {
                BigDecimal snapshotUnitQty = invCountLineDTO.getSnapshotUnitQty();
                if(invCountLineDTO.getUnitQty() != null){
                    BigDecimal unitQty = invCountLineDTO.getUnitQty();
                    BigDecimal result = unitQty.subtract(snapshotUnitQty);
                    invCountLineDTO.setUnitDiffQty(result);
                }

                invCountLineDTO.setObjectVersionNumber(mapLines.get(invCountLineDTO.getCountLineId()).getObjectVersionNumber());
            }
            invCountLineService.saveData(headerDTO.getCountOrderLineList());
        }
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

    @Override
    public InvCountInfoDTO submitCheck(List<InvCountHeaderDTO> headerDTOS) {
//        List<String> statusNeeded = new ArrayList<>(lovStatus().get(Constants.LOV_STATUS).values());
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();
        Map<Long, Map<Long, InvCountLineDTO>> linesMap = mappingLine(headerDTOS);

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            boolean isPresentReason = false;
            StringBuilder errMsg = new StringBuilder();
            String countStatus = headerDTO.getCountStatus();
            if(!countStatus.equals("PROCESSING") && !countStatus.equals("WITHDRAWN")
            && !countStatus.equals("INCOUNTING") && !countStatus.equals("REJECTED")){
                errMsg.append("The operation is allowed only when the status in in counting, processing, rejected, withdrawn.");
            }
            String supervisorIds = headerDTO.getSupervisorIds();
            String[] split = supervisorIds.split(",");
            List<Long> idsSupervisor = new ArrayList<>();
            for (String s : split) {
                idsSupervisor.add(Long.valueOf(s));
            }
            if(!idsSupervisor.contains(userDetails.getUserId())){
                errMsg.append("Only the current login user is the supervisor can submit document.");
            }

            for (InvCountLineDTO countLineDTO : headerDTO.getCountOrderLineList()) {
                if(countLineDTO.getUnitQty() == null){
                    errMsg.append("There are data rows with empty count quantity. Please check the data.");
                }
                if(!Objects.equals(countLineDTO.getUnitQty(), linesMap
                        .get(headerDTO.getCountHeaderId()).get(countLineDTO.getCountLineId()).getUnitQty())
                ){
                   isPresentReason = true;
                }
            }
            if(isPresentReason && headerDTO.getReason().isEmpty())
            {
                errMsg.append("When there is a difference in counting, the reason field must be entered.");
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }
        return getTheInfo(headerDTOS);
    }

    @Override
    public List<InvCountHeaderDTO> submit(List<InvCountHeaderDTO> headerDTOS) {
        String profileValueByOptions = profileClient.getProfileValueByOptions(0L, null, null, Constants.PROFILE_CONTENT_CODE);
        Map<Long, IamDepartment> departmentMap = iamDepartmentService.getFromHeaders(headerDTOS);
        int isWorkflow = Integer.parseInt(profileValueByOptions);
        if(isWorkflow == 1){
            headerDTOS.forEach(
                    invCountHeaderDTO -> {
                        WorkFlowEventDTO workFlowEventDTO = new WorkFlowEventDTO();
                        workFlowEventDTO.setBusinessKey(invCountHeaderDTO.getCountNumber());
                        IamDepartment iamDepartment = departmentMap.get(invCountHeaderDTO.getDepartmentId());
                        workflowService.startWorkFlow(invCountHeaderDTO.getTenantId(),
                                workFlowEventDTO, iamDepartment.getDepartmentCode()
                        );
                    }
            );
            Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);
            for (InvCountHeaderDTO headerDTO : headerDTOS) {
                headerDTO.setStatus(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus());
                if(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getApprovedTime() != null){
                    headerDTO.setApprovedTime(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getApprovedTime());
                }
                headerDTO.setWorkflowId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getWorkflowId());
                headerDTO.setSupervisorIds(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSupervisorIds());
            }
        }else {
            for (InvCountHeaderDTO headerDTO : headerDTOS) {
                headerDTO.setCountStatus("CONFIRMED");
            }
            invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(headerDTOS));
        }
        return headerDTOS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO orderSave(List<InvCountHeaderDTO> headerDTOS) {
        InvCountInfoDTO invCountInfoDTO = self().manualSaveCheck(headerDTOS);
        if(invCountInfoDTO.getErrorList()
         == null || invCountInfoDTO.getErrorList().isEmpty()){
            List<InvCountHeaderDTO> invCountHeaderDTOS = self().manualSave(headerDTOS);
            invCountInfoDTO.setTotalErrorMsg("");
            invCountInfoDTO.setSuccessList(invCountHeaderDTOS);
        }
        return invCountInfoDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO orderExecution(List<InvCountHeaderDTO> headerDTOS) {
        InvCountInfoDTO invCountInfoDTO = self().orderSave(headerDTOS);
        if(invCountInfoDTO.getErrorList().isEmpty()){
            InvCountInfoDTO executeCheck = self().executeCheck(invCountInfoDTO.getSuccessList());
            if(executeCheck.getErrorList().isEmpty()){
                List<InvCountHeaderDTO> execute = self().execute(executeCheck.getSuccessList());
                invCountInfoDTO.setSuccessList(execute);
                return countSyncWms(execute);
            }else {
                invCountInfoDTO.setErrorList(executeCheck.getErrorList());
            }
        }
        return invCountInfoDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public InvCountInfoDTO orderSubmit(List<InvCountHeaderDTO> headerDTOS) {
        InvCountInfoDTO invCountInfoDTO = self().orderSave(headerDTOS);
        if(invCountInfoDTO.getErrorList() == null || invCountInfoDTO.getErrorList().isEmpty()){
            InvCountInfoDTO submitCheck = self().submitCheck(invCountInfoDTO.getSuccessList());
            if(submitCheck.getErrorList() == null || submitCheck.getErrorList() .isEmpty()){
                List<InvCountHeaderDTO> submit = self().submit(submitCheck.getSuccessList());
                invCountInfoDTO.setSuccessList(submit);
            }else {
                invCountInfoDTO.setErrorList(submitCheck.getErrorList());
                //needed this if there is error and success
                if(submitCheck.getSuccessList() != null){
                    invCountInfoDTO.setSuccessList(submitCheck.getSuccessList());
                }
            }
        }
        return invCountInfoDTO;
    }

    private List<SnapShotMaterialDTO> getMaterials(String materialIds, Map<Long, InvMaterial> invMaterialMap){
        Set<Long> idsMats = new HashSet<>();
        String[] split = materialIds.split(",");
        for (String s : split) {
            idsMats.add(Long.valueOf(s));
        }
        List<SnapShotMaterialDTO> result = new ArrayList<>();
        for (Long idsMat : idsMats) {
            InvMaterial invMaterial = invMaterialMap.get(idsMat);
            SnapShotMaterialDTO snapShotMaterialDTO = new SnapShotMaterialDTO();
            snapShotMaterialDTO.setId(idsMat);
            snapShotMaterialDTO.setCode(invMaterial.getMaterialCode());
            result.add(snapShotMaterialDTO);
        }
        return result;
    }

    private List<SnapShotBatchDTO> getBatches(String batchids, Map<Long, InvBatch> invBatchMap){
        Set<Long> idsBatch = new HashSet<>();
        String[] split = batchids.split(",");
        for (String s : split) {
            idsBatch.add(Long.valueOf(s));
        }
        List<SnapShotBatchDTO> result = new ArrayList<>();
        for (Long idsMat : idsBatch) {
            InvBatch invBatch = invBatchMap.get(idsMat);
            SnapShotBatchDTO snapShotBatchDTO = new SnapShotBatchDTO();
            snapShotBatchDTO.setId(idsMat);
            snapShotBatchDTO.setCode(invBatch.getBatchCode());
            result.add(snapShotBatchDTO);
        }
        return result;
    }

    @Override
    @ProcessCacheValue
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public List<InvCountHeaderDTO> report(InvCountHeaderDTO invCountHeaderDTO) {

        if(invCountHeaderDTO.getCompanyCode() != null){
            IamCompany iamCompany = iamCompanyRepository.selectOne(new IamCompany().setCompanyCode(invCountHeaderDTO.getCompanyCode()));
            invCountHeaderDTO.setCompanyId(iamCompany.getCompanyId());
        }
        if(invCountHeaderDTO.getDepartmentCode() != null){
            IamDepartment iamDepartment = iamDepartmentRepository.selectOne(new IamDepartment().setDepartmentCode(invCountHeaderDTO.getDepartmentCode()));
            invCountHeaderDTO.setDepartmentId(iamDepartment.getDepartmentId());
        }
        if(invCountHeaderDTO.getWareHouseCode() != null){
            InvWarehouse invWarehouse = invWarehouseRepository.selectOne(new InvWarehouse().setWarehouseCode(invCountHeaderDTO.getWareHouseCode()));
            invCountHeaderDTO.setWarehouseId(invWarehouse.getWarehouseId());
        }


        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountHeaderRepository.selectList(invCountHeaderDTO);

        Map<Long, IamDepartment> iamDepartmentMap = iamDepartmentService.getFromHeaders(invCountHeaderDTOS);
        Map<Long, InvWarehouse> warehouseMap = invWarehouseService.getFromOrders(invCountHeaderDTOS);
        Map<Long, InvBatch> invBatchMap = invBatchService.getFromHeaders(invCountHeaderDTOS);
        Map<Long, InvMaterial> invMaterialMap = invMaterialService.getFromHeaders(invCountHeaderDTOS);
        Map<Long, Map<Long, InvCountLineDTO>> lineMap = mappingLine(invCountHeaderDTOS);


        invCountHeaderDTOS.forEach(
                invCountHeaderDTO1 -> {
                    List<SnapShotBatchDTO> batches = getBatches(invCountHeaderDTO1.getSnapshotBatchIds(), invBatchMap);
                    List<SnapShotMaterialDTO> materials = getMaterials(invCountHeaderDTO1.getSnapshotMaterialIds(), invMaterialMap);

                    invCountHeaderDTO1.setSnapshotMaterialList(materials);
                    invCountHeaderDTO1.setSnapshotBatchList(batches);

                    String departmentName = iamDepartmentMap.get(invCountHeaderDTO1.getDepartmentId()).getDepartmentName();

                    invCountHeaderDTO1.setDepartmentName(departmentName);

                    invCountHeaderDTO1.setWareHouseCode(warehouseMap.get(invCountHeaderDTO1.getWarehouseId()).getWarehouseCode());
                    List<RunTaskHistory> history = workflowService.getHistory(invCountHeaderDTO1.getTenantId(), Constants.FLOW_KEY_CODE, invCountHeaderDTO1.getCountNumber());
                    invCountHeaderDTO1.setHistoryApproval(history);

                    String mCodes = materials.stream()
                            .map(SnapShotMaterialDTO::getCode)
                            .collect(Collectors.joining(", "));

                    String bCodes = batches.stream()
                            .map(SnapShotBatchDTO::getCode)
                            .collect(Collectors.joining(", "));

                    invCountHeaderDTO1.setMaterialCodes(mCodes);
                    invCountHeaderDTO1.setBatchCodes(bCodes);

                    invCountHeaderDTO1.setCountOrderLineList(new ArrayList<>(lineMap.get(invCountHeaderDTO1.getCountHeaderId()).values()));
                    List<InvCountLineDTO> countOrderLineList = invCountHeaderDTO1.getCountOrderLineList();

                    for (InvCountLineDTO invCountLineDTO : countOrderLineList) {
                        invCountLineDTO.setItemName(invMaterialMap.get(invCountLineDTO.getMaterialId()).getMaterialName());
                        invCountLineDTO.setItemCode(invMaterialMap.get(invCountLineDTO.getMaterialId()).getMaterialCode());
                        if(invCountLineDTO.getBatchId() != null){
                            invCountLineDTO.setBatchCode(invBatchMap.get(invCountLineDTO.getBatchId()).getBatchCode());
                        }
                    }
                }
        );
        return invCountHeaderDTOS;
    }

    @Override
    public void callbackHeader(WorkFlowEventDTO workFlowEventDTO) {
        InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
        invCountHeaderDTO.setCountNumber(workFlowEventDTO.getBusinessKey());
        InvCountHeader invCountHeader = invCountHeaderRepository.selectOne(invCountHeaderDTO);
        invCountHeader.setCountStatus(workFlowEventDTO.getDocStatus());
        invCountHeader.setWorkflowId(workFlowEventDTO.getWorkflowId());
        UserVO userSelf = getUserSelf();

        invCountHeader.setWorkflowId(workFlowEventDTO.getWorkflowId());
        if(workFlowEventDTO.getDocStatus().equals("APPROVED")){
            invCountHeader.setApprovedTime(workFlowEventDTO.getApprovedTime());
        }

        if(workFlowEventDTO.getDocStatus().equals("PROCESSING")){
            Long id = userSelf.getId();
            invCountHeader.setSupervisorIds(String.valueOf(id));
        }

        invCountHeaderRepository.updateByPrimaryKeySelective(invCountHeader);
    }

    public void setCreateLineToHeader(InvCountHeaderDTO invCountHeaderDTO,
                                List<InvStockDTO> stocks)
    {
        List<InvCountLineDTO> saveLine = new ArrayList<>();
        Integer number = 1;
        for (InvStockDTO stock : stocks) {
            InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
            invCountLineDTO.setTenantId(stock.getTenantId());
            invCountLineDTO.setLineNumber(number);
            invCountLineDTO.setWarehouseId(stock.getWarehouseId());

            invCountLineDTO.setMaterialId(stock.getMaterialId());
            if(stock.getBatchId() != null){
                invCountLineDTO.setBatchId(stock.getBatchId());
            }

            invCountLineDTO.setCountHeaderId(invCountHeaderDTO.getCountHeaderId());
            invCountLineDTO.setUnitCode(stock.getUnitCode());
            invCountLineDTO.setMaterialCode(stock.getMaterialCode());

            //available qty
            invCountLineDTO.setSnapshotUnitQty(stock.getSummary());
            invCountLineDTO.setCounterIds(invCountHeaderDTO.getCounterIds());
            saveLine.add(invCountLineDTO);
            number++;
        }
        invCountHeaderDTO.setCountOrderLineList(saveLine);
    }


    public Map<Long, InvCountHeader> selectByIds(List<InvCountHeaderDTO> headerDTOS)
    {
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


    public void verificationStatus(String statusReq, Map<String, String> lovStatus)
    {
        if(statusReq.isEmpty()) {
            throw new CommonException(Constants.ERROR_CODE_MISMATCH);
        }
        String s = lovStatus.get(statusReq);
        if(s.isEmpty())
        {
            throw new CommonException(Constants.ERROR_CODE_MISMATCH, statusReq);
        }
    }


    public Map<String, Map<String, String>> lovStatus(){
        Map<String, Map<String, String>> lovMap = new HashMap<>();
        List<LovValueDTO> status = lovAdapter.queryLovValue(Constants.LOV_STATUS, BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> dimension = lovAdapter.queryLovValue(Constants.LOV_DIMENSION, BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> countMode = lovAdapter.queryLovValue(Constants.LOV_COUNT_MODE, BaseConstants.DEFAULT_TENANT_ID);
        List<LovValueDTO> type = lovAdapter.queryLovValue(Constants.LOV_COUNT_TYPE, BaseConstants.DEFAULT_TENANT_ID);
        Map<String, String> statusMap = new HashMap<>();
        for (LovValueDTO lovValueDTO : status) {
            statusMap.put(lovValueDTO.getValue(), lovValueDTO.getMeaning());
        }
        lovMap.put(Constants.LOV_STATUS, statusMap);
        Map<String, String> dimensionMap = new HashMap<>();
        for (LovValueDTO lovValueDTO : dimension) {
            dimensionMap.put(lovValueDTO.getValue(), lovValueDTO.getMeaning());
        }
        lovMap.put(Constants.LOV_DIMENSION, dimensionMap);
        Map<String, String> countMap = new HashMap<>();
        for (LovValueDTO lovValueDTO : countMode) {
            countMap.put(lovValueDTO.getValue(), lovValueDTO.getMeaning());
        }
        lovMap.put(Constants.LOV_COUNT_MODE, countMap);
        Map<String, String> typeMap = new HashMap<>();
        for (LovValueDTO lovValueDTO : type) {
            typeMap.put(lovValueDTO.getValue(), lovValueDTO.getMeaning());
        }
        lovMap.put(Constants.LOV_COUNT_TYPE, typeMap);
        return lovMap;
    }

    public void updateVerification(List<InvCountHeaderDTO> headerDTOS)
    {
        if(headerDTOS == null || headerDTOS.isEmpty()) {
            return;
        }
        validList(headerDTOS, InvCountHeader.UpdateCheck.class);
        //get query header and mapping according to the id
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);

        Long userId = DetailsHelper.getUserDetails().getUserId();

        List<String> statuses = new ArrayList<>();
        statuses.add("DRAFT");
        statuses.add("INCOUNTING");
        statuses.add("REJECTED");
        statuses.add("WITHDRAWN");

        Map<Long, InvWarehouse> invWarehouseMap = invWarehouseService.getFromOrders(headerDTOS);

        Map<Long, Map<Long, InvCountLineDTO>> lineMap = mappingLine(headerDTOS);

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();

            if(headerDTO.getCompanyId() == null){
                headerDTO.setCompanyId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCompanyId());
            }
            if(headerDTO.getWarehouseId() == null){
                headerDTO.setWarehouseId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getWarehouseId());
            }


            if(!statuses.contains(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus())) {
                errMsg.append("Only draft, in counting, rejected, and withdrawn status can be modified.");
            }

            if(headerDTO.getCountStatus() != null){
                errMsg.append("The status cannot be allowed to change");
            }
            if(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus().equals("DRAFT")
                    && !Objects.equals(invCountHeaderMap
                    .get(headerDTO.getCountHeaderId()).getCreatedBy(), userId)) {
                errMsg.append("Document in draft status can only be modified by the document creator.");
            }

            if(!invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus().equals("DRAFT")
                    && statuses.contains(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus()))
            {
                String[] split = invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSupervisorIds().split(",");
                List<String> listSupervisor = Arrays.asList(split);
                String[] split1 = invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCounterIds().split(",");
                List<String> listCounter = Arrays.asList(split1);

                if(invWarehouseMap.get(headerDTO.getWarehouseId()).getIsWmsWarehouse() == 1
                 && !listSupervisor.contains(userId.toString())) {
                    errMsg.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
                }
                if(!listCounter.contains(userId.toString()) && !listSupervisor.contains(userId.toString()) &&
                 !Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userId)) {
                    errMsg.append("Only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn.");
                }

            }

            if(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus().equals("DRAFT")
                    && statuses.contains(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus())
                    && !headerDTO.getReason().isEmpty()){
                errMsg.append("Only the document in status of in counting, rejected, withdrawn, that can change or add the reason.");
            }

            if(headerDTO.getSnapshotMaterialIds() == null){
                headerDTO.setSnapshotMaterialIds(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSnapshotMaterialIds());
            }
            if(headerDTO.getSnapshotBatchIds() == null){
                headerDTO.setSnapshotBatchIds(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getSnapshotBatchIds());
            }

            List<InvCountLineDTO> invCountLineDTOList = headerDTO.getCountOrderLineList();

            if(headerDTO.getCountOrderLineList() != null){
                for (InvCountLineDTO invCountLineDTO : invCountLineDTOList) {
                    if(invCountLineDTO.getTenantId() == null){
                        invCountLineDTO.setTenantId(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getTenantId());
                    }
                    if(invCountLineDTO.getCountHeaderId() == null){
                        invCountLineDTO.setCountHeaderId(headerDTO.getCountHeaderId());
                    }

                    if(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus().equals("INCOUNTING")) {
                        if(invCountLineDTO.getUnitQty() != null){
                            BigDecimal subtract = invCountLineDTO.getUnitQty().subtract(lineMap.get(headerDTO.getCountHeaderId()).get(invCountLineDTO.getCountLineId()).getSnapshotUnitQty());
                            invCountLineDTO.setUnitDiffQty(subtract);
                        }
                    }
                }

                validList(headerDTO.getCountOrderLineList(), InvCountLine.UpdateCheck.class);
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }
    }


    public void insertVerification(List<InvCountHeaderDTO> headerDTOS)
    {
        if(headerDTOS == null || headerDTOS.isEmpty()) return;
        validList(headerDTOS, InvCountHeader.CreateCheck.class);

        Map<Long, IamCompany> iamCompanyMap = iamCompanyService.byIdsFromHeader(headerDTOS);
        Map<Long, InvWarehouse> invWarehouseMap = invWarehouseService.getFromOrders(headerDTOS);

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();
            if(iamCompanyMap.get(headerDTO.getCompanyId()) == null) {
                errMsg.append("Company cannot be found.");
            }

            if(invWarehouseMap.get(headerDTO.getWarehouseId()) == null) {
                errMsg.append("Warehouse cannot be found.");
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }
    }
}


