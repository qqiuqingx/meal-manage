package me.zhengjie.modules.customer.profile.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 客户档案详情 DTO
 */
@Data
public class CustomerProfileDetailDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String customerCode;
    private String customerName;
    private String phone;
    private Integer gestationalWeek;
    private List<String> allergyTags;
    private String medicalRequirements;
    private Boolean status;
    private String remark;
    private LocalDate createTime;
    private LocalDate updateTime;

    /**
     * 地址列表
     */
    private List<AddressDto> addresses;

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
}
