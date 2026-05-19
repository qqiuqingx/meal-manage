package me.zhengjie.agent.orchestrator;

import me.zhengjie.agent.client.DiagnosisAiClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.rule.RuleRegistryLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class MealPlanDiagnosisOrchestrator {

    private static final String MEAL_PLAN_SCENE = "MEAL_PLAN_NOT_GENERATED";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final Logger log = LoggerFactory.getLogger(MealPlanDiagnosisOrchestrator.class);

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
        long start = System.currentTimeMillis();
        RuleRegistry ruleRegistry = ruleRegistryLoader.load(MEAL_PLAN_SCENE);
        log.info("diagnosis rules loaded requestId={} scene={} ruleCount={} digest={} costMs={}",
            MDC.get(REQUEST_ID_KEY), ruleRegistry.getScene(), ruleRegistry.getRules() == null ? 0 : ruleRegistry.getRules().size(),
            shortDigest(ruleRegistry.getVersionDigest()), System.currentTimeMillis() - start);
        long aiStart = System.currentTimeMillis();
        DiagnosisResponse response = diagnosisAiClient.diagnose(context, ruleRegistry);
        log.info("diagnosis ai client returned requestId={} fallback={} reasonCount={} modelName={} costMs={}",
            MDC.get(REQUEST_ID_KEY), response.isFallback(), response.getReasons() == null ? 0 : response.getReasons().size(),
            response.getModelName(), System.currentTimeMillis() - aiStart);
        fillContextIfMissing(response, context, ruleRegistry);
        return response;
    }

    private String shortDigest(String digest) {
        if (digest == null || digest.length() <= 12) {
            return digest;
        }
        return digest.substring(0, 12);
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
