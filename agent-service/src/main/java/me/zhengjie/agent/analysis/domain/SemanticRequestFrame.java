package me.zhengjie.agent.analysis.domain;

import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import java.util.ArrayList;
import java.util.List;

/** 单个可校验、可规划的组合式业务请求。 */
public class SemanticRequestFrame {
    private String frameId;
    private SemanticGoal goal;
    private SemanticEntityType targetEntity;
    private SemanticScope scope;
    private List<AgentQueryMetric> measures = new ArrayList<>();
    private List<AgentQueryDimension> dimensions = new ArrayList<>();
    private List<SemanticOperation> operations = new ArrayList<>();
    private AgentQueryFilters constraints = new AgentQueryFilters();
    private SemanticOutputShape outputShape;
    private List<String> missingInformation = new ArrayList<>();
    private List<String> dependsOnFrameIds = new ArrayList<>();
    private double confidence;
    public String getFrameId() { return frameId; } public void setFrameId(String frameId) { this.frameId = frameId; }
    public SemanticGoal getGoal() { return goal; } public void setGoal(SemanticGoal goal) { this.goal = goal; }
    public SemanticEntityType getTargetEntity() { return targetEntity; } public void setTargetEntity(SemanticEntityType targetEntity) { this.targetEntity = targetEntity; }
    public SemanticScope getScope() { return scope; } public void setScope(SemanticScope scope) { this.scope = scope; }
    public List<AgentQueryMetric> getMeasures() { return measures; } public void setMeasures(List<AgentQueryMetric> measures) { this.measures = measures == null ? new ArrayList<>() : measures; }
    public List<AgentQueryDimension> getDimensions() { return dimensions; } public void setDimensions(List<AgentQueryDimension> dimensions) { this.dimensions = dimensions == null ? new ArrayList<>() : dimensions; }
    public List<SemanticOperation> getOperations() { return operations; } public void setOperations(List<SemanticOperation> operations) { this.operations = operations == null ? new ArrayList<>() : operations; }
    public AgentQueryFilters getConstraints() { return constraints; } public void setConstraints(AgentQueryFilters constraints) { this.constraints = constraints == null ? new AgentQueryFilters() : constraints; }
    public SemanticOutputShape getOutputShape() { return outputShape; } public void setOutputShape(SemanticOutputShape outputShape) { this.outputShape = outputShape; }
    public List<String> getMissingInformation() { return missingInformation; } public void setMissingInformation(List<String> missingInformation) { this.missingInformation = missingInformation == null ? new ArrayList<>() : missingInformation; }
    public List<String> getDependsOnFrameIds() { return dependsOnFrameIds; } public void setDependsOnFrameIds(List<String> dependsOnFrameIds) { this.dependsOnFrameIds = dependsOnFrameIds == null ? new ArrayList<>() : dependsOnFrameIds; }
    public double getConfidence() { return confidence; } public void setConfidence(double confidence) { this.confidence = confidence; }
}
