package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 客户档案保存 DTO
 */
@Data
public class CustomerProfileSaveDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 客户编号
     */
    private String customerCode;

    /**
     * 客户姓名
     */
    private String customerName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 孕周
     */
    private Integer gestationalWeek;

    /**
     * 过敏食物标签
     */
    private List<String> allergyTags;

    /**
     * 医嘱要求
     */
    private String medicalRequirements;

    /**
     * 状态
     */
    private Boolean status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 地址列表
     */
    private List<AddressDto> addresses;

    /**
     * 套餐信息
     */
    private PackageInfoDto packageInfo;

    /**
     * 地址 DTO
     */
    @Data
    public static class AddressDto implements Serializable {
        private String addressType;
        private String addressDetail;
        private String contactName;
        private String contactPhone;
    }

    /**
     * 套餐信息 DTO
     */
    @Data
    public static class PackageInfoDto implements Serializable {
        private Long parentPackageId;
        private Long childPackageId;
        private Integer breakfastCount;
        private Integer lunchDinnerCount;
        private Integer totalCount;
        private String startDate;
        private String endDate;
    }
}