package me.zhengjie.modules.agent.formdraft.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** 新增客户档案及首单的类型化草稿。 */
@Data
public class CustomerWithOrderDraftPayload implements Serializable {
    /** 客户档案字段。 */
    private CustomerDraft customer;
    /** 首单字段。 */
    private CustomerOrderDraftPayload order;

    /** 客户档案草稿字段。 */
    @Data
    public static class CustomerDraft implements Serializable {
        /** 手动客户编号，可空。 */
        private String customerCode;
        /** 客户姓名。 */
        private String customerName;
        /** 客户手机号。 */
        private String phone;
        /** 孕周。 */
        private Integer gestationalWeek;
        /** 过敏标签。 */
        private List<String> allergyTags;
        /** 排除菜品 ID。 */
        private List<Long> excludedDishIds;
        /** 排除配送日期及餐次。 */
        private List<ExcludedDateDraft> excludedDates;
        /** 医嘱要求。 */
        private String medicalRequirements;
        /** 特殊要求。 */
        private String specialRequirements;
        /** 生产日期。 */
        private LocalDate productionDate;
        /** 客户备注。 */
        private String remark;
        /** 默认、工作日和周末地址槽位。 */
        private List<AddressDraft> addresses;
    }

    /** 客户地址草稿。 */
    @Data
    public static class AddressDraft implements Serializable {
        /** 地址槽位：DEFAULT、WORKDAY 或 WEEKEND。 */
        private String addressType;
        /** 详细地址。 */
        private String addressDetail;
        /** 联系人。 */
        private String contactName;
        /** 联系电话。 */
        private String contactPhone;
    }

    /** 客户排除日期草稿。 */
    @Data
    public static class ExcludedDateDraft implements Serializable {
        /** 排除日期。 */
        private LocalDate date;
        /** 排除餐次。 */
        private List<String> mealTypes;
    }
}
