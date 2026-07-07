package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 智能排查动作草稿确认请求。
 */
@Data
public class AgentActionConfirmRequest {

    private String requestId;

    private String sessionId;

    @NotBlank(message = "idempotencyKey不能为空")
    private String idempotencyKey;

    @Valid
    @NotNull(message = "actionDraft不能为空")
    private AgentDiagnosisActionDraftDto actionDraft;

    private Boolean secondConfirmed;

    private String comment;
}
