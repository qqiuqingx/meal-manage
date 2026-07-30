package me.zhengjie.modules.agent.query;

import me.zhengjie.modules.agent.query.domain.AgentBusinessRuleDefinition;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证有效规则均能加载其唯一、已哈希校验的业务依据，并可按登记主题查询。 */
class AgentBusinessRuleDocumentationContractTest {

    @Test
    void shouldLoadEveryEffectiveRuleWithVerifiedDocumentationEvidence() throws Exception {
        AgentBusinessRuleRegistry registry = new AgentBusinessRuleRegistry();
        registry.initialize();
        Map<String, AgentBusinessRuleDefinition> byTopic = rulesByTopic(registry);

        assertEquals(7, byTopic.size());
        for (String topic : new String[] {"MEAL_BALANCE", "剩余餐数", "ORDER_EFFECTIVE", "订单有效性", "MEAL_PLAN_MATCH", "DIETARY_FILTER", "VERIFICATION_REFUND_EFFECT"}) {
            AgentBusinessRuleDefinition definition = registry.find(topic);
            assertNotNull(definition, topic);
            assertEquals("EFFECTIVE", definition.getStatus());
            assertTrue(definition.getEvidenceDocument().startsWith("doc/business/"));
            assertTrue(definition.getEvidenceHash().startsWith("sha256:"));
        }
    }

    /** 仅为断言目录实际注册主题数量读取不可变运行时索引，不修改生产对象。 */
    @SuppressWarnings("unchecked")
    private Map<String, AgentBusinessRuleDefinition> rulesByTopic(AgentBusinessRuleRegistry registry) throws Exception {
        Field field = AgentBusinessRuleRegistry.class.getDeclaredField("rulesByTopic");
        field.setAccessible(true);
        return (Map<String, AgentBusinessRuleDefinition>) field.get(registry);
    }
}
