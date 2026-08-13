package me.zhengjie.modules.agent.session.domain.dto;

import lombok.Data;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.modules.agent.domain.dto.AgentFormDraftSummaryDto;
import me.zhengjie.modules.agent.domain.dto.AgentUiActionDto;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能排查会话消息详情。
 */
@Data
public class AgentChatMessageDto {

    private Long id;

    private String sessionId;

    private String requestId;

    private String clientMessageId;

    private String role;

    private String content;

    private String status;

    private String conversationStage;

    /** 助手明确声明的待补充条件，从业务快照恢复。 */
    private List<String> missingSlots = new ArrayList<>();

    /** 由主系统按缺失条件生成的受控快捷回复，从业务快照恢复。 */
    private List<String> quickReplies = new ArrayList<>();

    private DiagnosisSlots slots;

    private AgentDiagnosisResponse diagnosisResult;

    private List<Map<String, Object>> toolSummary = new ArrayList<>();

    /** 工具事实生成的业务卡片。 */
    private List<Map<String, Object>> cards = new ArrayList<>();

    /** 会话详情顶层展示描述便利字段，内容同时保留在 businessResult 中。 */
    private List<Map<String, Object>> presentations = new ArrayList<>();

    /** 新统一工具事实摘要。 */
    private List<Map<String, Object>> toolFacts = new ArrayList<>();

    /** 新统一工具调用追踪摘要。 */
    private List<Map<String, Object>> toolTraceSummary = new ArrayList<>();

    /** 从消息快照恢复的脱敏草稿摘要。 */
    private AgentFormDraftSummaryDto formDraftSummary;

    /** 从消息快照恢复的固定 UI 动作。 */
    private List<AgentUiActionDto> uiActions = new ArrayList<>();

    /** 受控业务查询卡片快照，刷新历史会话时用于恢复结构化展示。 */
    private Map<String, Object> businessResult;

    private String createBy;

    private Timestamp createTime;

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

    /** 返回待补充条件，历史消息缺失该字段时返回空列表。 */
    public List<String> getMissingSlots() {
        return missingSlots == null ? new ArrayList<>() : missingSlots;
    }

    /** 设置待补充条件，null 按空列表处理。 */
    public void setMissingSlots(List<String> missingSlots) {
        this.missingSlots = missingSlots == null ? new ArrayList<>() : missingSlots;
    }

    /** 返回受控快捷回复，历史消息缺失该字段时返回空列表。 */
    public List<String> getQuickReplies() {
        return quickReplies == null ? new ArrayList<>() : quickReplies;
    }

    /** 设置受控快捷回复，null 按空列表处理。 */
    public void setQuickReplies(List<String> quickReplies) {
        this.quickReplies = quickReplies == null ? new ArrayList<>() : quickReplies;
    }

    public DiagnosisSlots getSlots() {
        return slots;
    }

    public void setSlots(DiagnosisSlots slots) {
        this.slots = slots;
    }

    public AgentDiagnosisResponse getDiagnosisResult() {
        return diagnosisResult;
    }

    public void setDiagnosisResult(AgentDiagnosisResponse diagnosisResult) {
        this.diagnosisResult = diagnosisResult;
    }

    public List<Map<String, Object>> getToolSummary() {
        return toolSummary;
    }

    public void setToolSummary(List<Map<String, Object>> toolSummary) {
        this.toolSummary = toolSummary;
    }

    public List<Map<String, Object>> getCards() { return cards; }
    public void setCards(List<Map<String, Object>> cards) { this.cards = cards == null ? new ArrayList<>() : cards; }
    /** 返回会话消息的展示描述；历史消息缺失该字段时返回空列表。 */
    public List<Map<String, Object>> getPresentations() {
        if (presentations == null) {
            presentations = new ArrayList<>();
        }
        return presentations;
    }
    /** 设置会话消息的展示描述；null 按空列表处理。 */
    public void setPresentations(List<Map<String, Object>> presentations) {
        this.presentations = presentations == null ? new ArrayList<>() : presentations;
    }
    public List<Map<String, Object>> getToolFacts() { return toolFacts; }
    public void setToolFacts(List<Map<String, Object>> toolFacts) { this.toolFacts = toolFacts == null ? new ArrayList<>() : toolFacts; }
    public List<Map<String, Object>> getToolTraceSummary() { return toolTraceSummary; }
    public void setToolTraceSummary(List<Map<String, Object>> toolTraceSummary) { this.toolTraceSummary = toolTraceSummary == null ? new ArrayList<>() : toolTraceSummary; }

    public Map<String, Object> getBusinessResult() {
        return businessResult;
    }

    public void setBusinessResult(Map<String, Object> businessResult) {
        this.businessResult = businessResult;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }
}
