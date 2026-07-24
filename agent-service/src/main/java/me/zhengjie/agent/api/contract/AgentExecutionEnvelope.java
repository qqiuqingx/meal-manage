package me.zhengjie.agent.api.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.query.domain.ConversationTaskStack;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.query.domain.PendingBusinessQueryContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 主系统向 Agent 下发的可信执行信封。
 *
 * <p>该对象仅供受内部令牌保护的服务间调用使用。前端只能提交 {@link ChatMessageRequest}。</p>
 */
public class AgentExecutionEnvelope {

    @NotBlank
    private String contractVersion = "v2";
    @Valid
    private ChatMessageRequest messageRequest;
    private DiagnosisSlots contextSnapshot;
    private List<String> availableTools = new ArrayList<>();
    private PendingBusinessQueryContext pendingBusinessQueryContext;
    private LastBusinessQueryContext lastBusinessQueryContext;
    private ConversationTaskStack activeTaskStack;

    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String contractVersion) { this.contractVersion = contractVersion; }
    public ChatMessageRequest getMessageRequest() { return messageRequest; }
    public void setMessageRequest(ChatMessageRequest messageRequest) { this.messageRequest = messageRequest; }
    public DiagnosisSlots getContextSnapshot() { return contextSnapshot; }
    public void setContextSnapshot(DiagnosisSlots contextSnapshot) { this.contextSnapshot = contextSnapshot; }
    public List<String> getAvailableTools() { return availableTools; }
    public void setAvailableTools(List<String> availableTools) { this.availableTools = availableTools == null ? new ArrayList<>() : availableTools; }
    public PendingBusinessQueryContext getPendingBusinessQueryContext() { return pendingBusinessQueryContext; }
    public void setPendingBusinessQueryContext(PendingBusinessQueryContext pendingBusinessQueryContext) { this.pendingBusinessQueryContext = pendingBusinessQueryContext; }
    public LastBusinessQueryContext getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(LastBusinessQueryContext lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public ConversationTaskStack getActiveTaskStack() { return activeTaskStack; }
    public void setActiveTaskStack(ConversationTaskStack activeTaskStack) { this.activeTaskStack = activeTaskStack; }
}
