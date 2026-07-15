package me.zhengjie.agent.analysis.domain;

import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.BusinessQuestionType;
import java.util.ArrayList;
import java.util.List;

/** 一轮会话理解的版本化结果；模型只输出受控语义，服务端再绑定上下文与能力。 */
public class ConversationUnderstandingResult {
    private String schemaVersion = "1.0";
    private BusinessQuestionType questionType = BusinessQuestionType.BUSINESS_QUERY;
    private BusinessInteractionMode interactionMode = BusinessInteractionMode.NEW_QUERY;
    private String referenceTurn;
    private List<SemanticRequestFrame> frames = new ArrayList<>();
    private List<BusinessAmbiguity> ambiguities = new ArrayList<>();
    private double modelConfidence;
    private String confidenceBucket;
    private boolean requiresClarification;
    private String clarificationCode;
    private String clarificationQuestion;
    private String unknownReason;
    public String getSchemaVersion() { return schemaVersion; } public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public BusinessQuestionType getQuestionType() { return questionType; } public void setQuestionType(BusinessQuestionType questionType) { this.questionType = questionType; }
    public BusinessInteractionMode getInteractionMode() { return interactionMode; } public void setInteractionMode(BusinessInteractionMode interactionMode) { this.interactionMode = interactionMode; }
    public String getReferenceTurn() { return referenceTurn; } public void setReferenceTurn(String referenceTurn) { this.referenceTurn = referenceTurn; }
    public List<SemanticRequestFrame> getFrames() { return frames; } public void setFrames(List<SemanticRequestFrame> frames) { this.frames = frames == null ? new ArrayList<>() : frames; }
    public List<BusinessAmbiguity> getAmbiguities() { return ambiguities; } public void setAmbiguities(List<BusinessAmbiguity> ambiguities) { this.ambiguities = ambiguities == null ? new ArrayList<>() : ambiguities; }
    public double getModelConfidence() { return modelConfidence; } public void setModelConfidence(double modelConfidence) { this.modelConfidence = modelConfidence; }
    public String getConfidenceBucket() { return confidenceBucket; } public void setConfidenceBucket(String confidenceBucket) { this.confidenceBucket = confidenceBucket; }
    public boolean isRequiresClarification() { return requiresClarification; } public void setRequiresClarification(boolean requiresClarification) { this.requiresClarification = requiresClarification; }
    public String getClarificationCode() { return clarificationCode; } public void setClarificationCode(String clarificationCode) { this.clarificationCode = clarificationCode; }
    public String getClarificationQuestion() { return clarificationQuestion; } public void setClarificationQuestion(String clarificationQuestion) { this.clarificationQuestion = clarificationQuestion; }
    public String getUnknownReason() { return unknownReason; } public void setUnknownReason(String unknownReason) { this.unknownReason = unknownReason; }
}
