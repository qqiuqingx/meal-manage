package me.zhengjie.agent.service.impl;

import me.zhengjie.agent.action.DiagnosisActionDraftService;
import me.zhengjie.agent.context.DiagnosisContextBuilder;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.orchestrator.MealPlanDiagnosisOrchestrator;
import me.zhengjie.agent.service.MealPlanDiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MealPlanDiagnosisServiceImpl implements MealPlanDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(MealPlanDiagnosisServiceImpl.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final DiagnosisContextBuilder contextBuilder;
    private final MealPlanDiagnosisOrchestrator orchestrator;
    private final DiagnosisActionDraftService actionDraftService;
    private final boolean toolModeEnabled;

    /**
     * 组装上下文构建器和诊断编排器，串起完整诊断链路。
     */
    public MealPlanDiagnosisServiceImpl(DiagnosisContextBuilder contextBuilder,
                                        MealPlanDiagnosisOrchestrator orchestrator,
                                        DiagnosisActionDraftService actionDraftService,
                                        @Value("${agent.diagnosis.tool-mode-enabled:true}") boolean toolModeEnabled) {
        this.contextBuilder = contextBuilder;
        this.orchestrator = orchestrator;
        this.actionDraftService = actionDraftService;
        this.toolModeEnabled = toolModeEnabled;
    }

    /**
     * 根据请求构建上下文并输出诊断结果。
     */
    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        long start = System.currentTimeMillis();
        log.info("诊断阶段 stage=上下文构建开始 requestId={} customerId={} customerCode={} recordDate={} mealType={}",
            MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
        DiagnosisContextDto context = toolModeEnabled ? lightweightContext(request) : contextBuilder.build(request);
        log.info("诊断阶段 stage=上下文构建完成 requestId={} customerId={} customerCode={} customerName={} recordDate={} mealType={} orders={} customerPlans={} candidateDishStats={} mealPlanPresent={} costMs={}",
            MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getCustomerCode(), context.getCustomerName(),
            context.getRecordDate(), context.getMealType(), sizeOf(context.getOrders()), sizeOf(context.getCustomerPlans()),
            sizeOf(context.getCandidateDishStats()), context.getMealPlan() != null && !context.getMealPlan().isEmpty(),
            System.currentTimeMillis() - start);
        log.info("诊断阶段 stage=进入规则编排 requestId={} customerId={} recordDate={} mealType={}",
            MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType());
        DiagnosisResponse response = orchestrator.orchestrate(context);
        // 智能客服本期严格只读：诊断仅提供证据与人工核对建议，不生成可确认或可执行的动作草稿。
        response.setActionDrafts(java.util.List.of());
        response.setRequestId(MDC.get(REQUEST_ID_KEY));
        log.info("诊断阶段 stage=结果就绪 requestId={} customerId={} recordDate={} mealType={} fallback={} modelName={} reasonCount={}",
            response.getRequestId(), response.getCustomerId(), response.getRecordDate(), response.getMealType(),
            response.isFallback(), response.getModelName(), sizeOf(response.getReasons()));
        return response;
    }

    private DiagnosisContextDto lightweightContext(DiagnosisRequest request) {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(request.getCustomerId());
        context.setCustomerCode(request.getCustomerCode());
        context.setRecordDate(request.getRecordDate());
        context.setMealType(request.getMealType());
        return context;
    }

    private int sizeOf(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }
}
