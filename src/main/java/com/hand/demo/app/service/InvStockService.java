package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvStockDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvStock;

import java.util.List;

/**
 * (InvStock)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:57:11
 */
public interface InvStockService {

    /**
     * 查询数据
     *
     * @param pageRequest 分页参数
     * @param invStocks   查询条件
     * @return 返回值
     */
    Page<InvStockDTO> selectList(PageRequest pageRequest, InvStockDTO invStocks);

    /**
     * 保存数据
     *
     * @param invStocks 数据
     */
    void saveData(List<InvStock> invStocks);
    List<InvStockDTO> getListStockAccordingHeader(InvCountHeaderDTO invCountHeaderDTO);
    List<InvStockDTO> getSummarizeStock(InvCountHeaderDTO invCountHeaderDTO);

}

