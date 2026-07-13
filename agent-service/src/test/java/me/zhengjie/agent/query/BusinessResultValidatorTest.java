package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证公共菜单结果不会因结构异常或米饭异常分布被当作完整结论。 */
class BusinessResultValidatorTest {
    private final BusinessResultValidator validator = new BusinessResultValidator();

    @Test
    void shouldFlagRiceOnlyScheduledMenu() {
        AgentQueryPlan plan = new AgentQueryPlan(); plan.setMealScope(MealScope.ALL_AVAILABLE);
        Map<String, Object> result = Map.of("recordDate", "2026-07-13", "groups", List.of(
            Map.of("mealTypeCode", "LUNCH", "items", List.of(Map.of("dishTypeCode", "RICE"))),
            Map.of("mealTypeCode", "DINNER", "items", List.of())));

        assertTrue(validator.validate("BUSINESS_QUERY_SCHEDULED_MENU", plan, result).contains("MENU_RESULT_IMPLAUSIBLE"));
    }

    @Test
    void shouldRejectScheduledMenuWithUnexpectedMealGroup() {
        AgentQueryPlan plan = new AgentQueryPlan(); plan.setMealScope(MealScope.LUNCH);
        Map<String, Object> result = Map.of("recordDate", "2026-07-13", "groups", List.of(
            Map.of("mealTypeCode", "DINNER", "items", List.of())));

        assertTrue(validator.validate("BUSINESS_QUERY_SCHEDULED_MENU", plan, result).contains("PLAN_RESULT_MISMATCH"));
    }
}
