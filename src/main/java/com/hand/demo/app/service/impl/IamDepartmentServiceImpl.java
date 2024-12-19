package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.domain.entity.InvWarehouse;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.IamDepartmentService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.IamDepartment;
import com.hand.demo.domain.repository.IamDepartmentRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (IamDepartment)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:55:45
 */
@Service
public class IamDepartmentServiceImpl implements IamDepartmentService {
    @Autowired
    private IamDepartmentRepository iamDepartmentRepository;

    @Override
    public Page<IamDepartment> selectList(PageRequest pageRequest, IamDepartment iamDepartment) {
        return PageHelper.doPageAndSort(pageRequest, () -> iamDepartmentRepository.selectList(iamDepartment));
    }

    @Override
    public void saveData(List<IamDepartment> iamDepartments) {
        List<IamDepartment> insertList = iamDepartments.stream().filter(line -> line.getDepartmentId() == null).collect(Collectors.toList());
        List<IamDepartment> updateList = iamDepartments.stream().filter(line -> line.getDepartmentId() != null).collect(Collectors.toList());
        iamDepartmentRepository.batchInsertSelective(insertList);
        iamDepartmentRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public Map<Long, IamDepartment> getFromHeaders(List<InvCountHeaderDTO> headerDTOS) {
        Set<String> departementIds = headerDTOS.stream()
                .map(header ->
                    header.getDepartmentId().toString()
                )
                .collect(Collectors.toSet());
        String departement = String.join(",", departementIds);
        List<IamDepartment> invWarehouses = iamDepartmentRepository.selectByIds(departement);
        Map<Long, IamDepartment> invWarehouseMap = new HashMap<>();
        for (IamDepartment iamDepartment : invWarehouses) {
            invWarehouseMap.put(iamDepartment.getDepartmentId(), iamDepartment);
        }
        return invWarehouseMap;
    }
}

