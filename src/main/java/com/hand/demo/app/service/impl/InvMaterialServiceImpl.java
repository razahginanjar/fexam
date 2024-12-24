package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvMaterialService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvMaterial;
import com.hand.demo.domain.repository.InvMaterialRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvMaterial)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:57:00
 */
@Service
public class InvMaterialServiceImpl implements InvMaterialService {
    @Autowired
    private InvMaterialRepository invMaterialRepository;

    @Override
    public Page<InvMaterial> selectList(PageRequest pageRequest, InvMaterial invMaterial) {
        return PageHelper.doPageAndSort(pageRequest, () -> invMaterialRepository.selectList(invMaterial));
    }

    @Override
    public void saveData(List<InvMaterial> invMaterials) {
        List<InvMaterial> insertList = invMaterials.stream().filter(line -> line.getMaterialId() == null).collect(Collectors.toList());
        List<InvMaterial> updateList = invMaterials.stream().filter(line -> line.getMaterialId() != null).collect(Collectors.toList());
        invMaterialRepository.batchInsertSelective(insertList);
        invMaterialRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Map<Long, InvMaterial> getFromHeaders(List<InvCountHeaderDTO> headerDTOS) {
        StringBuilder ids = new StringBuilder();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            String snapshotMaterialIds = headerDTO.getSnapshotMaterialIds();
            if(snapshotMaterialIds != null && !snapshotMaterialIds.isEmpty()){
                ids.append(snapshotMaterialIds).append(",");
            }
        }
        Set<Long> idsLong = new HashSet<>();
        String[] split = ids.toString().split(",(?=\\S|$)");
        for (String s : split) {
            idsLong.add(Long.valueOf(s));
        }
        StringBuilder result = new StringBuilder();
        for (Long l : idsLong) {
            result.append(l).append(",");
        }
        // Remove the trailing comma if it exists
        if (result.length() > 0) {
            result.deleteCharAt(result.length() - 1);
        }
        List<InvMaterial> invMaterials = invMaterialRepository.selectByIds(result.toString());
        Map<Long, InvMaterial> invMaterialMap = new HashMap<>();
        for (InvMaterial invMaterial : invMaterials) {
            invMaterialMap.put(invMaterial.getMaterialId(), invMaterial);
        }
        return invMaterialMap;
    }
}

