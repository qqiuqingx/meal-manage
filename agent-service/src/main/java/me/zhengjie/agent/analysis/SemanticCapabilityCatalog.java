package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.analysis.domain.SemanticScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 版本化的受控语义能力目录，仅保存业务组合与 planner profile。 */
public class SemanticCapabilityCatalog {
    private String catalogVersion;
    private List<Map<String, Object>> capabilities = new ArrayList<>();
    public String getCatalogVersion() { return catalogVersion; }
    public void setCatalogVersion(String catalogVersion) { this.catalogVersion = catalogVersion; }
    public List<Map<String, Object>> getCapabilities() { return capabilities; }
    public void setCapabilities(List<Map<String, Object>> capabilities) { this.capabilities = capabilities == null ? new ArrayList<>() : capabilities; }

    /**
     * 从服务端登记的能力中查找与语义帧完全兼容的一项。
     *
     * @param frame 已完成上下文解析的语义帧
     * @return 匹配能力；不存在时调用方必须拒绝执行
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> findMatching(SemanticRequestFrame frame) {
        if (frame == null) {
            return Optional.empty();
        }
        return capabilities.stream().filter(capability -> {
            Object rawFrame = capability.get("frame");
            if (!(rawFrame instanceof Map<?, ?>)) {
                return false;
            }
            Map<String, Object> rule = (Map<String, Object>) rawFrame;
            String scopeKind = frame.getScope() != null && frame.getScope().getType() == SemanticScope.Type.CONTEXT_REFERENCE
                ? String.valueOf(frame.getScope().getRequiredKind())
                : frame.getScope() == null ? SemanticScope.Type.EXPLICIT.name() : String.valueOf(frame.getScope().getType());
            return equalsValue(rule.get("goal"), frame.getGoal())
                && equalsValue(rule.get("targetEntity"), frame.getTargetEntity())
                && containsAll(rule.get("allowedScopeKinds"), List.of(scopeKind))
                && allowsSetDefinition(capability.get("allowedSetDefinitions"), frame.getScope())
                && containsAll(rule.get("requiredMeasures"), frame.getMeasures())
                && containsAll(rule.get("allowedDimensions"), frame.getDimensions())
                && containsAll(rule.get("allowedOperations"), frame.getOperations())
                && containsValue(rule.get("allowedOutputShapes"), frame.getOutputShape());
        }).findFirst();
    }

    /** 判断登记值与枚举名称是否一致。 */
    private boolean equalsValue(Object expected, Object actual) {
        return expected != null && actual != null && String.valueOf(expected).equals(String.valueOf(actual));
    }

    /** 判断规则列表包含请求中的所有受控枚举。 */
    private boolean containsAll(Object allowed, List<?> requested) {
        if (requested == null || requested.isEmpty()) {
            return true;
        }
        if (!(allowed instanceof List<?>)) {
            return false;
        }
        return requested.stream().allMatch(value -> containsValue(allowed, value));
    }

    /** 判断规则列表包含指定受控枚举。 */
    private boolean containsValue(Object allowed, Object requested) {
        return allowed instanceof List<?> && requested != null
            && ((List<?>) allowed).stream().anyMatch(value -> String.valueOf(value).equals(String.valueOf(requested)));
    }

    /** 限制集合型能力只能在目录明确列出的可重算定义上执行。 */
    private boolean allowsSetDefinition(Object allowedDefinitions, SemanticScope scope) {
        if (!(allowedDefinitions instanceof List<?>)) {
            return true;
        }
        return scope != null && scope.getResolvedDefinitionId() != null
            && containsValue(allowedDefinitions, scope.getResolvedDefinitionId());
    }
}
