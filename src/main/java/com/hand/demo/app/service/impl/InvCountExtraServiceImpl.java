package com.hand.demo.app.service.impl;

import io.choerodon.core.domain.Page;
import io.choerodon.mybatis.pagehelper.PageHelper;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import com.hand.demo.app.service.InvCountExtraService;
import org.springframework.stereotype.Service;
import com.hand.demo.domain.entity.InvCountExtra;
import com.hand.demo.domain.repository.InvCountExtraRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvCountExtra)应用服务
 *
 * @author razah
 * @since 2024-12-17 10:11:51
 */
@Service
public class InvCountExtraServiceImpl implements InvCountExtraService {
    @Autowired
    private InvCountExtraRepository invCountExtraRepository;

    @Override
    public Page<InvCountExtra> selectList(PageRequest pageRequest, InvCountExtra invCountExtra) {
        return PageHelper.doPageAndSort(pageRequest, () -> invCountExtraRepository.selectList(invCountExtra));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void saveData(List<InvCountExtra> invCountExtras) {
        List<InvCountExtra> insertList = invCountExtras.stream().filter(line -> line.getExtraInfoId() == null).collect(Collectors.toList());
        List<InvCountExtra> updateList = invCountExtras.stream().filter(line -> line.getExtraInfoId() != null).collect(Collectors.toList());
        invCountExtraRepository.batchInsertSelective(insertList);
        invCountExtraRepository.batchUpdateByPrimaryKeySelective(updateList);
    }
}

