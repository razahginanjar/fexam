package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvBatchService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvBatch;
import com.hand.demo.domain.repository.InvBatchRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvBatch)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:55:59
 */
@Service
public class InvBatchServiceImpl implements InvBatchService {
    @Autowired
    private InvBatchRepository invBatchRepository;

    @Override
    public Page<InvBatch> selectList(PageRequest pageRequest, InvBatch invBatch) {
        return PageHelper.doPageAndSort(pageRequest, () -> invBatchRepository.selectList(invBatch));
    }

    @Override
    public void saveData(List<InvBatch> invBatchs) {
        List<InvBatch> insertList = invBatchs.stream().filter(line -> line.getBatchId() == null).collect(Collectors.toList());
        List<InvBatch> updateList = invBatchs.stream().filter(line -> line.getBatchId() != null).collect(Collectors.toList());
        invBatchRepository.batchInsertSelective(insertList);
        invBatchRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Map<Long, InvBatch> getFromHeaders(List<InvCountHeaderDTO> headerDTOS) {
        // Build a string of snapshotBatchIds from the headerDTOS list
        StringBuilder ids = new StringBuilder();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            // Check if snapshotBatchIds is not null or empty before appending it
            String snapshotBatchIds = headerDTO.getSnapshotBatchIds();
            if (StringUtil.isNotBlank(snapshotBatchIds)) {
                ids.append(snapshotBatchIds).append(",");
            }
        }

        // If no batch IDs were found, return an empty map to avoid unnecessary database query
        if (ids.length() == 0) {
            return new HashMap<>();  // Return an empty map if no batch IDs are provided
        }

        // Convert the comma-separated string of IDs into a set of unique Long values
        Set<Long> idBatchsLong = new HashSet<>();
        String[] split = ids.toString().split(",(?=\\S|$)");  // Split by commas ensuring no empty results
        for (String s : split) {
            // Add each batch ID to the set after converting to Long
            idBatchsLong.add(Long.valueOf(s));
        }

        // Prepare the batch IDs for querying by concatenating them into a string
        StringBuilder result = new StringBuilder();
        for (Long l : idBatchsLong) {
            result.append(l).append(",");
        }

        // Remove the trailing comma if it exists, to ensure the correct format for the query
        if (result.length() > 0) {
            result.deleteCharAt(result.length() - 1);  // Remove the last comma
        }

        // Query the database to fetch the InvBatch entities by the concatenated batch IDs
        List<InvBatch> invBatches = invBatchRepository.selectByIds(result.toString());

        // Map the fetched InvBatch entities by their batchId for quick lookup
        Map<Long, InvBatch> invBatchMap = new HashMap<>();
        invBatches.forEach(
                invBatch -> invBatchMap.put(invBatch.getBatchId(), invBatch)
        );
        // Return the map of batchId to InvBatch
        return invBatchMap;
    }
}

