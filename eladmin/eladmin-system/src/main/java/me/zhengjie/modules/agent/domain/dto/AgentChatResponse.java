package me.zhengjie.modules.agent.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台展示的聊天诊断结果。
 */
@Data
public class AgentChatResponse {

    private String requestId;

    private String sessionId;

    private String status;

    private String assistantMessage;

    private DiagnosisSlots slots;

    private AgentDiagnosisResponse diagnosisResult;

    private List<String> quickReplies = new ArrayList<>();
}
