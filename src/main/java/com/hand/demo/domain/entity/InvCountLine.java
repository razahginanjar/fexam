package com.hand.demo.domain.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
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

/**
 * (InvCountLine)实体类
 *
 * @author razah
 * @since 2024-12-17 09:56:48
 */

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
@Table(name = "fexam_inv_count_line")
public class InvCountLine extends AuditDomain {
    private static final long serialVersionUID = -81478882327045579L;

    public static final String FIELD_COUNT_LINE_ID = "countLineId";
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
    public static final String FIELD_BATCH_ID = "batchId";
    public static final String FIELD_COUNT_HEADER_ID = "countHeaderId";
    public static final String FIELD_COUNTER_IDS = "counterIds";
    public static final String FIELD_LINE_NUMBER = "lineNumber";
    public static final String FIELD_MATERIAL_ID = "materialId";
    public static final String FIELD_REMARK = "remark";
    public static final String FIELD_SNAPSHOT_UNIT_QTY = "snapshotUnitQty";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_UNIT_CODE = "unitCode";
    public static final String FIELD_UNIT_DIFF_QTY = "unitDiffQty";
    public static final String FIELD_UNIT_QTY = "unitQty";
    public static final String FIELD_WAREHOUSE_ID = "warehouseId";

    @ApiModelProperty("")
    @Id
    @GeneratedValue
    private Long countLineId;

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
    private Long batchId;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Long countHeaderId;

    @ApiModelProperty(value = "")
    private Object counterIds;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Integer lineNumber;

    @ApiModelProperty(value = "")
    private Long materialId;

    @ApiModelProperty(value = "")
    private String remark;

    @ApiModelProperty(value = "")
    private Object snapshotUnitQty;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Long tenantId;

    @ApiModelProperty(value = "")
    private String unitCode;

    @ApiModelProperty(value = "unit_diff_qty = unit_qty - snapshot_unit_qty")
    private Object unitDiffQty;

    @ApiModelProperty(value = "")
    private Object unitQty;

    @ApiModelProperty(value = "")
    private Long warehouseId;


}

