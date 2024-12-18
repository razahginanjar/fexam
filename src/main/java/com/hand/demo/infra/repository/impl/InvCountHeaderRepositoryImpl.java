package com.hand.demo.infra.repository.impl;

import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.UserDTO;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import com.hand.demo.infra.mapper.InvCountHeaderMapper;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * (InvCountHeader)资源库
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */
@Component
public class InvCountHeaderRepositoryImpl extends BaseRepositoryImpl<InvCountHeader> implements InvCountHeaderRepository {
    @Resource
    private InvCountHeaderMapper invCountHeaderMapper;

    @Override
    public List<InvCountHeaderDTO> selectList(InvCountHeaderDTO invCountHeader) {
        List<InvCountHeaderDTO> invCountHeaderDTOS = invCountHeaderMapper.selectList(invCountHeader);
        for (InvCountHeaderDTO invCountHeaderDTO : invCountHeaderDTOS) {
            String[] superSplit = invCountHeaderDTO.getSupervisorIds().split(",");
            List<UserDTO> superUsers = new ArrayList<>();
            for (String s : superSplit) {
                UserDTO userDTO = new UserDTO();
                userDTO.setUserId(Long.parseLong(s));
                superUsers.add(userDTO);
            }
            String[] counterSplit = invCountHeaderDTO.getCounterIds().split(",");
            List<UserDTO> counterUsers = new ArrayList<>();
            for (String s : counterSplit) {
                UserDTO userDTO = new UserDTO();
                userDTO.setUserId(Long.parseLong(s));
                counterUsers.add(userDTO);
            }
            invCountHeaderDTO.setCounterList(counterUsers);
            invCountHeaderDTO.setSupervisorList(superUsers);
        }
        return invCountHeaderDTOS;
    }

    @Override
    public InvCountHeaderDTO selectByPrimary(Long countHeaderId) {
        InvCountHeaderDTO invCountHeader = new InvCountHeaderDTO();
        invCountHeader.setCountHeaderId(countHeaderId);
        List<InvCountHeaderDTO> invCountHeaders = selectList(invCountHeader);
        if (invCountHeaders.size() == 0) {
            return null;
        }
        return invCountHeaders.get(0);
    }

}

