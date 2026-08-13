package me.zhengjie.agent.tool.input.formdraft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/** 新增客户档案和首单的强类型草稿字段。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public class CustomerWithOrderDraftInput {
    private CustomerInput customer;
    private CustomerOrderDraftInput order;
    public CustomerInput getCustomer() { return customer; } public void setCustomer(CustomerInput v) { customer = v; }
    public CustomerOrderDraftInput getOrder() { return order; } public void setOrder(CustomerOrderDraftInput v) { order = v; }

    /** 客户档案字段。 */
    public static class CustomerInput {
        private String customerCode; private String customerName; private String phone; private Integer gestationalWeek;
        private List<String> allergyTags; private List<Long> excludedDishIds; private List<ExcludedDateInput> excludedDates;
        private String medicalRequirements; private String specialRequirements; private LocalDate productionDate;
        private String remark; private List<AddressInput> addresses;
        public String getCustomerCode() { return customerCode; } public void setCustomerCode(String v) { customerCode = v; }
        public String getCustomerName() { return customerName; } public void setCustomerName(String v) { customerName = v; }
        public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
        public Integer getGestationalWeek() { return gestationalWeek; } public void setGestationalWeek(Integer v) { gestationalWeek = v; }
        public List<String> getAllergyTags() { return allergyTags; } public void setAllergyTags(List<String> v) { allergyTags = v; }
        public List<Long> getExcludedDishIds() { return excludedDishIds; } public void setExcludedDishIds(List<Long> v) { excludedDishIds = v; }
        public List<ExcludedDateInput> getExcludedDates() { return excludedDates; } public void setExcludedDates(List<ExcludedDateInput> v) { excludedDates = v; }
        public String getMedicalRequirements() { return medicalRequirements; } public void setMedicalRequirements(String v) { medicalRequirements = v; }
        public String getSpecialRequirements() { return specialRequirements; } public void setSpecialRequirements(String v) { specialRequirements = v; }
        public LocalDate getProductionDate() { return productionDate; } public void setProductionDate(LocalDate v) { productionDate = v; }
        public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
        public List<AddressInput> getAddresses() { return addresses; } public void setAddresses(List<AddressInput> v) { addresses = v; }
    }

    /** 客户地址槽位。 */
    public static class AddressInput {
        private String addressType; private String addressDetail; private String contactName; private String contactPhone;
        public String getAddressType() { return addressType; } public void setAddressType(String v) { addressType = v; }
        public String getAddressDetail() { return addressDetail; } public void setAddressDetail(String v) { addressDetail = v; }
        public String getContactName() { return contactName; } public void setContactName(String v) { contactName = v; }
        public String getContactPhone() { return contactPhone; } public void setContactPhone(String v) { contactPhone = v; }
    }

    /** 客户排除日期及餐次。 */
    public static class ExcludedDateInput {
        private LocalDate date; private List<String> mealTypes;
        public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
        public List<String> getMealTypes() { return mealTypes; } public void setMealTypes(List<String> v) { mealTypes = v; }
    }
}
