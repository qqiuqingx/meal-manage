package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.BusinessResponseTypeCatalog;
import me.zhengjie.agent.query.presentation.BusinessPresentationResult;

import java.util.ArrayList;
import java.util.List;

/** 在回答组装前校验受控工具结果的结构与基本业务合理性。 */
public class BusinessResultValidator {
    /** 校验公共菜单餐次范围、分组结构和明显异常的菜品类型分布。 */
    public List<String> validate(String responseType, AgentQueryPlan plan,
                                 BusinessPresentationResult result) {
        List<String> warnings = new ArrayList<>();
        if (!BusinessResponseTypeCatalog.SCHEDULED_MENU.equals(responseType)) return warnings;
        if (result == null || result.getRecordDate() == null || !result.isGroupsDeclared()) {
            warnings.add("PLAN_RESULT_MISMATCH");
            return warnings;
        }
        List<BusinessPresentationResult> groups = result.getGroups();
        if (!matchesScope(plan == null ? null : plan.getMealScope(), groups)) {
            warnings.add("PLAN_RESULT_MISMATCH");
        }
        boolean hasItem = false;
        boolean onlyRice = true;
        for (BusinessPresentationResult group : groups) {
            for (BusinessPresentationResult item : group.getItems()) {
                hasItem = true;
                if (!"RICE".equals(item.getDishTypeCode())) onlyRice = false;
            }
        }
        if (hasItem && onlyRice) warnings.add("MENU_RESULT_IMPLAUSIBLE");
        return warnings;
    }

    /** 校验 QueryPlan 餐次范围与公共菜单分组完全一致。 */
    private boolean matchesScope(MealScope scope,
                                 List<BusinessPresentationResult> groups) {
        if (scope == MealScope.LUNCH) {
            return groups.size() == 1
                && "LUNCH".equals(groups.get(0).getMealTypeCode());
        }
        if (scope == MealScope.DINNER) {
            return groups.size() == 1
                && "DINNER".equals(groups.get(0).getMealTypeCode());
        }
        return groups.size() == 2
            && "LUNCH".equals(groups.get(0).getMealTypeCode())
            && "DINNER".equals(groups.get(1).getMealTypeCode());
    }
}
