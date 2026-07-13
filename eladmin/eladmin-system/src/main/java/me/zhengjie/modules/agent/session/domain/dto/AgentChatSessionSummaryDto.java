package me.zhengjie.modules.agent.session.domain.dto;

import lombok.Data;

import java.sql.Timestamp;

/**
 * 智能排查会话摘要。
 */
@Data
public class AgentChatSessionSummaryDto {

    private String sessionId;

    private String title;

    private String operator;

    private Long customerId;

    private String customerCode;

    /** 当前会话订单 ID，供客服快速识别订单焦点。 */
    private Long orderId;

    /** 当前会话订单编号，供客服快速识别订单焦点。 */
    private String orderCode;

    /** 当前会话排餐客户记录 ID。 */
    private Long mealPlanRecordId;

    private String recordDate;

    /** 当前会话受控查询起始日期。 */
    private String queryStartDate;

    /** 当前会话受控查询结束日期。 */
    private String queryEndDate;

    private String mealType;

    private String stage;

    private String lastRequestId;

    private String lastSummary;

    private Timestamp lastMessageTime;

    private Boolean archived;

    private Timestamp createTime;

    private Timestamp updateTime;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    /** 返回当前会话聚焦的订单 ID。 */
    public Long getOrderId() {
        return orderId;
    }

    /** 设置当前会话聚焦的订单 ID。 */
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    /** 返回当前会话聚焦的订单编号。 */
    public String getOrderCode() {
        return orderCode;
    }

    /** 设置当前会话聚焦的订单编号。 */
    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public String getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(String recordDate) {
        this.recordDate = recordDate;
    }

    public String getQueryStartDate() {
        return queryStartDate;
    }

    public void setQueryStartDate(String queryStartDate) {
        this.queryStartDate = queryStartDate;
    }

    public String getQueryEndDate() {
        return queryEndDate;
    }

    public void setQueryEndDate(String queryEndDate) {
        this.queryEndDate = queryEndDate;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getLastRequestId() {
        return lastRequestId;
    }

    public void setLastRequestId(String lastRequestId) {
        this.lastRequestId = lastRequestId;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }

    public Timestamp getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(Timestamp lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public Timestamp getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Timestamp updateTime) {
        this.updateTime = updateTime;
    }
}
