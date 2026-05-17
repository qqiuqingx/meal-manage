package me.zhengjie.agent.context;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.springframework.stereotype.Component;

@Component
public class DefaultDiagnosisContextBuilder implements DiagnosisContextBuilder {

    /**
     * 将请求中的基础字段转换为诊断上下文，后续再由外部数据源补全。
     */
    @Override
    public DiagnosisContextDto build(DiagnosisRequest request) {
        DiagnosisContextDto context = new DiagnosisContextDto();
        context.setCustomerId(request.getCustomerId());
        context.setRecordDate(request.getRecordDate());
        context.setMealType(request.getMealType());
        context.setOrders(java.util.List.of());
        context.setCustomerPlans(java.util.List.of());
        return context;
    }
}
