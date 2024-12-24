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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    public Map<Long, IamDepartment> getFromHeaders(List<InvCountHeaderDTO> headerDTOS) {
        // Collect unique department IDs from the headerDTOS list.
        // Convert each departmentId to a string and collect them in a set to ensure uniqueness.
        Set<String> departmentIds = headerDTOS.stream()
                .map(header -> header.getDepartmentId().toString())  // Convert each departmentId to string
                .collect(Collectors.toSet());  // Collect unique department IDs into a set

        // Join the set of departmentIds into a comma-separated string for querying.
        String departmentIdString = String.join(",", departmentIds);

        // Fetch the list of IamDepartment entities by the joined department IDs.
        // Assumes that the repository method `selectByIds` handles the SQL query based on the provided IDs.
        List<IamDepartment> iamDepartments = iamDepartmentRepository.selectByIds(departmentIdString);

        // Create a map to store the IamDepartment objects by their departmentId.
        // This allows for quick lookup by departmentId.

        // Return the map of departmentId to IamDepartment.
        return iamDepartments.stream()
                .collect(Collectors.toMap(IamDepartment::getDepartmentId, department -> department));
    }
}

