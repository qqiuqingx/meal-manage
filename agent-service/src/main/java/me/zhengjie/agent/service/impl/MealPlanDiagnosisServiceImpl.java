package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.context.DiagnosisContextBuilder;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class MealPlanDiagnosisServiceImpl implements MealPlanDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanDiagnosisServiceImpl.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final DiagnosisContextBuilder contextBuilder;
    private final MealPlanDiagnosisOrchestrator orchestrator;

    /**
     * 组装上下文构建器和诊断编排器，串起完整诊断链路。
     */
    public MealPlanDiagnosisServiceImpl(DiagnosisContextBuilder contextBuilder,
                                        MealPlanDiagnosisOrchestrator orchestrator) {
        this.contextBuilder = contextBuilder;
        this.orchestrator = orchestrator;
    }

    /**
     * 根据请求构建上下文并输出诊断结果。
     */
    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        long start = System.currentTimeMillis();
        DiagnosisContextDto context = contextBuilder.build(request);
        log.info("diagnosis context built requestId={} customerId={} customerCode={} customerName={} recordDate={} mealType={} orders={} customerPlans={} candidateDishStats={} mealPlanPresent={} costMs={}",
            MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getCustomerCode(), context.getCustomerName(),
            context.getRecordDate(), context.getMealType(), sizeOf(context.getOrders()), sizeOf(context.getCustomerPlans()),
            sizeOf(context.getCandidateDishStats()), context.getMealPlan() != null && !context.getMealPlan().isEmpty(),
            System.currentTimeMillis() - start);
        DiagnosisResponse response = orchestrator.orchestrate(context);
        response.setRequestId(MDC.get(REQUEST_ID_KEY));
        log.info("diagnosis response ready requestId={} customerId={} recordDate={} mealType={} fallback={} modelName={} reasonCount={}",
            response.getRequestId(), response.getCustomerId(), response.getRecordDate(), response.getMealType(),
            response.isFallback(), response.getModelName(), sizeOf(response.getReasons()));
        return response;
    }

    private int sizeOf(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }
}
