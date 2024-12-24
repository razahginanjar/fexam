package com.hand.demo.app.service.impl;

import com.hand.demo.api.dto.InvCountLineDTO;
import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.hzero.core.cache.ProcessCacheValue;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountLineService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvCountLine)应用服务
 *
 * @author razah
 * @since 2024-12-17 09:56:48
 */
@Service
public class InvCountLineServiceImpl implements InvCountLineService {
    @Autowired
    private InvCountLineRepository invCountLineRepository;

    @Override
    public Page<InvCountLineDTO> selectList(PageRequest pageRequest, InvCountLineDTO invCountLine) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountLineRepository.selectList(invCountLine));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void saveData(List<InvCountLineDTO> invCountLines) {
        List<InvCountLineDTO> insertList = invCountLines.stream().filter(line -> line.getCountLineId() == null).collect(Collectors.toList());
        List<InvCountLineDTO> updateList = invCountLines.stream().filter(line -> line.getCountLineId() != null).collect(Collectors.toList());
        invCountLineRepository.batchInsertSelective(new ArrayList<>(insertList));
        invCountLineRepository.batchUpdateByPrimaryKeySelective(new ArrayList<>(updateList));
    }
}

