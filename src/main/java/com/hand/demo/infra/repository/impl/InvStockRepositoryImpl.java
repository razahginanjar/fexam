package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvStockDTO;
import com.hand.demo.domain.repository.InvStockRepository;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.hzero.mybatis.domian.Condition;
import org.hzero.mybatis.util.Sqls;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvStock;
import com.hand.demo.infra.mapper.InvStockMapper;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * (InvStock)资源库
 *
 * @author razah
 * @since 2024-12-17 09:57:11
 */
@Component
public class InvStockRepositoryImpl extends BaseRepositoryImpl<InvStock> implements InvStockRepository {
    @Resource
    private InvStockMapper invStockMapper;

    @Override
    public List<InvStockDTO> selectList(InvStockDTO invStock) {
        return invStockMapper.selectList(invStock);
    }

    @Override
    public InvStock selectByPrimary(Long stockId) {
        InvStockDTO invStock = new InvStockDTO();
        invStock.setStockId(stockId);
        List<InvStockDTO> invStocks = invStockMapper.selectList(invStock);
        if (invStocks.size() == 0) {
            return null;
        }
        return invStocks.get(0);
    }

    @Override
    public List<InvStockDTO> getSummarizeStock(InvStockDTO invStockDTO) {
        return invStockMapper.selectAvailabilityQty(invStockDTO);
    }

    @Override
    public List<InvStock> checkAvailability(InvStockDTO invStock) {
        Sqls sqls = Sqls.custom()
                .andEqualTo(InvStock.FIELD_COMPANY_ID, invStock.getCompanyId())
                .andEqualTo(InvStock.FIELD_TENANT_ID, invStock.getTenantId())
                .andEqualTo(InvStock.FIELD_WAREHOUSE_ID, invStock.getWarehouseId())
                .andEqualTo(InvStock.FIELD_DEPARTMENT_ID, invStock.getDepartmentId())
                .andGreaterThan(InvStock.FIELD_AVAILABLE_QUANTITY, 0);
        if(CollectionUtils.isNotEmpty(invStock.getBatchIds())){
            sqls.andIn(InvStock.FIELD_BATCH_ID, invStock.getBatchIds());
        }

        if(CollectionUtils.isNotEmpty(invStock.getMaterialsId())){
            sqls.andIn(InvStock.FIELD_MATERIAL_ID, invStock.getMaterialsId());
        }

        //        return invStockMapper.checkAvailabilityQty(invStock);
        return selectByCondition(Condition.builder(InvStock.class)
                .andWhere(sqls)
                .build());
    }

}

