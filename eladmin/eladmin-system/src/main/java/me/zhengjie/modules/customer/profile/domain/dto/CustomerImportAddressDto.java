package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 客户批量导入的地址槽位草稿。
 *
 * @author qqx
 * @date 2026-09-24
 **/
@Data
public class CustomerImportAddressDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 地址类型：DEFAULT / WORKDAY / WEEKEND
     */
    private String addressType;

    /**
     * 详细送餐地址
     */
    private String addressDetail;

    /**
     * 联系人姓名，取「联系人/姓名」字段；缺失时回填客户编号
     */
    private String contactName;

    /**
     * 联系人电话，取「电话」字段；预览响应按手机号脱敏，写入时使用解析草稿中的原值
     */
    private String contactPhone;
}
