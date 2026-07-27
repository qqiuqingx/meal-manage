package me.zhengjie.agent.capability;

import me.zhengjie.agent.analysis.SemanticCapabilityCatalog;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 启动时校验能力目录与处理器的 profile 映射，避免 YAML 成为无效配置。 */
@Component
public class CapabilityHandlerRegistry {
    private final Map<String, CapabilityHandler> byProfile;

    public CapabilityHandlerRegistry(List<CapabilityHandler> handlers, SemanticCapabilityCatalog catalog) {
        Map<String, CapabilityHandler> profiles = new LinkedHashMap<>();
        for (CapabilityHandler handler : handlers) {
            for (String profile : handler.plannerProfiles()) {
                if (profiles.putIfAbsent(profile, handler) != null) {
                    throw new IllegalStateException("Duplicate capability planner profile: " + profile);
                }
            }
        }
        for (SemanticCapabilityCatalog.CapabilityDefinition definition : catalog.getCapabilities()) {
            if (!profiles.containsKey(definition.plannerProfile())) {
                throw new IllegalStateException("No capability handler for planner profile: " + definition.plannerProfile());
            }
            if (definition.requiredPermissions().stream().anyMatch(permission -> !AgentPermissionCatalog.contains(permission))) {
                throw new IllegalStateException("Unknown capability permission: " + definition.capabilityId());
            }
        }
        this.byProfile = Map.copyOf(profiles);
    }

    /** 返回目录登记 profile 的唯一处理器；未登记能力不允许降级到猜测性旧意图。 */
    public CapabilityHandler require(String plannerProfile) {
        CapabilityHandler handler = byProfile.get(plannerProfile);
        if (handler == null) throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: " + plannerProfile);
        return handler;
    }
}
