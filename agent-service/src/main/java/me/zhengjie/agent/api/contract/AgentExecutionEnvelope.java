package me.zhengjie.agent.api.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 主系统向 Agent 下发的可信执行信封。
 *
 * <p>该对象仅供受内部令牌保护的服务间调用使用。前端只能提交 {@link ChatMessageRequest}。</p>
 */
public class AgentExecutionEnvelope {

    @NotBlank
    private String contractVersion = "v2";
    @NotNull
    @Valid
    private ChatMessageRequest messageRequest;
    private DiagnosisSlots contextSnapshot;
    @NotNull
    @Size(max = 100)
    private List<@NotBlank @Size(max = 80) String> availableTools = new ArrayList<>();
    private Map<String, Object> lastBusinessQueryContext;
    private Map<String, Object> formDraftContext;
    /** 主系统读取会话快照时的乐观锁版本；Agent 仅回传，不自行提交会话。 */
    @NotNull
    @PositiveOrZero
    private Long sessionVersion;

    public String getContractVersion() { return contractVersion; }
    public void setContractVersion(String contractVersion) { this.contractVersion = contractVersion; }
    public ChatMessageRequest getMessageRequest() { return messageRequest; }
    public void setMessageRequest(ChatMessageRequest messageRequest) { this.messageRequest = messageRequest; }
    public DiagnosisSlots getContextSnapshot() { return contextSnapshot; }
    public void setContextSnapshot(DiagnosisSlots contextSnapshot) { this.contextSnapshot = contextSnapshot; }
    public List<String> getAvailableTools() { return availableTools; }
    public void setAvailableTools(List<String> availableTools) { this.availableTools = availableTools == null ? new ArrayList<>() : availableTools; }
    public Map<String, Object> getLastBusinessQueryContext() { return lastBusinessQueryContext; }
    public void setLastBusinessQueryContext(Map<String, Object> lastBusinessQueryContext) { this.lastBusinessQueryContext = lastBusinessQueryContext; }
    public Map<String, Object> getFormDraftContext() { return formDraftContext; }
    public void setFormDraftContext(Map<String, Object> formDraftContext) { this.formDraftContext = formDraftContext; }
    public Long getSessionVersion() { return sessionVersion; }
    public void setSessionVersion(Long sessionVersion) { this.sessionVersion = sessionVersion; }
}
