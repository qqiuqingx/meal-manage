package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.RuleRegistry;
import me.zhengjie.agent.validator.DiagnosisResultValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 未配置真实模型时的安全兜底客户端。
 */
@Component
@ConditionalOnProperty(prefix = "agent.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class FallbackDiagnosisAiClient implements DiagnosisAiClient {

    private static final Logger log = LoggerFactory.getLogger(FallbackDiagnosisAiClient.class);
    private static final String REQUEST_ID_KEY = "requestId";

    private final DiagnosisResultValidator resultValidator;

    public FallbackDiagnosisAiClient(DiagnosisResultValidator resultValidator) {
        this.resultValidator = resultValidator;
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry) {
        log.info("fallback ai client used requestId={} customerId={} recordDate={} mealType={} aiEnabled=false",
            MDC.get(REQUEST_ID_KEY), context.getCustomerId(), context.getRecordDate(), context.getMealType());
        return resultValidator.validateOrFallback(null, context, ruleRegistry.getVersionDigest());
    }
}
