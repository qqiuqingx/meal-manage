package me.zhengjie.agent.capability;

import me.zhengjie.agent.analysis.SemanticCapabilityCatalog;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 启动时校验能力目录与处理器的 profile 映射，避免 YAML 成为无效配置。 */
@Component
public class CapabilityHandlerRegistry {
    private final Map<String, CapabilityHandler> byProfile;
    private final Map<String, CapabilityHandler> byCapabilityId;

    public CapabilityHandlerRegistry(List<CapabilityHandler> handlers, SemanticCapabilityCatalog catalog) {
        Map<String, CapabilityHandler> profiles = new LinkedHashMap<>();
        for (CapabilityHandler handler : handlers) {
            for (String profile : handler.plannerProfiles()) {
                if (profiles.putIfAbsent(profile, handler) != null) {
                    throw new IllegalStateException("Duplicate capability planner profile: " + profile);
                }
            }
        }
        Map<String, CapabilityHandler> capabilities = new LinkedHashMap<>();
        Set<String> routingKeys = new HashSet<>();
        for (SemanticCapabilityCatalog.CapabilityDefinition definition : catalog.getCapabilities()) {
            if (!profiles.containsKey(definition.plannerProfile())) {
                throw new IllegalStateException("No capability handler for planner profile: " + definition.plannerProfile());
            }
            if (definition.requiredPermissions().stream().anyMatch(permission -> !AgentPermissionCatalog.contains(permission))) {
                throw new IllegalStateException("Unknown capability permission: " + definition.capabilityId());
            }
            if (capabilities.putIfAbsent(definition.capabilityId(), profiles.get(definition.plannerProfile())) != null) {
                throw new IllegalStateException("Duplicate capability id: " + definition.capabilityId());
            }
            String routingKey = definition.frame() + "|" + definition.allowedSetDefinitions();
            if (!routingKeys.add(routingKey)) {
                throw new IllegalStateException("Ambiguous semantic capability frame: " + definition.capabilityId());
            }
        }
        this.byProfile = Map.copyOf(profiles);
        this.byCapabilityId = Map.copyOf(capabilities);
    }

    /** 返回目录登记 profile 的唯一处理器；未登记能力不允许降级到猜测性旧意图。 */
    public CapabilityHandler require(String plannerProfile) {
        CapabilityHandler handler = byProfile.get(plannerProfile);
        if (handler == null) throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: " + plannerProfile);
        return handler;
    }

    /** 返回能力 ID 对应的唯一处理器，供领域审计和灰度开关使用。 */
    public CapabilityHandler requireCapability(String capabilityId) {
        CapabilityHandler handler = byCapabilityId.get(capabilityId);
        if (handler == null) throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: " + capabilityId);
        return handler;
    }

    /**
     * 通过目录定义路由并编译计划，中心 Planner 不再维护 profile 分支。
     *
     * @param definition 已匹配的能力定义
     * @param frame 受控语义帧
     * @param context 可信执行上下文
     * @return 能力处理器生成的固定查询计划
     */
    public AgentQueryPlan compile(SemanticCapabilityCatalog.CapabilityDefinition definition,
                                  SemanticRequestFrame frame,
                                  ConversationExecutionContext context) {
        if (definition == null || frame == null) {
            throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE");
        }
        CapabilityHandler handler = requireCapability(definition.capabilityId());
        if (!handler.supports(definition.plannerProfile(), frame)) {
            throw new IllegalArgumentException("CAPABILITY_NOT_AVAILABLE: " + definition.capabilityId());
        }
        return handler.compile(definition.plannerProfile(), frame, context);
    }
}
