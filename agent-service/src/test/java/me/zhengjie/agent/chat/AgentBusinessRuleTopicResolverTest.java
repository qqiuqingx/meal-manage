package me.zhengjie.agent.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则同义问法只能从 YAML 目录解析，未登记问法不得伪装为已验证规则。 */
class AgentBusinessRuleTopicResolverTest {
    private final AgentBusinessRuleTopicResolver resolver = new ResourceAgentBusinessRuleTopicResolver();

    @Test
    void shouldResolveAllWhitelistedRuleTopics() {
        assertEquals("MEAL_BALANCE", resolver.resolve("午餐核销扣哪个池").orElseThrow());
        assertEquals("ORDER_EFFECTIVE", resolver.resolve("订单什么时候有效").orElseThrow());
        assertEquals("MEAL_PLAN_MATCH", resolver.resolve("为什么订单不能排午餐").orElseThrow());
        assertEquals("DIETARY_FILTER", resolver.resolve("过敏菜为什么会被过滤").orElseThrow());
        assertEquals("VERIFICATION_REFUND_EFFECT", resolver.resolve("退餐对餐数有什么影响").orElseThrow());
    }

    @Test
    void shouldNotResolveUndeclaredRuleQuestion() {
        assertTrue(resolver.resolve("配送员的排班规则是什么").isEmpty());
    }
}
