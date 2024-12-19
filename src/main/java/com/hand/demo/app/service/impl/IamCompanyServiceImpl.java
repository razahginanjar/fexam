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
        Set<String> companyIds = headerDTOS.stream()
                .map(header -> header.getCompanyId().toString())
                .collect(Collectors.toSet());
        String join = String.join(",", companyIds);
        List<IamCompany> iamCompanies = iamCompanyRepository.selectByIds(join);
        Map<Long, IamCompany> iamCompanyMap = new HashMap<>();
        for (IamCompany iamCompany : iamCompanies) {
            iamCompanyMap.put(iamCompany.getCompanyId(), iamCompany);
        }
        return iamCompanyMap;
    }
}

