package me.zhengjie.modules.agent.domain.dto.insight;

import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Agent 专用数据契约安全测试。
 */
public class AgentCustomerInsightDtoSecurityTest {

    /**
     * 验证订单餐数余额 DTO 不声明任何金额属性，防止金额被传入 Agent 上下文。
     */
    @Test
    public void shouldNotExposeAmountPropertyInOrderMealBalanceItem() throws Exception {
        boolean hasAmountProperty = Arrays.stream(Introspector.getBeanInfo(AgentCustomerOrderMealBalanceItem.class)
                        .getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .map(String::toLowerCase)
                .anyMatch(name -> name.contains("amount") || name.contains("price"));

        assertFalse(hasAmountProperty, "Agent 订单餐数 DTO 不得暴露金额或单价字段");
    }
}
