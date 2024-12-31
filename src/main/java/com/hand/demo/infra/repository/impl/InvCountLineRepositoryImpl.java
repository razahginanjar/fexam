package com.hand.demo.infra.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hand.demo.api.dto.InvCountHeaderDTO;
import com.hand.demo.api.dto.InvCountLineDTO;
import com.hand.demo.api.dto.UserDTO;
import io.choerodon.core.exception.CommonException;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.boot.apaas.common.userinfo.domain.UserVO;
import org.hzero.boot.apaas.common.userinfo.infra.feign.IamRemoteService;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.mybatis.base.impl.BaseRepositoryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import com.hand.demo.domain.entity.InvCountLine;
import com.hand.demo.domain.repository.InvCountLineRepository;
import com.hand.demo.infra.mapper.InvCountLineMapper;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * (InvCountLine)资源库
 *
 * @author razah
 * @since 2024-12-17 09:56:48
 */
@Component
public class InvCountLineRepositoryImpl extends BaseRepositoryImpl<InvCountLine> implements InvCountLineRepository {
    @Resource
    private InvCountLineMapper invCountLineMapper;

    @Autowired
    private IamRemoteService iamRemoteService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @ProcessCacheValue
    public List<InvCountLineDTO> selectList(InvCountLineDTO invCountLine) {
        List<InvCountLineDTO> invCountLineDTOS = invCountLineMapper.selectList(invCountLine);
        invCountLineDTOS.parallelStream().forEach(invCountLineDTO -> {
            String[] counterSplit = invCountLineDTO.getCounterIds().split(",");
            List<UserDTO> counterUsers = new ArrayList<>();
            for (String s : counterSplit) {
                UserDTO userDTO = new UserDTO();
                userDTO.setUserId(Long.parseLong(s));
                counterUsers.add(userDTO);
            }
            invCountLineDTO.setCounterList(counterUsers);
        });
        return invCountLineDTOS;
    }

    public UserVO getUserSelf(){
        try{
            ResponseEntity<String> selectSelf = iamRemoteService.selectSelf();
            return objectMapper.readValue(selectSelf.getBody(), UserVO.class);
        } catch (JsonProcessingException e) {
            throw new CommonException(e);
        }
    }

    @Override
    public InvCountLineDTO selectByPrimary(Long countLineId) {
        InvCountLineDTO invCountLine = new InvCountLineDTO();
        invCountLine.setCountLineId(countLineId);
        List<InvCountLineDTO> invCountLines = invCountLineMapper.selectList(invCountLine);
        if (invCountLines.size() == 0) {
            return null;
        }
        return invCountLines.get(0);
    }

    @Override
    @ProcessCacheValue
    public List<InvCountLineDTO> selectReport(InvCountLineDTO invCountLine) {
        List<InvCountLineDTO> invCountLineDTOS = invCountLineMapper.selectReport(invCountLine);
        invCountLineDTOS.parallelStream().forEach(invCountLineDTO -> {
            String[] counterSplit = invCountLineDTO.getCounterIds().split(",");
            List<UserDTO> counterUsers = new ArrayList<>();
            for (String s : counterSplit) {
                UserDTO userDTO = new UserDTO();
                userDTO.setUserId(Long.parseLong(s));
                counterUsers.add(userDTO);
            }
            invCountLineDTO.setCounterList(counterUsers);
        });
        return invCountLineDTOS;
    }

}

