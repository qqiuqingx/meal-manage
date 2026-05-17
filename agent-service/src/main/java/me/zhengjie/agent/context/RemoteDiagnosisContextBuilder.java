package me.zhengjie.agent.context;

import me.zhengjie.agent.client.DiagnosisContextClient;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 优先通过主系统接口构建诊断上下文，失败时回退到本地默认上下文。
 */
@Primary
@Component
public class RemoteDiagnosisContextBuilder implements DiagnosisContextBuilder {

    private final DiagnosisContextClient diagnosisContextClient;
    private final DefaultDiagnosisContextBuilder fallbackBuilder;

    public RemoteDiagnosisContextBuilder(DiagnosisContextClient diagnosisContextClient,
                                         DefaultDiagnosisContextBuilder fallbackBuilder) {
        this.diagnosisContextClient = diagnosisContextClient;
        this.fallbackBuilder = fallbackBuilder;
    }

    @Override
    public DiagnosisContextDto build(DiagnosisRequest request) {
        try {
            DiagnosisContextDto context = diagnosisContextClient.fetch(request);
            if (context != null) {
                return context;
            }
        } catch (RuntimeException ex) {
            // 主系统不可用时，降级为本地空上下文，保证诊断服务仍可返回结构化结果。
        }
        return fallbackBuilder.build(request);
    }
}
