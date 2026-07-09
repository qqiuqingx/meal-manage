package me.zhengjie.modules.agent.session.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import me.zhengjie.base.BaseEntity;

import java.sql.Timestamp;

/**
 * 智能排查聊天会话记录。
 */
@Data
@TableName("agent_chat_session")
public class AgentChatSession extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID。 */
    private String sessionId;

    /** 会话标题。 */
    private String title;

    /** 客服账号。 */
    private String operator;

    /** 当前会话客户ID。 */
    private Long customerId;

    /** 当前会话客户编号。 */
    private String customerCode;

    /** 当前会话排查日期。 */
    private String recordDate;

    /** 当前会话餐次。 */
    private String mealType;

    /** 当前会话阶段。 */
    private String stage;

    /** 最近一次请求ID。 */
    private String lastRequestId;

    /** 最近诊断摘要。 */
    private String lastSummary;

    /** 最近消息时间。 */
    private Timestamp lastMessageTime;

    /** 是否归档。 */
    private Boolean archived;

    /** 乐观锁版本。 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
