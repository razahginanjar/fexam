package com.hand.demo.api.controller.v1;

import com.hand.demo.api.dto.*;
import io.choerodon.core.domain.Page;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.mybatis.pagehelper.annotation.SortDefault;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import io.choerodon.mybatis.pagehelper.domain.Sort;
import io.choerodon.swagger.annotation.Permission;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections.CollectionUtils;
import org.hzero.boot.platform.lov.annotation.ProcessLovValue;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.base.BaseController;
import org.hzero.core.cache.ProcessCacheValue;
import org.hzero.core.util.Results;
import org.hzero.mybatis.helper.SecurityTokenHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hand.demo.app.service.InvCountHeaderService;
import com.hand.demo.domain.entity.InvCountHeader;
import com.hand.demo.domain.repository.InvCountHeaderRepository;
import springfox.documentation.annotations.ApiIgnore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * (InvCountHeader)表控制层
 *
 * @author razah
 * @since 2024-12-17 09:56:34
 */

@RestController("invCountHeaderController.v1")
@RequestMapping("/v1/{organizationId}/inv-count-headers")
public class InvCountHeaderController extends BaseController {

    @Autowired
    private InvCountHeaderService invCountHeaderService;
    @Autowired
    private InvCountHeaderRepository invCountHeaderRepository;

    @ApiOperation(value = "列表")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @ProcessCacheValue
    @GetMapping
    public ResponseEntity<Page<InvCountHeaderDTO>> list(InvCountHeaderDTO invCountHeader,
                                                        @PathVariable Long organizationId,
                                                        @ApiIgnore @SortDefault(value = InvCountHeader.FIELD_CREATION_DATE,
            direction = Sort.Direction.DESC) PageRequest pageRequest) {
        Page<InvCountHeaderDTO> list = invCountHeaderService.selectList(pageRequest, invCountHeader);
        return Results.success(list);
    }

    @ApiOperation(value = "明细")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping("/{countHeaderId}/detail")
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @ProcessCacheValue
    public ResponseEntity<InvCountHeaderDTO> detail(@PathVariable Long countHeaderId,
                                                    @PathVariable String organizationId) {
        InvCountHeaderDTO detail = invCountHeaderService.detail(countHeaderId);
        return Results.success(detail);
    }

    @ApiOperation(value = "order save")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping
    public ResponseEntity<InvCountInfoDTO> orderSave(@PathVariable Long organizationId,
                                                             @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        validList(invCountHeaders, InvCountHeader.OrderSaveCheck.class);
        invCountHeaders.forEach(
                invCountHeaderDTO -> {
                    if(CollectionUtils.isNotEmpty(invCountHeaderDTO.getCountOrderLineList())){
                        validList(invCountHeaderDTO.getCountOrderLineList());
                    }
                }
        );
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.orderSave(invCountHeaders);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "order execution")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping(path = "/execute")
    public ResponseEntity<InvCountInfoDTO> orderExecution(@PathVariable Long organizationId,
                                                     @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        SecurityTokenHelper.validTokenIgnoreInsert(invCountHeaders);
        validList(invCountHeaders, InvCountHeader.OrderExecuteCheck.class);
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.orderExecution(invCountHeaders);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "count result sync")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping(path = "/result-sync")
    public ResponseEntity<InvCountHeaderDTO> countResultSync(@PathVariable Long organizationId,
                                                          @RequestBody InvCountHeaderDTO invCountHeader) {
        invCountHeader.setTenantId(organizationId);
        InvCountHeaderDTO invCountHeaderDTO = invCountHeaderService.countResultSync(invCountHeader);
        return Results.success(invCountHeaderDTO);
    }

    @ApiOperation(value = "order submit")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping(path = "/submit")
    public ResponseEntity<InvCountInfoDTO> orderSubmit(@PathVariable Long organizationId,
                                                             @RequestBody List<InvCountHeaderDTO> invCountHeaders) {
        invCountHeaders.forEach(item ->
                {
                    if(item.getTenantId() == null){
                        item.setTenantId(organizationId);
                    }
                }
        );
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.orderSubmit(invCountHeaders);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "callback submit")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @PostMapping(path = "/callback")
    public ResponseEntity<?> callback(@PathVariable Long organizationId,
                                                      @RequestBody WorkFlowEventDTO workFlowEventDTO) {
        invCountHeaderService.callbackHeader(workFlowEventDTO);
        return Results.success("data status is change " + workFlowEventDTO.getDocStatus());
    }



    @ApiOperation(value = "orderRemove")
    @Permission(level = ResourceLevel.ORGANIZATION)
    @DeleteMapping
    public ResponseEntity<?> checkAndRemove(@RequestBody List<InvCountHeaderDTO> invCountHeaders,
                                            @PathVariable String organizationId) {
        SecurityTokenHelper.validToken(invCountHeaders);
        InvCountInfoDTO invCountInfoDTO = invCountHeaderService.checkAndRemove(invCountHeaders);
        return Results.success(invCountInfoDTO);
    }

    @ApiOperation(value = "Report")
    @ProcessLovValue(targetField = BaseConstants.FIELD_BODY)
    @Permission(level = ResourceLevel.ORGANIZATION)
    @GetMapping(
            path = "/report"
    )
    public ResponseEntity<List<InvCountHeaderDTO>> countingOrderReportDs(
            @PathVariable Long organizationId,
            InvCountHeaderDTO invCountHeaderDTO) {
//        List<InvCountHeaderDTO> report = invCountHeaderService.report(invCountHeaderDTO);
        List<InvCountHeaderDTO> report = invCountHeaderRepository.selectReport(invCountHeaderDTO);
        return Results.success(report);

    }
}

