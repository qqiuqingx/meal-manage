package me.zhengjie.agent.orchestrator;

import me.zhengjie.agent.client.DiagnosisAiClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.springframework.stereotype.Component;

@Component
public class MealPlanDiagnosisOrchestrator {

    private static final String MEAL_PLAN_SCENE = "MEAL_PLAN_NOT_GENERATED";

    private final RuleRegistryLoader ruleRegistryLoader;
    private final DiagnosisAiClient diagnosisAiClient;

    /**
     * 组装规则加载器和 AI 客户端，负责统一编排诊断输出。
     */
    public MealPlanDiagnosisOrchestrator(RuleRegistryLoader ruleRegistryLoader,
                                         DiagnosisAiClient diagnosisAiClient) {
        this.ruleRegistryLoader = ruleRegistryLoader;
        this.diagnosisAiClient = diagnosisAiClient;
    }

    /**
     * 加载规则后委托 AI 直接基于上下文和规则进行诊断。
     */
    public DiagnosisResponse orchestrate(DiagnosisContextDto context) {
        RuleRegistry ruleRegistry = ruleRegistryLoader.load(MEAL_PLAN_SCENE);
        DiagnosisResponse response = diagnosisAiClient.diagnose(context, ruleRegistry);
        fillContextIfMissing(response, context, ruleRegistry);
        return response;
    }

    private void fillContextIfMissing(DiagnosisResponse response,
                                      DiagnosisContextDto context,
                                      RuleRegistry ruleRegistry) {
        response.setCustomerId(context.getCustomerId());
        response.setCustomerName(context.getCustomerName());
        response.setRecordDate(context.getRecordDate());
        response.setMealType(context.getMealType());
        response.setRuleVersionDigest(ruleRegistry.getVersionDigest());
    }
}
