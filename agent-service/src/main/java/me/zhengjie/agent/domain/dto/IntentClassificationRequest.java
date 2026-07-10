package me.zhengjie.agent.domain.dto;

import me.zhengjie.agent.chat.DiagnosisConversationTurn;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图分类请求，描述当前消息以及可供分类器使用的会话上下文。
 */
public class IntentClassificationRequest {

    private String userMessage;
    private DiagnosisSlots existingSlots;
    private List<DiagnosisConversationTurn> recentTurns = new ArrayList<>();
    private String conversationStage;
    private boolean hasLastDiagnosisResult;
    private String ruleIntentCandidate;
    private Double ruleIntentConfidence;

    public String getUserMessage() { return userMessage; }
    public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
    public DiagnosisSlots getExistingSlots() { return existingSlots; }
    public void setExistingSlots(DiagnosisSlots existingSlots) { this.existingSlots = existingSlots; }
    public List<DiagnosisConversationTurn> getRecentTurns() { return recentTurns; }
    public void setRecentTurns(List<DiagnosisConversationTurn> recentTurns) { this.recentTurns = recentTurns; }
    public String getConversationStage() { return conversationStage; }
    public void setConversationStage(String conversationStage) { this.conversationStage = conversationStage; }
    public boolean isHasLastDiagnosisResult() { return hasLastDiagnosisResult; }
    public void setHasLastDiagnosisResult(boolean hasLastDiagnosisResult) { this.hasLastDiagnosisResult = hasLastDiagnosisResult; }
    public String getRuleIntentCandidate() { return ruleIntentCandidate; }
    public void setRuleIntentCandidate(String ruleIntentCandidate) { this.ruleIntentCandidate = ruleIntentCandidate; }
    public Double getRuleIntentConfidence() { return ruleIntentConfidence; }
    public void setRuleIntentConfidence(Double ruleIntentConfidence) { this.ruleIntentConfidence = ruleIntentConfidence; }
}
