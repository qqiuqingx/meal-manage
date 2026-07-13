package me.zhengjie.modules.agent.session.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import me.zhengjie.base.BaseEntity;

/**
 * 智能排查聊天消息记录。
 */
@Data
@TableName("agent_chat_message")
public class AgentChatMessage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID。 */
    private String sessionId;

    /** 请求ID。 */
    private String requestId;

    /** 前端消息幂等ID。 */
    private String clientMessageId;

    /** 消息角色。 */
    private String role;

    /** 消息内容。 */
    private String content;

    /** 聊天响应状态。 */
    private String status;

    /** 会话阶段。 */
    private String conversationStage;

    /** 槽位快照JSON。 */
    private String slotsJson;

    /** 诊断结果JSON。 */
    private String diagnosisResultJson;

    /** 工具调用摘要JSON。 */
    private String toolSummaryJson;

    /** 受控业务查询卡片快照JSON，不保存金额字段或原始工具响应。 */
    private String businessResultJson;

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConversationStage() {
        return conversationStage;
    }

    public void setConversationStage(String conversationStage) {
        this.conversationStage = conversationStage;
    }

    public String getSlotsJson() {
        return slotsJson;
    }

    public void setSlotsJson(String slotsJson) {
        this.slotsJson = slotsJson;
    }

    public String getDiagnosisResultJson() {
        return diagnosisResultJson;
    }

    public void setDiagnosisResultJson(String diagnosisResultJson) {
        this.diagnosisResultJson = diagnosisResultJson;
    }

    public String getToolSummaryJson() {
        return toolSummaryJson;
    }

    public void setToolSummaryJson(String toolSummaryJson) {
        this.toolSummaryJson = toolSummaryJson;
    }

    public String getBusinessResultJson() {
        return businessResultJson;
    }

    public void setBusinessResultJson(String businessResultJson) {
        this.businessResultJson = businessResultJson;
    }
}
