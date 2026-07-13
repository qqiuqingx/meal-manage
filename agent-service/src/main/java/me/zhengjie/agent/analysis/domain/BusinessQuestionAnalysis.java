package me.zhengjie.agent.analysis.domain;

import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

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
    private double confidence;
    private boolean requiresClarification;
    private String clarificationQuestion;
    private String source = "RULE";

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
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isRequiresClarification() { return requiresClarification; }
    public void setRequiresClarification(boolean requiresClarification) { this.requiresClarification = requiresClarification; }
    public String getClarificationQuestion() { return clarificationQuestion; }
    public void setClarificationQuestion(String clarificationQuestion) { this.clarificationQuestion = clarificationQuestion; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
