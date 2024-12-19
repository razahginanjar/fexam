package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvWarehouseService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvWarehouse;
import com.hand.demo.domain.repository.InvWarehouseRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (InvWarehouse)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:57:23
 */
@Service
public class InvWarehouseServiceImpl implements InvWarehouseService {
    @Autowired
    private InvWarehouseRepository invWarehouseRepository;

    @Override
    public Page<InvWarehouse> selectList(PageRequest pageRequest, InvWarehouse invWarehouse) {
        return PageHelper.doPageAndSort(pageRequest, () -> invWarehouseRepository.selectList(invWarehouse));
    }

    @Override
    public void saveData(List<InvWarehouse> invWarehouses) {
        List<InvWarehouse> insertList = invWarehouses.stream().filter(line -> line.getWarehouseId() == null).collect(Collectors.toList());
        List<InvWarehouse> updateList = invWarehouses.stream().filter(line -> line.getWarehouseId() != null).collect(Collectors.toList());
        invWarehouseRepository.batchInsertSelective(insertList);
        invWarehouseRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public Map<Long, InvWarehouse> getFromOrders(List<InvCountHeaderDTO> headerDTOS) {
        Set<String> warehouseIds = headerDTOS.stream()
                .map(header -> header.getWarehouseId().toString())
                .collect(Collectors.toSet());
        String warehouses = String.join(",", warehouseIds);
        List<InvWarehouse> invWarehouses = invWarehouseRepository.selectByIds(warehouses);
        Map<Long, InvWarehouse> invWarehouseMap = new HashMap<>();
        for (InvWarehouse invWarehouse : invWarehouses) {
            invWarehouseMap.put(invWarehouse.getWarehouseId(), invWarehouse);
        }
        return invWarehouseMap;
    }
}

