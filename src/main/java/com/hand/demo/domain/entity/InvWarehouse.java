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
import javax.persistence.Transient;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * (InvWarehouse)实体类
 *
 * @author razah
 * @since 2024-12-17 09:57:23
 */

@Getter
@Setter
@ApiModel("")
@VersionAudit
@ModifyAudit
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "fexam_inv_warehouse")
public class InvWarehouse extends AuditDomain {
    private static final long serialVersionUID = 772993444868831275L;

    public static final String FIELD_WAREHOUSE_ID = "warehouseId";
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
    public static final String FIELD_IS_WMS_WAREHOUSE = "isWmsWarehouse";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_WAREHOUSE_CODE = "warehouseCode";

    @ApiModelProperty("")
    @Id
    @GeneratedValue
    private Long warehouseId;

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

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Long companyId;

    @ApiModelProperty(value = "")
    private Integer isWmsWarehouse;

    @ApiModelProperty(value = "", required = true)
    @NotNull
    private Long tenantId;

    @ApiModelProperty(value = "")
    private String warehouseCode;

    @Transient
    private List<Long> idsWarehouse;

    @Transient
    private List<Long> tenantIds;

}

