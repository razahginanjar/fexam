package com.hand.demo.domain.entity;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hand.demo.infra.constant.Constants;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.choerodon.mybatis.domain.AuditDomain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hzero.boot.platform.lov.annotation.LovValue;

/**
 * (InvCountHeader)实体类
 *
 * @author razah
 * @since 2024-12-17 09:56:33
 */

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
@Table(name = "fexam_inv_count_header")
public class InvCountHeader extends AuditDomain {
    private static final long serialVersionUID = -22314600767775033L;

    public static final String FIELD_COUNT_HEADER_ID = "countHeaderId";
    public static final String FIELD_APPROVED_TIME = "approvedTime";
    public static final String FIELD_ATTRIBUTE1 = "attribute1";
    public static final String FIELD_ATTRIBUTE10 = "attribute10";
    public static final String FIELD_ATTRIBUTE11 = "attribute11";
    public static final String FIELD_ATTRIBUTE12 = "attribute12";
    public static final String FIELD_ATTRIBUTE13 = "attribute13";
    public static final String FIELD_ATTRIBUTE14 = "attribute14";
    public static final String FIELD_ATTRIBUTE15 = "attribute15";
    public static final String FIELD_ATTRIBUTE2 = "attribute2";
    public static final String FIELD_ATTRIBUTE3 = "attribute3";
    public static final String FIELD_ATTRIBUTE4 = "attribute4";
    public static final String FIELD_ATTRIBUTE5 = "attribute5";
    public static final String FIELD_ATTRIBUTE6 = "attribute6";
    public static final String FIELD_ATTRIBUTE7 = "attribute7";
    public static final String FIELD_ATTRIBUTE8 = "attribute8";
    public static final String FIELD_ATTRIBUTE9 = "attribute9";
    public static final String FIELD_ATTRIBUTE_CATEGORY = "attributeCategory";
    public static final String FIELD_COMPANY_ID = "companyId";
    public static final String FIELD_COUNT_DIMENSION = "countDimension";
    public static final String FIELD_COUNT_MODE = "countMode";
    public static final String FIELD_COUNT_NUMBER = "countNumber";
    public static final String FIELD_COUNT_STATUS = "countStatus";
    public static final String FIELD_COUNT_TIME_STR = "countTimeStr";
    public static final String FIELD_COUNT_TYPE = "countType";
    public static final String FIELD_COUNTER_IDS = "counterIds";
    public static final String FIELD_DEL_FLAG = "delFlag";
    public static final String FIELD_DEPARTMENT_ID = "departmentId";
    public static final String FIELD_REASON = "reason";
    public static final String FIELD_RELATED_WMS_ORDER_CODE = "relatedWmsOrderCode";
    public static final String FIELD_REMARK = "remark";
    public static final String FIELD_SNAPSHOT_BATCH_IDS = "snapshotBatchIds";
    public static final String FIELD_SNAPSHOT_MATERIAL_IDS = "snapshotMaterialIds";
    public static final String FIELD_SOURCE_CODE = "sourceCode";
    public static final String FIELD_SOURCE_ID = "sourceId";
    public static final String FIELD_SOURCE_SYSTEM = "sourceSystem";
    public static final String FIELD_SUPERVISOR_IDS = "supervisorIds";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_WAREHOUSE_ID = "warehouseId";
    public static final String FIELD_WORKFLOW_ID = "workflowId";

    @ApiModelProperty("")
    @Id
    @GeneratedValue
    @NotNull(groups = {UpdateCheck.class})
    private Long countHeaderId;

    @ApiModelProperty(value = "")
    private Date approvedTime;

    @ApiModelProperty(value = "")
    private String attribute1;

    @ApiModelProperty(value = "")
    private String attribute10;

    @ApiModelProperty(value = "")
    private String attribute11;

    @ApiModelProperty(value = "")
    private String attribute12;

    @ApiModelProperty(value = "")
    private String attribute13;

    @ApiModelProperty(value = "")
    private String attribute14;

    @ApiModelProperty(value = "")
    private String attribute15;

    @ApiModelProperty(value = "")
    private String attribute2;

    @ApiModelProperty(value = "")
    private String attribute3;

    @ApiModelProperty(value = "")
    private String attribute4;

    @ApiModelProperty(value = "")
    private String attribute5;

    @ApiModelProperty(value = "")
    private String attribute6;

    @ApiModelProperty(value = "")
    private String attribute7;

    @ApiModelProperty(value = "")
    private String attribute8;

    @ApiModelProperty(value = "")
    private String attribute9;

    @ApiModelProperty(value = "")
    private String attributeCategory;

    @ApiModelProperty(value = "")
    @NotNull(groups = {UpdateCheck.class, CreateCheck.class})
    private Long companyId;

    @ApiModelProperty(value = "")
    @LovValue(lovCode = Constants.LOV_DIMENSION, groups = {UpdateCheck.class}, message = "Mismatch value lov dimension")
    private String countDimension;

    @ApiModelProperty(value = "")
    @LovValue(lovCode = Constants.LOV_COUNT_MODE, groups = {UpdateCheck.class}, message = "Mismatch value lov count")
    private String countMode;

    @ApiModelProperty(value = "", required = true)
    @NotBlank(groups = {UpdateCheck.class})
    private String countNumber;

    @ApiModelProperty(value = "", required = true)
    @LovValue(lovCode = Constants.LOV_STATUS, groups = {CreateCheck.class}, message = "Mismatch Status value lov")
    @NotBlank(groups = {CreateCheck.class})
    private String countStatus;

    @ApiModelProperty(value = "")
    private String countTimeStr;

    @ApiModelProperty(value = "")
    @LovValue(lovCode = Constants.LOV_COUNT_TYPE, groups = {UpdateCheck.class}, message = "Mismatch value type lov")
    private String countType;

    @ApiModelProperty(value = "")
    @NotNull(groups = {CreateCheck.class})
    private String counterIds;

    @ApiModelProperty(value = "")
    private Integer delFlag;

    @ApiModelProperty(value = "")
    @NotNull(groups = {CreateCheck.class})
    private Long departmentId;

    @ApiModelProperty(value = "")
    private String reason;

    @ApiModelProperty(value = "")
    private String relatedWmsOrderCode;

    @ApiModelProperty(value = "")
    private String remark;

    @ApiModelProperty(value = "")
    private String snapshotBatchIds;

    @ApiModelProperty(value = "")
    private String snapshotMaterialIds;

    @ApiModelProperty(value = "")
    private String sourceCode;

    @ApiModelProperty(value = "")
    private Long sourceId;

    @ApiModelProperty(value = "")
    private String sourceSystem;

    @ApiModelProperty(value = "")
    @NotNull(groups = {CreateCheck.class})
    private String supervisorIds;

    @ApiModelProperty(value = "", required = true)
    @NotNull(groups = {CreateCheck.class})
    private Long tenantId;

    @ApiModelProperty(value = "")
    @NotNull(groups = {CreateCheck.class})
    private Long warehouseId;

    @ApiModelProperty(value = "")
    private Long workflowId;


    public interface CreateCheck{ }
    public interface UpdateCheck{}
    public interface Execute{}
    public interface ExecuteCheck{}
}

