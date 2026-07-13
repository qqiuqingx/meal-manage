package me.zhengjie.agent.query;

import me.zhengjie.agent.query.domain.AgentQueryFact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessAnswerValidatorTest {
    private final BusinessAnswerValidator validator = new BusinessAnswerValidator();

    @Test
    void shouldRejectAmountAndWriteClaims() {
        assertFalse(validator.isSafe("该订单金额为 100", List.of()));
        assertFalse(validator.isSafe("已修改订单", List.of()));
        assertFalse(validator.isSafe("查询完成", List.of(new AgentQueryFact("F1", "单价", 10, null, "ORDER", "1"))));
    }

    @Test
    void shouldAllowReadOnlyVerificationFact() {
        assertTrue(validator.isSafe("已核销 1 餐", List.of(new AgentQueryFact("F1", "核销记录数", 1, "笔", "VERIFICATION_LIST", "1"))));
    }

    @Test
    void shouldAllowUndeletedLogAsReadOnlyStatisticalScope() {
        assertTrue(validator.isSafe("当前余额按未删除核销日志实时统计。", List.of()));
        assertFalse(validator.isSafe("已删除该核销记录。", List.of()));
    }

    @Test
    void shouldRejectFabricatedFactReference() {
        assertFalse(validator.isSafe("查询完成 [F2]", List.of(new AgentQueryFact("F1", "核销记录数", 1, "笔", "VERIFICATION_LIST", "1"))));
        assertTrue(validator.isSafe("查询完成 [F1]", List.of(new AgentQueryFact("F1", "核销记录数", 1, "笔", "VERIFICATION_LIST", "1"))));
    }

    @Test
    void shouldRejectQuantifiedNumberNotBackedByFacts() {
        List<AgentQueryFact> facts = List.of(new AgentQueryFact("F1", "核销记录数", 2, "条", "VERIFICATION_LIST", "1"));

        assertTrue(validator.isSafe("查询到 2 条核销记录 [F1]", facts));
        assertFalse(validator.isSafe("查询到 3 条核销记录 [F1]", facts));
        assertFalse(validator.isSafe("当前可用 2 个候选菜", List.of()));
    }

    @Test
    void shouldRejectRawPhoneAndCustomerMealPlanEvidenceWithoutCustomerCode() {
        AgentQueryFact missingCode = new AgentQueryFact("F1", "因过敏过滤菜品", "香菇滑鸡", null, "MEAL_PLAN_DISH_ITEM", "89231");
        assertFalse(validator.isSafe("B3303：香菇滑鸡。[F1]", List.of(missingCode)));

        AgentQueryFact phone = new AgentQueryFact("F1", "联系方式", "13812345678", null, "CUSTOMER_OVERVIEW", "1");
        assertFalse(validator.isSafe("联系方式：[F1]", List.of(phone)));
    }
}
