package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.IamCompany;

import java.util.List;
import java.util.Map;

/**
 * (IamCompany)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:55:30
 */
public interface IamCompanyService {

    /**
     * 查询数据
     *
     * @param pageRequest 分页参数
     * @param iamCompanys 查询条件
     * @return 返回值
     */
    Page<IamCompany> selectList(PageRequest pageRequest, IamCompany iamCompanys);

    /**
     * 保存数据
     *
     * @param iamCompanys 数据
     */
    void saveData(List<IamCompany> iamCompanys);
    Map<Long, IamCompany> byIdsFromHeader(List<InvCountHeaderDTO> headerDTOS);
}

