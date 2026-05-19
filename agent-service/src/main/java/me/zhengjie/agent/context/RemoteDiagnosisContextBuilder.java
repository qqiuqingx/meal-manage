package me.zhengjie.agent.context;

import me.zhengjie.agent.client.DiagnosisContextClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 优先通过主系统接口构建诊断上下文，失败时回退到本地默认上下文。
 */
@Primary
@Component
public class RemoteDiagnosisContextBuilder implements DiagnosisContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(RemoteDiagnosisContextBuilder.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final DiagnosisContextClient diagnosisContextClient;
    private final DefaultDiagnosisContextBuilder fallbackBuilder;

    public RemoteDiagnosisContextBuilder(DiagnosisContextClient diagnosisContextClient,
                                         DefaultDiagnosisContextBuilder fallbackBuilder) {
        this.diagnosisContextClient = diagnosisContextClient;
        this.fallbackBuilder = fallbackBuilder;
    }

    @Override
    public DiagnosisContextDto build(DiagnosisRequest request) {
        long start = System.currentTimeMillis();
        try {
            log.info("诊断阶段 stage=远程上下文编排开始 requestId={} customerId={} customerCode={} recordDate={} mealType={}",
                MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(), request.getMealType());
            DiagnosisContextDto context = diagnosisContextClient.fetch(request);
            if (context != null) {
                log.info("诊断阶段 stage=远程上下文编排完成 requestId={} customerId={} customerCode={} recordDate={} mealType={} customerName={} orders={} customerPlans={} candidateDishStats={} costMs={}",
                    MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getCustomerCode(), context.getRecordDate(),
                    context.getMealType(), context.getCustomerName(), sizeOf(context.getOrders()), sizeOf(context.getCustomerPlans()),
                    sizeOf(context.getCandidateDishStats()), System.currentTimeMillis() - start);
                return context;
            }
            log.warn("诊断阶段 stage=远程上下文返回空 requestId={} customerId={} customerCode={} recordDate={} mealType={} costMs={}",
                MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(),
                request.getMealType(), System.currentTimeMillis() - start);
        } catch (RuntimeException ex) {
            log.warn("诊断阶段 stage=远程上下文编排失败并回退本地上下文 requestId={} customerId={} customerCode={} recordDate={} mealType={} costMs={} errorType={} errorMessage={}",
                MDC.get(REQUEST_ID_KEY), request.getCustomerId(), request.getCustomerCode(), request.getRecordDate(),
                request.getMealType(), System.currentTimeMillis() - start, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        }
        return fallbackBuilder.build(request);
    }

    private int sizeOf(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }
}
