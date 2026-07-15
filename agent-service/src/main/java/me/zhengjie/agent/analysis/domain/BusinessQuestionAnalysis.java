package me.zhengjie.agent.analysis.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.BusinessCorrection;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则或模型产生的受控业务问题分析结果。该对象不包含 SQL、URL、字段名或工具名。
 */
public class BusinessQuestionAnalysis {
    private BusinessQuestionType questionType = BusinessQuestionType.BUSINESS_QUERY;
    private List<AgentQueryDomain> domains = new ArrayList<>();
    private AgentEntityReference entities = new AgentEntityReference();
    private AgentQueryFilters filters = new AgentQueryFilters();
    private List<AgentQueryMetric> metrics = new ArrayList<>();
    private List<AgentQueryDimension> dimensions = new ArrayList<>();
    private List<BusinessAmbiguity> ambiguities = new ArrayList<>();
    private List<String> subjects = new ArrayList<>();
    private List<String> relations = new ArrayList<>();
    private List<String> requestedFacts = new ArrayList<>();
    private String operation;
    private List<String> groupBy = new ArrayList<>();
    private BusinessQueryTarget queryTarget;
    private BusinessInteractionMode interactionMode = BusinessInteractionMode.NEW_QUERY;
    private String referenceTurn;
    private MealScope mealScope;
    private BusinessCorrection correction;
    private BusinessTemporalIntent temporal = new BusinessTemporalIntent();
    private double confidence;
    private boolean requiresClarification;
    private String clarificationQuestion;
    @JsonIgnore
    private String source = "RULE";
    @JsonIgnore
    private String fallbackReason;
    @JsonIgnore
    private String semanticCatalogVersion;

    public BusinessQuestionType getQuestionType() { return questionType; }
    public void setQuestionType(BusinessQuestionType questionType) { this.questionType = questionType; }
    public List<AgentQueryDomain> getDomains() { return domains; }
    public void setDomains(List<AgentQueryDomain> domains) { this.domains = domains; }
    public AgentEntityReference getEntities() { return entities; }
    public void setEntities(AgentEntityReference entities) { this.entities = entities; }
    public AgentQueryFilters getFilters() { return filters; }
    public void setFilters(AgentQueryFilters filters) { this.filters = filters; }
    public List<AgentQueryMetric> getMetrics() { return metrics; }
    public void setMetrics(List<AgentQueryMetric> metrics) { this.metrics = metrics; }
    public List<AgentQueryDimension> getDimensions() { return dimensions; }
    public void setDimensions(List<AgentQueryDimension> dimensions) { this.dimensions = dimensions; }
    public List<BusinessAmbiguity> getAmbiguities() { return ambiguities; }
    public void setAmbiguities(List<BusinessAmbiguity> ambiguities) { this.ambiguities = ambiguities; }
    public List<String> getSubjects() { return subjects; }
    public void setSubjects(List<String> subjects) { this.subjects = subjects; }
    public List<String> getRelations() { return relations; }
    public void setRelations(List<String> relations) { this.relations = relations; }
    public List<String> getRequestedFacts() { return requestedFacts; }
    public void setRequestedFacts(List<String> requestedFacts) { this.requestedFacts = requestedFacts; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public List<String> getGroupBy() { return groupBy; }
    public void setGroupBy(List<String> groupBy) { this.groupBy = groupBy; }
    public BusinessQueryTarget getQueryTarget() { return queryTarget; }
    public void setQueryTarget(BusinessQueryTarget queryTarget) { this.queryTarget = queryTarget; }
    public BusinessInteractionMode getInteractionMode() { return interactionMode; }
    public void setInteractionMode(BusinessInteractionMode interactionMode) { this.interactionMode = interactionMode; }
    public String getReferenceTurn() { return referenceTurn; }
    public void setReferenceTurn(String referenceTurn) { this.referenceTurn = referenceTurn; }
    public MealScope getMealScope() { return mealScope; }
    public void setMealScope(MealScope mealScope) { this.mealScope = mealScope; }
    public BusinessCorrection getCorrection() { return correction; }
    public void setCorrection(BusinessCorrection correction) { this.correction = correction; }
    public BusinessTemporalIntent getTemporal() { return temporal; }
    public void setTemporal(BusinessTemporalIntent temporal) { this.temporal = temporal; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isRequiresClarification() { return requiresClarification; }
    public void setRequiresClarification(boolean requiresClarification) { this.requiresClarification = requiresClarification; }
    public String getClarificationQuestion() { return clarificationQuestion; }
    public void setClarificationQuestion(String clarificationQuestion) { this.clarificationQuestion = clarificationQuestion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getFallbackReason() { return fallbackReason; }
    public void setFallbackReason(String fallbackReason) { this.fallbackReason = fallbackReason; }
    public String getSemanticCatalogVersion() { return semanticCatalogVersion; }
    public void setSemanticCatalogVersion(String semanticCatalogVersion) { this.semanticCatalogVersion = semanticCatalogVersion; }
}
