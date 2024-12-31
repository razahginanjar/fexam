package com.hand.demo.infra.mapper;

import com.hand.demo.api.dto.InvStockDTO;
import io.choerodon.mybatis.common.BaseMapper;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:57:11
 */
public interface InvStockMapper extends BaseMapper<InvStock> {
    /**
     * 基础查询
     *
     * @param invStock 查询条件
     * @return 返回值
     */
    List<InvStockDTO> selectList(InvStockDTO invStock);
    List<InvStockDTO> selectAvailabilityQty(InvStockDTO invStockDTO);
    List<InvStockDTO> checkAvailabilityQty(InvStockDTO invStockDTO);
}

