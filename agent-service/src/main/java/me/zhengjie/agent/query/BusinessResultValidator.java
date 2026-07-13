package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.query.domain.AgentQueryPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 在回答组装前校验受控工具结果的结构与基本业务合理性。 */
public class BusinessResultValidator {
    /** 校验公共菜单餐次范围、分组结构和明显异常的菜品类型分布。 */
    @SuppressWarnings("unchecked")
    public List<String> validate(String responseType, AgentQueryPlan plan, Map<String, Object> result) {
        List<String> warnings = new ArrayList<>();
        if (!"BUSINESS_QUERY_SCHEDULED_MENU".equals(responseType)) return warnings;
        if (result == null || result.get("recordDate") == null) {
            warnings.add("PLAN_RESULT_MISMATCH");
            return warnings;
        }
        Object groupsValue = result.get("groups");
        if (!(groupsValue instanceof List)) {
            warnings.add("PLAN_RESULT_MISMATCH");
            return warnings;
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Object value : (List<?>) groupsValue) {
            if (value instanceof Map) groups.add((Map<String, Object>) value);
        }
        if (groups.size() != ((List<?>) groupsValue).size() || !matchesScope(plan == null ? null : plan.getMealScope(), groups)) {
            warnings.add("PLAN_RESULT_MISMATCH");
        }
        boolean hasItem = false;
        boolean onlyRice = true;
        for (Map<String, Object> group : groups) {
            Object items = group.get("items");
            if (!(items instanceof List)) continue;
            for (Object item : (List<?>) items) {
                if (!(item instanceof Map)) continue;
                hasItem = true;
                if (!"RICE".equals(String.valueOf(((Map<?, ?>) item).get("dishTypeCode")))) onlyRice = false;
            }
        }
        if (hasItem && onlyRice) warnings.add("MENU_RESULT_IMPLAUSIBLE");
        return warnings;
    }

    private boolean matchesScope(MealScope scope, List<Map<String, Object>> groups) {
        if (scope == MealScope.LUNCH) return groups.size() == 1 && "LUNCH".equals(groups.get(0).get("mealTypeCode"));
        if (scope == MealScope.DINNER) return groups.size() == 1 && "DINNER".equals(groups.get(0).get("mealTypeCode"));
        return groups.size() == 2 && "LUNCH".equals(groups.get(0).get("mealTypeCode"))
            && "DINNER".equals(groups.get(1).get("mealTypeCode"));
    }
}
