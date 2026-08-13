package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台展示的聊天诊断结果。
 */
@Data
public class AgentChatResponse {

    private String requestId;

    /** 服务间聊天契约版本；旧 Agent 响应未提供时保持为空以兼容历史发布。 */
    private String contractVersion;

    private String sessionId;

    private String clientMessageId;
    /** Agent 处理请求时使用的会话版本，供主系统校验后再写入 Patch。 */
    private Long expectedSessionVersion;
    /** Agent 返回的结构化会话 Patch，由主系统以版本条件提交。 */
    private Map<String, Object> conversationPatch;

    private String status;

    private String assistantMessage;

    private DiagnosisSlots slots;

    private AgentDiagnosisResponse diagnosisResult;

    private List<String> quickReplies = new ArrayList<>();

    /** Agent 明确声明的待补充条件；仅使用受控枚举字符串，历史响应缺失时为空数组。 */
    private List<String> missingSlots = new ArrayList<>();

    private String conversationStage;

    /** 可追溯事实，前端直接展示而不解析自然语言。 */
    private List<Map<String, Object>> facts = new ArrayList<>();

    /** 由成功工具事实确定性生成的业务卡片。 */
    private List<Map<String, Object>> cards = new ArrayList<>();

    /** 与业务卡片关联的受控展示描述；主系统只透传和持久化，不解释展示业务。 */
    private List<Map<String, Object>> presentations = new ArrayList<>();

    /** 新统一工具事实摘要。 */
    private List<Map<String, Object>> toolFacts = new ArrayList<>();

    /** 新统一工具调用追踪摘要。 */
    private List<Map<String, Object>> toolTraceSummary = new ArrayList<>();

    /** 部分成功、截断和权限不足等展示告警。 */
    private List<String> warnings = new ArrayList<>();

    /** 是否命中同一轮业务查询缓存。 */
    private boolean cached;

    /** 是否仅返回部分业务查询结果。 */
    private boolean partial;

    /** 主系统时区下的查询时间。 */
    private String queriedAt;

    /** Agent 返回的最近业务查询脱敏摘要。 */
    private Map<String, Object> lastBusinessQueryContext;

    /** Agent 草稿工具成功事实生成的脱敏摘要。 */
    private AgentFormDraftSummaryDto formDraftSummary;

    /** 固定动作类型和最小参数，不接受任意 URL 或 route。 */
    private List<AgentUiActionDto> uiActions = new ArrayList<>();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getContractVersion() {
        return contractVersion;
    }

    public void setContractVersion(String contractVersion) {
        this.contractVersion = contractVersion;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }
    public Long getExpectedSessionVersion() { return expectedSessionVersion; }
    public void setExpectedSessionVersion(Long expectedSessionVersion) { this.expectedSessionVersion = expectedSessionVersion; }
    public Map<String, Object> getConversationPatch() { return conversationPatch; }
    public void setConversationPatch(Map<String, Object> conversationPatch) { this.conversationPatch = conversationPatch; }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
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

    public List<String> getQuickReplies() {
        return quickReplies == null ? new ArrayList<>() : quickReplies;
    }

    public void setQuickReplies(List<String> quickReplies) {
        this.quickReplies = quickReplies == null ? new ArrayList<>() : quickReplies;
    }

    /** 返回 Agent 明确声明的待补充条件，兼容旧响应的空值。 */
    public List<String> getMissingSlots() {
        return missingSlots == null ? new ArrayList<>() : missingSlots;
    }

    /** 设置待补充条件，null 按空数组处理，避免前端分支出现空值。 */
    public void setMissingSlots(List<String> missingSlots) {
        this.missingSlots = missingSlots == null ? new ArrayList<>() : missingSlots;
    }

    public String getConversationStage() {
        return conversationStage;
    }

    public void setConversationStage(String conversationStage) {
        this.conversationStage = conversationStage;
    }

    public List<Map<String, Object>> getFacts() { return facts; }
    public void setFacts(List<Map<String, Object>> facts) { this.facts = facts; }
    public List<Map<String, Object>> getCards() { return cards; }
    public void setCards(List<Map<String, Object>> cards) { this.cards = cards == null ? new ArrayList<>() : cards; }
    /** 返回受控展示描述列表；旧 Agent 响应缺失该字段时返回空列表。 */
    public List<Map<String, Object>> getPresentations() {
        if (presentations == null) {
            presentations = new ArrayList<>();
        }
        return presentations;
    }
    /** 设置受控展示描述列表；null 按空列表处理以兼容旧响应。 */
    public void setPresentations(List<Map<String, Object>> presentations) {
        this.presentations = presentations == null ? new ArrayList<>() : presentations;
    }
    public List<Map<String, Object>> getToolFacts() { return toolFacts; }
    public void setToolFacts(List<Map<String, Object>> toolFacts) { this.toolFacts = toolFacts == null ? new ArrayList<>() : toolFacts; }
    public List<Map<String, Object>> getToolTraceSummary() { return toolTraceSummary; }
    public void setToolTraceSummary(List<Map<String, Object>> toolTraceSummary) { this.toolTraceSummary = toolTraceSummary == null ? new ArrayList<>() : toolTraceSummary; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public boolean isCached() { return cached; }
    public void setCached(boolean cached) { this.cached = cached; }
    public boolean isPartial() { return partial; }
    public void setPartial(boolean partial) { this.partial = partial; }
    public String getQueriedAt() { return queriedAt; }
    public void setQueriedAt(String queriedAt) { this.queriedAt = queriedAt; }
    public Map<String, Object> getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(Map<String, Object> lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public AgentFormDraftSummaryDto getFormDraftSummary() { return formDraftSummary; }
    public void setFormDraftSummary(AgentFormDraftSummaryDto formDraftSummary) { this.formDraftSummary = formDraftSummary; }
    public List<AgentUiActionDto> getUiActions() { return uiActions == null ? new ArrayList<>() : uiActions; }
    public void setUiActions(List<AgentUiActionDto> uiActions) { this.uiActions = uiActions == null ? new ArrayList<>() : uiActions; }
}
