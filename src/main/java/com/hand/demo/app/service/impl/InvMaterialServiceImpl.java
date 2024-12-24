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
        // StringBuilder to collect all material IDs from the snapshotMaterialIds field of the headers
        StringBuilder ids = new StringBuilder();

        // Iterate through all headerDTOS to collect the snapshot material IDs
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            String snapshotMaterialIds = headerDTO.getSnapshotMaterialIds();

            // Only add to the ids if snapshotMaterialIds is not null or empty
            if (snapshotMaterialIds != null && !snapshotMaterialIds.isEmpty()) {
                ids.append(snapshotMaterialIds).append(",");
            }
        }

        // If no IDs were collected, return an empty map early (optimization)
        if (ids.length() == 0) {
            return new HashMap<>();
        }

        // Split the collected IDs into individual strings and convert them into a set of longs (to remove duplicates)
        Set<Long> idsLong = new HashSet<>();
        String[] split = ids.toString().split(",(?=\\S|$)");
        for (String s : split) {
            idsLong.add(Long.valueOf(s));  // Convert to Long and add to the set
        }

        // If no IDs were found after processing, return an empty map (early exit optimization)
        if (idsLong.isEmpty()) {
            return new HashMap<>();
        }

        // Convert the set of IDs to a comma-separated string (for SQL query)
        String result = String.join(",", idsLong.stream().map(String::valueOf).collect(Collectors.toSet()));

        // Query the database to fetch InvMaterial entities using the comma-separated string of IDs
        List<InvMaterial> invMaterials = invMaterialRepository.selectByIds(result);

        // Create a map to store the materials with their ID as the key
        Map<Long, InvMaterial> invMaterialMap = new HashMap<>();
        for (InvMaterial invMaterial : invMaterials) {
            invMaterialMap.put(invMaterial.getMaterialId(), invMaterial);
        }

        // Return the map of InvMaterial entities
        return invMaterialMap;
    }
}

