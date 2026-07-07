package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

/**
 * 智能排查客服反馈提交响应。
 */
@Data
public class AgentDiagnosisFeedbackResponse {

    private Long id;

    private String status;

    private String message;
}
