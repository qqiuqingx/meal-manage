package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.ConversationUnderstandingResult;
import me.zhengjie.agent.analysis.SemanticCapabilityCatalog;
import me.zhengjie.agent.analysis.SemanticCapabilityCatalogLoader;
import me.zhengjie.agent.analysis.domain.SemanticRequestFrame;
import me.zhengjie.agent.application.conversation.ConversationExecutionContext;
import me.zhengjie.agent.capability.CapabilityHandlerRegistry;
import me.zhengjie.agent.capability.LegacyBusinessQueryCapabilityHandler;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import java.util.ArrayList;
import java.util.List;

/** 将多个受控语义帧编译为固定查询计划；不从用户文本或模型输出读取工具名。 */
public class MultiIntentPlanningService {
    private final SemanticCapabilityCatalog capabilityCatalog;
    private final CapabilityHandlerRegistry handlerRegistry;

    /** 使用内置、版本化的受控能力目录构造规划器。 */
    public MultiIntentPlanningService() {
        this(defaultCatalog());
    }

    /** 注入已校验能力目录，便于应用启动时统一管理目录版本。 */
    public MultiIntentPlanningService(SemanticCapabilityCatalog capabilityCatalog) {
        this(capabilityCatalog, new CapabilityHandlerRegistry(
            List.of(new LegacyBusinessQueryCapabilityHandler()), capabilityCatalog));
    }

    /** 注入能力目录和 Handler Registry，新增能力无需修改中心 Planner。 */
    public MultiIntentPlanningService(SemanticCapabilityCatalog capabilityCatalog,
                                      CapabilityHandlerRegistry handlerRegistry) {
        this.capabilityCatalog = capabilityCatalog;
        this.handlerRegistry = handlerRegistry;
    }

    /** 编译目前登记的多帧能力；未登记组合返回空计划，调用方应返回能力缺失码。 */
    public List<AgentQueryPlan> plan(ConversationUnderstandingResult understanding) {
        List<AgentQueryPlan> plans = new ArrayList<>();
        if (understanding == null || understanding.getFrames() == null) return plans;
        for (SemanticRequestFrame frame : understanding.getFrames()) {
            SemanticCapabilityCatalog.CapabilityDefinition capability = capabilityCatalog == null ? null : capabilityCatalog.findMatching(frame).orElse(null);
            if (capability == null) return List.of();
            AgentQueryPlan plan;
            try {
                plan = handlerRegistry.compile(capability, frame, new ConversationExecutionContext(null, null));
            } catch (IllegalArgumentException exception) {
                return List.of();
            }
            plans.add(plan);
        }
        return plans;
    }

    private static SemanticCapabilityCatalog defaultCatalog() {
        return new SemanticCapabilityCatalogLoader().load();
    }
}
