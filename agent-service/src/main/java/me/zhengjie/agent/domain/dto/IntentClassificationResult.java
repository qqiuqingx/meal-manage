package me.zhengjie.agent.domain.dto;

import me.zhengjie.agent.domain.chat.ChatIntent;

/**
 * 受控意图分类结果。intent 为 null 表示分类失败或模型返回了未知值。
 */
public class IntentClassificationResult {

    private ChatIntent intent;
    private double confidence;
    private String reason;
    private boolean fallbackSuggested;

    public IntentClassificationResult() { }

    public IntentClassificationResult(ChatIntent intent, double confidence, String reason, boolean fallbackSuggested) {
        this.intent = intent;
        this.confidence = confidence;
        this.reason = reason;
        this.fallbackSuggested = fallbackSuggested;
    }

    public ChatIntent getIntent() { return intent; }
    public void setIntent(ChatIntent intent) { this.intent = intent; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public boolean isFallbackSuggested() { return fallbackSuggested; }
    public void setFallbackSuggested(boolean fallbackSuggested) { this.fallbackSuggested = fallbackSuggested; }
}
