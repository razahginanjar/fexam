package com.hand.demo.app.service;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountInfoDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import com.hand.demo.domain.entity.InvCountHeader;

import java.util.List;

/**
 * (InvCountHeader)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */
public interface InvCountHeaderService {

    /**
     * 查询数据
     *
     * @param pageRequest     分页参数
     * @param invCountHeaders 查询条件
     * @return 返回值
     */
    Page<InvCountHeaderDTO> selectList(PageRequest pageRequest, InvCountHeaderDTO invCountHeaders);

    /**
     * 保存数据
     *
     * @param invCountHeaders 数据
     */
    List<InvCountHeaderDTO> saveData(List<InvCountHeaderDTO> invCountHeaders);
    InvCountInfoDTO manualSaveCheck(List<InvCountHeaderDTO> invCountHeaderDTOS);
    InvCountInfoDTO checkAndRemove(List<InvCountHeaderDTO> invCountHeaderDTOS);
    InvCountHeaderDTO detail (Long countHeaderId);
    InvCountInfoDTO executeCheck(List<InvCountHeaderDTO> headerDTOS);

}

