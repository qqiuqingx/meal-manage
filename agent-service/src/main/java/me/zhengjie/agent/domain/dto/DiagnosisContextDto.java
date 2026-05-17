package me.zhengjie.agent.domain.dto;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 诊断编排内部使用的上下文对象，后续可从主系统补充业务数据。
 */
public class DiagnosisContextDto {

    private Long customerId;
    private String customerName;
    private String recordDate;
    private String mealType;
    private Map<String, Object> customerProfile = new HashMap<>();
    private List<Map<String, Object>> orders = new ArrayList<>();
    private Map<String, Object> mealPlan = new HashMap<>();
    private List<Map<String, Object>> customerPlans = new ArrayList<>();

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public Map<String, Object> getCustomerProfile() {
        return customerProfile;
    }

    public void setCustomerProfile(Map<String, Object> customerProfile) {
        this.customerProfile = customerProfile;
    }

    public List<Map<String, Object>> getOrders() {
        return orders;
    }

    public void setOrders(List<Map<String, Object>> orders) {
        this.orders = orders;
    }

    public Map<String, Object> getMealPlan() {
        return mealPlan;
    }

    public void setMealPlan(Map<String, Object> mealPlan) {
        this.mealPlan = mealPlan;
    }

    public List<Map<String, Object>> getCustomerPlans() {
        return customerPlans;
    }

    public void setCustomerPlans(List<Map<String, Object>> customerPlans) {
        this.customerPlans = customerPlans;
    }
}
