package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.SemanticEntityType;
import me.zhengjie.agent.analysis.domain.SemanticGoal;
import me.zhengjie.agent.analysis.domain.SemanticOperation;
import me.zhengjie.agent.analysis.domain.SemanticOutputShape;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 版本化的受控语义能力目录；业务层不再读取 YAML 的任意 Map。 */
public class SemanticCapabilityCatalog {
    private String catalogVersion;
    private List<CapabilityDefinition> capabilities = new ArrayList<>();

    public String getCatalogVersion() { return catalogVersion; }
    public void setCatalogVersion(String catalogVersion) { this.catalogVersion = catalogVersion; }
    public List<CapabilityDefinition> getCapabilities() { return List.copyOf(capabilities); }
    public void setCapabilities(List<CapabilityDefinition> capabilities) { this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities); }

    /** 从服务端登记的能力中查找与语义帧完全兼容的一项。 */
    public Optional<CapabilityDefinition> findMatching(SemanticRequestFrame frame) {
        return frame == null ? Optional.empty() : capabilities.stream().filter(item -> item.supports(frame)).findFirst();
    }

    /** 目录中的单项能力定义。 */
    public record CapabilityDefinition(String capabilityId, String displayName, SemanticFrameConstraint frame,
                                       Set<String> allowedSetDefinitions, Set<String> requiredPermissions,
                                       String plannerProfile, RiskLevel riskLevel) {
        public boolean supports(SemanticRequestFrame request) {
            if (request == null || frame == null || !frame.matches(request)) return false;
            return allowedSetDefinitions == null || allowedSetDefinitions.isEmpty()
                || request.getScope() != null && allowedSetDefinitions.contains(request.getScope().getResolvedDefinitionId());
        }
    }

    /** 目录允许的受控语义帧组合。 */
    public record SemanticFrameConstraint(SemanticGoal goal, SemanticEntityType targetEntity,
                                          Set<String> allowedScopeKinds, Set<AgentQueryMetric> requiredMeasures,
                                          Set<AgentQueryDimension> allowedDimensions,
                                          Set<SemanticOperation> allowedOperations,
                                          Set<SemanticOutputShape> allowedOutputShapes) {
        boolean matches(SemanticRequestFrame request) {
            String scopeKind = request.getScope() != null && request.getScope().getType() == SemanticScope.Type.CONTEXT_REFERENCE
                ? String.valueOf(request.getScope().getRequiredKind())
                : request.getScope() == null ? SemanticScope.Type.EXPLICIT.name() : String.valueOf(request.getScope().getType());
            return goal == request.getGoal() && targetEntity == request.getTargetEntity()
                && (allowedScopeKinds == null || allowedScopeKinds.contains(scopeKind))
                && containsAll(requiredMeasures, request.getMeasures())
                && containsAll(allowedDimensions, request.getDimensions())
                && containsAll(allowedOperations, request.getOperations())
                && (allowedOutputShapes == null || allowedOutputShapes.contains(request.getOutputShape()));
        }
        private static boolean containsAll(Set<?> allowed, List<?> requested) {
            return requested == null || requested.isEmpty() || allowed != null && allowed.containsAll(requested);
        }
    }

    /** 能力的敏感度，用于目录校验和审计，不作为模型自由输入。 */
    public enum RiskLevel { INTERNAL, INTERNAL_SENSITIVE_LIST }
}
