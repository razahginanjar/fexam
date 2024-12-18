package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvStockService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvStock;
import com.hand.demo.domain.repository.InvStockRepository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvStock)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:57:11
 */
@Service
public class InvStockServiceImpl implements InvStockService {
    @Autowired
    private InvStockRepository invStockRepository;

    @Override
    public Page<InvStock> selectList(PageRequest pageRequest, InvStock invStock) {
        return PageHelper.doPageAndSort(pageRequest, () -> invStockRepository.selectList(invStock));
    }

    @Override
    public void saveData(List<InvStock> invStocks) {
        List<InvStock> insertList = invStocks.stream().filter(line -> line.getStockId() == null).collect(Collectors.toList());
        List<InvStock> updateList = invStocks.stream().filter(line -> line.getStockId() != null).collect(Collectors.toList());
        invStockRepository.batchInsertSelective(insertList);
        invStockRepository.batchUpdateByPrimaryKeySelective(updateList);
    }
    @Override
    public List<InvStock> getListStockAccordingHeader(InvCountHeaderDTO invCountHeaderDTO){
        InvStock invStock = new InvStock();
        invStock.setDepartmentId(invCountHeaderDTO.getDepartmentId());
        invStock.setWarehouseId(invCountHeaderDTO.getWarehouseId());
        invStock.setCompanyId(invCountHeaderDTO.getCompanyId());
        invStock.setTenantId(invCountHeaderDTO.getTenantId());
        String snapshotMaterialIds = invCountHeaderDTO.getSnapshotMaterialIds();
        String[] split1 = snapshotMaterialIds.split(",");
        List<Long> materialIds = Arrays.stream(split1).map(Long::parseLong).collect(Collectors.toList());
        invStock.setMaterialsId(materialIds);
        if(invCountHeaderDTO.getCountDimension().equals("LOT"))
        {
            String snapshotBatchIds = invCountHeaderDTO.getSnapshotBatchIds();
            String[] split = snapshotBatchIds.split(",");
            List<Long> batchIds = Arrays.stream(split).map(Long::parseLong).collect(Collectors.toList());
            invStock.setBatchIds(batchIds);
        }
        return invStockRepository.selectList(invStock);
    }
}

