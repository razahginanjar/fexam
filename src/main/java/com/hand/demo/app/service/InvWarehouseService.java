package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvWarehouse;

import java.util.List;
import java.util.Map;

/**
 * (InvWarehouse)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:57:23
 */
public interface InvWarehouseService {

    /**
     * 查询数据
     *
     * @param pageRequest   分页参数
     * @param invWarehouses 查询条件
     * @return 返回值
     */
    Page<InvWarehouse> selectList(PageRequest pageRequest, InvWarehouse invWarehouses);

    /**
     * 保存数据
     *
     * @param invWarehouses 数据
     */
    void saveData(List<InvWarehouse> invWarehouses);
    Map<Long, InvWarehouse> getFromOrders(List<InvCountHeaderDTO> headerDTOS);
}

