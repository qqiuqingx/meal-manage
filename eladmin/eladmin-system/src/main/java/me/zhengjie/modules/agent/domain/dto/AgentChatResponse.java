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

    private String sessionId;

    private String status;

    private String assistantMessage;

    private DiagnosisSlots slots;

    private Map<String, String> slotConfidence = new LinkedHashMap<>();

    private List<String> missingSlots = new ArrayList<>();

    private AgentDiagnosisResponse diagnosisResult;

    private List<String> quickReplies = new ArrayList<>();

    private String conversationStage;
}
