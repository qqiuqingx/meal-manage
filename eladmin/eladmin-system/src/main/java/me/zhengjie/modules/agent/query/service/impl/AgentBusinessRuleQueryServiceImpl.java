package me.zhengjie.modules.agent.query.service.impl;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.AgentBusinessRuleRegistry;
import me.zhengjie.modules.agent.query.domain.AgentBusinessRuleDefinition;
import me.zhengjie.modules.agent.query.domain.dto.AgentBusinessRuleDto;
import me.zhengjie.modules.agent.query.service.AgentBusinessRuleQueryService;
import org.springframework.stereotype.Service;

/** 从已校验的版本化目录返回客服可解释业务规则。 */
@Service
@RequiredArgsConstructor
public class AgentBusinessRuleQueryServiceImpl implements AgentBusinessRuleQueryService {
    private final AgentBusinessRuleRegistry registry;

    /** {@inheritDoc} */
    @Override
    public AgentBusinessRuleDto explain(String topic) {
        AgentBusinessRuleDto dto = new AgentBusinessRuleDto();
        AgentBusinessRuleDefinition rule = registry.find(topic);
        if (rule == null) return dto;
        dto.setPresent(true);
        dto.setRuleId(rule.getRuleId());
        dto.setVersion(rule.getVersion());
        dto.setTopic(rule.getTitle());
        dto.setContent(rule.getContent());
        dto.setOwnerModule(rule.getOwnerModule());
        dto.setEvidenceDocument(rule.getEvidenceDocument());
        dto.setUpdatedAt(rule.getUpdatedAt());
        return dto;
    }
}
