package com.hand.demo.infra.util;

import org.hzero.boot.interfaces.sdk.dto.RequestPayloadDTO;
import org.hzero.boot.interfaces.sdk.dto.ResponsePayloadDTO;
import org.hzero.boot.interfaces.sdk.invoke.InterfaceInvokeSdk;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.util.TokenUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.thymeleaf.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utils
 */
@Service
public class Utils {
    @Autowired
    private InterfaceInvokeSdk interfaceInvokeSdk;

//    private Utils() {}

    public ResponsePayloadDTO invokeTranslation(
            String jsonString, String namespace, String serverCode,
            String interfaceCode, String accessToken, String text
    )
    {
        RequestPayloadDTO requestPayloadDTO = new RequestPayloadDTO();
        Map<String, String> header = new HashMap<>();
        header.put("Authorization", (StringUtils.isEmpty(accessToken) ? TokenUtils.getToken() : accessToken));

        if(Objects.nonNull(jsonString))
        {
            requestPayloadDTO.setPayload(jsonString);
            requestPayloadDTO.setMediaType("application/json");
        }

        Map<String, String> path = new HashMap<>();
        path.put("organizationId", BaseConstants.DEFAULT_TENANT_ID.toString());
        if(Objects.nonNull(text))
        {
            Map<String, String> params = new HashMap<>();
            params.put("text", text);
            requestPayloadDTO.setRequestParamMap(params);
        }
        requestPayloadDTO.setPathVariableMap(path);
        requestPayloadDTO.setHeaderParamMap(header);
        return interfaceInvokeSdk.invoke(namespace, serverCode, interfaceCode, requestPayloadDTO);
    }
}
