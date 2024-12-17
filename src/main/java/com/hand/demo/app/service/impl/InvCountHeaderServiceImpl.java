package com.hand.demo.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.domain.entity.*;
import com.hand.demo.domain.repository.*;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import lombok.extern.slf4j.Slf4j;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.boot.platform.lov.adapter.LovAdapter;
import org.hzero.boot.platform.lov.dto.LovValueDTO;
import org.hzero.core.base.BaseConstants;
import org.json.JSONException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountHeaderService;
import org.springframework.stereotype.Service;
import spire.random.Const;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */
@Service
@Slf4j
public class InvCountHeaderServiceImpl implements InvCountHeaderService {
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @Autowired
    private CodeRuleBuilder codeRuleBuilder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IamCompanyRepository iamCompanyRepository;

    @Autowired
    private LovAdapter lovAdapter;

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

    @Override
    public Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeader) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountHeaderRepository.selectList(invCountHeader));
    }

    @Override
    public List<InvCountHeaderDTO> saveData(List<InvCountHeaderDTO> invCountHeaders) {
        if(invCountHeaders.isEmpty())
        {
            return null;
        }
        List<InvCountHeaderDTO> insertList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaders.stream().filter(line -> line.getCountHeaderId() != null).collect(Collectors.toList());
        insertList.forEach(
                invCountHeader -> {
                    Map<String, String> args = new HashMap<>();
                    args.put("customSegment1", "Counting");
                    args.put("customSegment2", invCountHeader.getTenantId().toString());
                    String s = codeRuleBuilder.generateCode(Constants.RULE_BUILDER_CODE, args);
                    invCountHeader.setCountNumber(s);
                    invCountHeader.setCountStatus("DRAFT");
                }
        );
        invCountHeaderRepository.batchInsertSelective(new ArrayList<>(insertList));
        invCountHeaderRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateList));
        return invCountHeaders;
    }

    @Override
    public InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS) {
        if(invCountHeaderDTOS.isEmpty())
        {
            return new InvCountInfoDTO();
        }

        List<InvCountHeaderDTO> insertList = invCountHeaderDTOS.stream().filter(header -> header.getCountHeaderId() == null).collect(Collectors.toList());
        List<InvCountHeaderDTO> updateList = invCountHeaderDTOS.stream().filter(header -> header.getCountHeaderId() != null).collect(Collectors.toList());

        List<InvCountHeaderDTO> invCountHeaderDTOS1 = insertVerification(insertList);

        Map<String, Map<String, String>> lovMap = lovStatus();
        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            verificationStatus(invCountHeaderDTO.getCountStatus(), lovMap.get(Constants.LOV_STATUS));
        }

        List<InvCountHeaderDTO> invCountHeaderDTOS2 = updateVerification(updateList);

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
        try{
            InvCountHeader invCountHeader =
                    invCountHeaderRepository.selectByPrimary(countHeaderId);
            InvCountHeaderDTO invCountHeaderDTO = new InvCountHeaderDTO();
            if(invCountHeader == null)
            {
                throw new CommonException(Constants.ERROR_ORDER_NOT_FOUND);
            }
            BeanUtils.copyProperties(invCountHeader, invCountHeaderDTO);

            List<InvMaterial> invMaterials = invMaterialRepository.selectByIds(invCountHeader.getSnapshotMaterialIds());
            List<String> materials = new ArrayList<>();

            for (InvMaterial invMaterial : invMaterials) {
                Map<String, String> mapMaterials = new HashMap<>();
                mapMaterials.put("id", String.valueOf(invMaterial.getMaterialId()));
                mapMaterials.put("code", invMaterial.getMaterialCode());
                String s = objectMapper.writeValueAsString(mapMaterials);
                materials.add(s);
            }

            List<InvBatch> invBatches = invBatchRepository.selectByIds(invCountHeader.getSnapshotBatchIds());
            List<String> batches = new ArrayList<>();
            for (InvBatch invBatch : invBatches) {
                Map<String, String> mapBatch = new HashMap<>();
                mapBatch.put("id", String.valueOf(invBatch.getBatchId()));
                mapBatch.put("code", invBatch.getBatchCode());
                String s = objectMapper.writeValueAsString(mapBatch);
                batches.add(s);
            }
            invCountHeaderDTO.setSnapshotBatchList(batches);
            invCountHeaderDTO.setSnapshotMaterialList(materials);
            List<InvCountLine> invCountLines = invCountLineRepository.selectList(new InvCountLine().setCountHeaderId(invCountHeader.getCountHeaderId()));
            List<InvCountLineDTO> collect = invCountLines.stream().map(invCountLine ->
            {
                InvCountLineDTO invCountLineDTO = new InvCountLineDTO();
                BeanUtils.copyProperties(invCountLine, invCountLineDTO);
                return invCountLineDTO;
            }).collect(Collectors.toList());
            InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(invCountHeader.getWarehouseId());
            invCountHeaderDTO.setWMSWarehouse(invWarehouse.getIsWmsWarehouse() == 1);
            invCountHeaderDTO.setInvCountLineDTOList(collect);
            return invCountHeaderDTO;
        }catch (JsonProcessingException exception)
        {
            log.error(exception.getMessage());
            throw new CommonException("Failed to parse to string, : {}", exception.getMessage());
        }
    }

    public void validateLOVs(StringBuilder errMsg,
                             InvCountHeaderDTO invCountHeaderDTO,
                             Map<String, Map<String, String>> mapLov) {
        Map<String, String> statusMap = mapLov.get(Constants.LOV_STATUS);
        Map<String, String> typeMap = mapLov.get(Constants.LOV_COUNT_TYPE);
        Map<String, String> countModeMap = mapLov.get(Constants.LOV_COUNT_MODE);
        Map<String, String> dimensionMap = mapLov.get(Constants.LOV_DIMENSION);
        if(!statusMap.containsKey(invCountHeaderDTO.getCountStatus()))
        {
            errMsg.append("Status Doesn't exists in Lov");
        }
        if(!typeMap.containsKey(invCountHeaderDTO.getCountType()))
        {
            errMsg.append("Type Doesn't exists in Lov");
        }
        if(!countModeMap.containsKey(invCountHeaderDTO.getCountMode()))
        {
            errMsg.append("Mode Doesn't exists in Lov");
        }
        if(!dimensionMap.containsKey(invCountHeaderDTO.getCountDimension()))
        {
            errMsg.append("Dimension Doesn't exists in Lov");
        }
    }

    public void validateComDepaWare(InvCountHeaderDTO invCountHeaderDTO, StringBuilder errMsg) {
        InvWarehouse invWarehouse = invWarehouseRepository.selectByPrimary(invCountHeaderDTO.getWarehouseId());
        IamCompany iamCompany = iamCompanyRepository.selectByPrimary(invCountHeaderDTO.getCompanyId());
        IamDepartment iamDepartment = iamDepartmentRepository.selectByPrimary(invCountHeaderDTO.getDepartmentId());
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


    @Override
    public InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOS) {
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);
        Map<String, Map<String, String>> stringMapMap = lovStatus();
        CustomUserDetails userDetails = DetailsHelper.getUserDetails();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();
            if(!invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCountStatus().equals("DRAFT"))
            {
                errMsg.append("Only draft status can execute");
            }
            if(!Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userDetails.getUserId()))
            {
                errMsg.append("Only the document creator can execute");
            }
            validateLOVs(errMsg, headerDTO, stringMapMap);
            validateComDepaWare(headerDTO, errMsg);


        }


        Requery the database based on the input document ID
        For(invCountHeader : invCountHeaders) {
        // a. document status validation: Only draft status can execute
        // b. current login user validation: Only the document creator can execute
        // c. value set validation
        // d. company, department, warehouse validation
        // e. on hand quantity validation
            Query the stock data that the on hand quantity is not 0 according
            to the tenantId + companyId + departmentId + warehouseId +
                    snapshotMaterialIds + snapshotBatchIds on the table header,
            if no data is queried, error message: Unable to query on hand quantity data.
        }


        return null;
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

    public List<InvCountHeaderDTO> updateVerification(List<InvCountHeaderDTO> headerDTOS)
    {
        if(headerDTOS.isEmpty()) {
            return null;
        }
        Map<Long, InvCountHeader> invCountHeaderMap = selectByIds(headerDTOS);

        Long userId = DetailsHelper.getUserDetails().getUserId();
        List<String> statuses = new ArrayList<>();
        statuses.add("DRAFT");
        statuses.add("IN_COUNTING");
        statuses.add("REJECTED");
        statuses.add("WITHDRAWN");
        Set<String> warehouseIds = headerDTOS.stream()
                .map(header -> header.getWarehouseId().toString())
                .collect(Collectors.toSet());
        String warehouses = String.join(",", warehouseIds);
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectByIds(warehouses);
        Map<Long, InvWarehouse> invWarehouseMap = new HashMap<>();
        for (InvWarehouse invWarehouse : invWarehouses) {
            invWarehouseMap.put(invWarehouse.getWarehouseId(), invWarehouse);
        }

        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();
            if(!statuses.contains(headerDTO.getCountStatus())) {
                errMsg.append("Only draft, in counting, rejected, and withdrawn status can be modified.");
            }

            if(headerDTO.getCountStatus().equals("DRAFT") && !Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userId))
            {
                errMsg.append("Document in draft status can only be modified by the document creator.");
            }

            if(!headerDTO.getCountStatus().equals("DRAFT") && statuses.contains(headerDTO.getCountStatus()))
            {
                String[] split = headerDTO.getSupervisorIds().split(",");
                List<String> listSupervisor = Arrays.asList(split);
                String[] split1 = headerDTO.getCounterIds().split(",");
                List<String> listCounter = Arrays.asList(split1);
                if(invWarehouseMap.get(headerDTO.getWarehouseId()).getIsWmsWarehouse() == 1
                 && !listSupervisor.contains(userId.toString())) {
                    errMsg.append("The current warehouse is a WMS warehouse, and only the supervisor is allowed to operate.");
                }
                if(!listCounter.contains(userId.toString()) && !listSupervisor.contains(userId.toString()) &&
                 !Objects.equals(invCountHeaderMap.get(headerDTO.getCountHeaderId()).getCreatedBy(), userId))
                {
                    errMsg.append("Only the document creator, counter, and supervisor can modify the document for the status  of in counting, rejected, withdrawn.");
                }
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }


        return headerDTOS;
    }


    public List<InvCountHeaderDTO> insertVerification(List<InvCountHeaderDTO> headerDTOS)
    {
        if(headerDTOS.isEmpty()) return null;
        Set<String> companyIds = headerDTOS.stream()
                .map(header -> header.getCompanyId().toString())
                .collect(Collectors.toSet());
        String join = String.join(",", companyIds);
        List<IamCompany> iamCompanies = iamCompanyRepository.selectByIds(join);
        Map<Long, IamCompany> iamCompanyMap = new HashMap<>();
        for (IamCompany iamCompany : iamCompanies) {
            iamCompanyMap.put(iamCompany.getCompanyId(), iamCompany);
        }
        Set<String> warehouseIds = headerDTOS.stream()
                .map(header -> header.getWarehouseId().toString())
                .collect(Collectors.toSet());
        String warehouses = String.join(",", warehouseIds);
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectByIds(warehouses);
        Map<Long, InvWarehouse> invWarehouseMap = new HashMap<>();
        for (InvWarehouse invWarehouse : invWarehouses) {
            invWarehouseMap.put(invWarehouse.getWarehouseId(), invWarehouse);
        }
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            StringBuilder errMsg = new StringBuilder();
            if(headerDTO.getCountStatus().isEmpty())
            {
                headerDTO.setCountStatus("DRAFT");
            }
            if(headerDTO.getCounterIds().isEmpty())
            {
                errMsg.append("Counter list id cannot be null");
            }

            if(headerDTO.getSupervisorIds().isEmpty())
            {
                errMsg.append("Supervisor list cannot be null");
            }

            if(iamCompanyMap.get(headerDTO.getCompanyId()) == null)
            {
                errMsg.append("Company cannot be found");
            }

            if(invWarehouseMap.get(headerDTO.getWarehouseId()) == null)
            {
                errMsg.append("Warehouse cannot be found");
            }
            headerDTO.setErrorMsg(errMsg.toString());
        }
        return headerDTOS;
    }
}


