package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.IamCompanyService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.IamCompany;
import com.hand.demo.domain.repository.IamCompanyRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * (IamCompany)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:55:30
 */
@Service
public class IamCompanyServiceImpl implements IamCompanyService {
    @Autowired
    private IamCompanyRepository iamCompanyRepository;

    @Override
    public Page<IamCompany> selectList(PageRequest pageRequest, IamCompany iamCompany) {
        return PageHelper.doPageAndSort(pageRequest, () -> iamCompanyRepository.selectList(iamCompany));
    }

    @Override
    public void saveData(List<IamCompany> iamCompanys) {
        List<IamCompany> insertList = iamCompanys.stream().filter(line -> line.getCompanyId() == null).collect(Collectors.toList());
        List<IamCompany> updateList = iamCompanys.stream().filter(line -> line.getCompanyId() != null).collect(Collectors.toList());
        iamCompanyRepository.batchInsertSelective(insertList);
        iamCompanyRepository.batchUpdateByPrimaryKeySelective(updateList);
    }

    @Override
    public Map<Long, IamCompany> byIdsFromHeader(List<InvCountHeaderDTO> headerDTOS) {
        // Collect unique company IDs from the headerDTOS list.
        // We convert each companyId to a string and collect them in a set to ensure uniqueness.
        Set<String> companyIds = headerDTOS.stream()
                .map(header -> header.getCompanyId().toString())  // Convert each companyId to string
                .collect(Collectors.toSet());  // Collect unique company IDs into a set

        //Join the set of companyIds into a comma-separated string for querying.
        String join = String.join(",", companyIds);

        // Fetch the list of IamCompany entities by the joined company IDs.
        // Assumes that the repository method `selectByIds` handles the SQL query based on the provided IDs.
        List<IamCompany> iamCompanies = iamCompanyRepository.selectByIds(join);

        // Create a map to store the IamCompany objects by their companyId.
        // This will allow for quick lookup by companyId.

        // Return the map of companyId to IamCompany.
        return iamCompanies.stream()
                .collect(Collectors.toMap(IamCompany::getCompanyId, company -> company));
    }
}

