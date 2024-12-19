package com.hand.demo.infra.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.UserDTO;
import io.choerodon.core.exception.CommonException;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.boot.apaas.common.userinfo.domain.UserVO;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private IamRemoteService iamRemoteService;

    @Autowired
    private ObjectMapper objectMapper;

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
        try{
            ResponseEntity<String> selectSelf = iamRemoteService.selectSelf();
            UserVO userVO = objectMapper.readValue(selectSelf.getBody(), UserVO.class);
            InvCountHeaderDTO invCountHeader = new InvCountHeaderDTO();
            invCountHeader.setCountHeaderId(countHeaderId);
            invCountHeader.setTenantId(userVO.getTenantId());
            invCountHeader.setUserId(userVO.getId());
            if(userVO.getTenantAdminFlag() != null){
                invCountHeader.setTenantAdminFlag(userVO.getTenantAdminFlag());
            }
            List<InvCountHeaderDTO> invCountHeaders = selectList(invCountHeader);
            if (invCountHeaders.size() == 0) {
                return null;
            }
            return invCountHeaders.get(0);
        } catch (JsonProcessingException e) {
            throw new CommonException(e.getMessage());
        }
    }

}

