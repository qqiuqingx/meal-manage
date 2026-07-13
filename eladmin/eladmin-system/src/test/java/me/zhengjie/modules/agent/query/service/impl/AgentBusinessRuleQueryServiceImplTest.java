package me.zhengjie.modules.agent.query.service.impl;

import me.zhengjie.modules.agent.query.domain.dto.AgentBusinessRuleDto;
import me.zhengjie.modules.agent.query.AgentBusinessRuleRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Agent 仅返回已登记且不含金额语义的业务规则。 */
class AgentBusinessRuleQueryServiceImplTest {

    private final AgentBusinessRuleQueryServiceImpl service = service();

    @Test
    void shouldReturnAllWhitelistedBusinessRuleTopics() {
        for (String topic : new String[] {"MEAL_BALANCE", "ORDER_EFFECTIVE", "MEAL_PLAN_MATCH", "DIETARY_FILTER", "VERIFICATION_REFUND_EFFECT"}) {
            AgentBusinessRuleDto rule = service.explain(topic);
            assertTrue(rule.isPresent());
            assertEquals("1.0", rule.getVersion());
            assertFalse(rule.getContent().contains("金额"));
            assertTrue(rule.getEvidenceDocument().startsWith("doc/business/"));
        }
    }

    /** 测试直接构造时也按生产启动路径加载并校验规则资源。 */
    private AgentBusinessRuleQueryServiceImpl service() {
        AgentBusinessRuleRegistry registry = new AgentBusinessRuleRegistry();
        registry.initialize();
        return new AgentBusinessRuleQueryServiceImpl(registry);
    }
}
