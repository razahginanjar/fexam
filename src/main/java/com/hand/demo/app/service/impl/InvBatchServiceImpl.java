package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.domain.entity.InvMaterial;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
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
        StringBuilder ids = new StringBuilder();
        for (InvCountHeaderDTO headerDTO : headerDTOS) {
            String snapshotBatchIds = headerDTO.getSnapshotBatchIds();
            if(snapshotBatchIds != null && !snapshotBatchIds.isEmpty()){
                ids.append(snapshotBatchIds).append(",");
            }
        }
        Set<Long> idBatchsLong = new HashSet<>();
        String[] split = ids.toString().split(",(?=\\S|$)");
        for (String s : split) {
            idBatchsLong.add(Long.valueOf(s));
        }
        StringBuilder result = new StringBuilder();
        for (Long l : idBatchsLong) {
            result.append(l).append(",");
        }
        // Remove the trailing comma if it exists
        if (result.length() > 0) {
            result.deleteCharAt(result.length() - 1);
        }
        List<InvBatch> invBatches = invBatchRepository.selectByIds(result.toString());
        Map<Long, InvBatch> invBatchMap = new HashMap<>();
        for (InvBatch invBatch : invBatches) {
            invBatchMap.put(invBatch.getBatchId(), invBatch);
        }
        return invBatchMap;
    }
}

