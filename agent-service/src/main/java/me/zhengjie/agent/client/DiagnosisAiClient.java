package me.zhengjie.agent.client;

import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisResponse;
import me.zhengjie.agent.rule.RuleRegistry;

public interface DiagnosisAiClient {

    DiagnosisResponse diagnose(DiagnosisContextDto context, RuleRegistry ruleRegistry);
}
