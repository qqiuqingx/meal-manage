package me.zhengjie.agent.tool.input.formdraft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 新增订单草稿字段；排除图片、状态和服务端计算字段。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public class CustomerOrderDraftInput {
    @JsonPropertyDescription("已有客户ID；CREATE_ORDER 必须先通过查询工具唯一确定")
    private Long customerId;
    private String customerCode;
    private Long parentPackageId;
    private Long childPackageId;
    private Integer breakfastCount;
    private Integer lunchDinnerCount;
    private BigDecimal breakfastPrice;
    private BigDecimal lunchDinnerPrice;
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;
    private BigDecimal finalAmount;
    private LocalDateTime dealTime;
    private LocalDateTime firstDeliveryTime;
    private LocalDate startDate;
    private String startMealType;
    private LocalDate endDate;
    private String mealType;
    private String scheduleMode;
    private List<DeliveryDateInput> deliveryDates;
    private String customerSource;
    private Boolean trialConverted;
    private Long trialOrderId;
    private Integer mainDishCount;
    private Integer sideDishCount;
    private Integer vegCount;
    private Integer riceCount;
    private String riceType;
    private Integer soupCount;
    private String remark;
    private List<ReplaceRuleInput> replaceRules;

    public Long getCustomerId() { return customerId; } public void setCustomerId(Long v) { customerId = v; }
    public String getCustomerCode() { return customerCode; } public void setCustomerCode(String v) { customerCode = v; }
    public Long getParentPackageId() { return parentPackageId; } public void setParentPackageId(Long v) { parentPackageId = v; }
    public Long getChildPackageId() { return childPackageId; } public void setChildPackageId(Long v) { childPackageId = v; }
    public Integer getBreakfastCount() { return breakfastCount; } public void setBreakfastCount(Integer v) { breakfastCount = v; }
    public Integer getLunchDinnerCount() { return lunchDinnerCount; } public void setLunchDinnerCount(Integer v) { lunchDinnerCount = v; }
    public BigDecimal getBreakfastPrice() { return breakfastPrice; } public void setBreakfastPrice(BigDecimal v) { breakfastPrice = v; }
    public BigDecimal getLunchDinnerPrice() { return lunchDinnerPrice; } public void setLunchDinnerPrice(BigDecimal v) { lunchDinnerPrice = v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { totalAmount = v; }
    public BigDecimal getDepositAmount() { return depositAmount; } public void setDepositAmount(BigDecimal v) { depositAmount = v; }
    public BigDecimal getFinalAmount() { return finalAmount; } public void setFinalAmount(BigDecimal v) { finalAmount = v; }
    public LocalDateTime getDealTime() { return dealTime; } public void setDealTime(LocalDateTime v) { dealTime = v; }
    public LocalDateTime getFirstDeliveryTime() { return firstDeliveryTime; } public void setFirstDeliveryTime(LocalDateTime v) { firstDeliveryTime = v; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate v) { startDate = v; }
    public String getStartMealType() { return startMealType; } public void setStartMealType(String v) { startMealType = v; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate v) { endDate = v; }
    public String getMealType() { return mealType; } public void setMealType(String v) { mealType = v; }
    public String getScheduleMode() { return scheduleMode; } public void setScheduleMode(String v) { scheduleMode = v; }
    public List<DeliveryDateInput> getDeliveryDates() { return deliveryDates; } public void setDeliveryDates(List<DeliveryDateInput> v) { deliveryDates = v; }
    public String getCustomerSource() { return customerSource; } public void setCustomerSource(String v) { customerSource = v; }
    public Boolean getTrialConverted() { return trialConverted; } public void setTrialConverted(Boolean v) { trialConverted = v; }
    public Long getTrialOrderId() { return trialOrderId; } public void setTrialOrderId(Long v) { trialOrderId = v; }
    public Integer getMainDishCount() { return mainDishCount; } public void setMainDishCount(Integer v) { mainDishCount = v; }
    public Integer getSideDishCount() { return sideDishCount; } public void setSideDishCount(Integer v) { sideDishCount = v; }
    public Integer getVegCount() { return vegCount; } public void setVegCount(Integer v) { vegCount = v; }
    public Integer getRiceCount() { return riceCount; } public void setRiceCount(Integer v) { riceCount = v; }
    public String getRiceType() { return riceType; } public void setRiceType(String v) { riceType = v; }
    public Integer getSoupCount() { return soupCount; } public void setSoupCount(Integer v) { soupCount = v; }
    public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
    public List<ReplaceRuleInput> getReplaceRules() { return replaceRules; } public void setReplaceRules(List<ReplaceRuleInput> v) { replaceRules = v; }

    /** 指定配送日期及餐次。 */
    public static class DeliveryDateInput {
        private LocalDate date; private List<String> mealTypes;
        public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
        public List<String> getMealTypes() { return mealTypes; } public void setMealTypes(List<String> v) { mealTypes = v; }
    }

    /** 订单换菜规则。 */
    public static class ReplaceRuleInput {
        private Long sourceDishId; private Long targetDishId; private Boolean enabled; private String remark;
        public Long getSourceDishId() { return sourceDishId; } public void setSourceDishId(Long v) { sourceDishId = v; }
        public Long getTargetDishId() { return targetDishId; } public void setTargetDishId(Long v) { targetDishId = v; }
        public Boolean getEnabled() { return enabled; } public void setEnabled(Boolean v) { enabled = v; }
        public String getRemark() { return remark; } public void setRemark(String v) { remark = v; }
    }
}
