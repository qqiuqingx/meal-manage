package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PlanFailedAnalyzer implements DiagnosisAnalyzer {

    /**
     * 检查排餐主单和客户排餐记录是否已经明确失败。
     */
    @Override
    public List<DiagnosisReasonDto> analyze(DiagnosisContextDto context) {
        List<DiagnosisReasonDto> reasons = new ArrayList<>();

        if (isFailed(context.getMealPlan())) {
            reasons.add(buildReason("排餐生成失败", "PLAN_FAILED", "排餐主单状态为失败。", context.getMealPlan(), "排餐主单"));
        }

        for (Map<String, Object> customerPlan : context.getCustomerPlans()) {
            if (!Integer.valueOf(0).equals(asInteger(customerPlan.get("status")))) {
                continue;
            }
            reasons.add(buildReason("排餐生成失败", "PLAN_FAILED", "客户排餐记录状态为失败。", customerPlan, "客户排餐"));
        }

        return reasons;
    }

    /**
     * 判断排餐主单是否处于失败状态。
     */
    private boolean isFailed(Map<String, Object> mealPlan) {
        if (mealPlan == null || mealPlan.isEmpty()) {
            return false;
        }
        Object status = mealPlan.get("status");
        return "FAILED".equals(String.valueOf(status));
    }

    /**
     * 构造失败原因和对应证据。
     */
    private DiagnosisReasonDto buildReason(String title, String code, String description,
                                           Map<String, Object> source, String sourceType) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode(code);
        reason.setTitle(title);
        reason.setLevel("HIGH");
        reason.setDescription(description);
        reason.setSuggestion("查看 failReason 和排餐主任务日志。");
        reason.setEvidence(List.of(
            new DiagnosisEvidenceDto("来源类型", sourceType),
            new DiagnosisEvidenceDto("失败原因", String.valueOf(source.getOrDefault("failReason", "-"))),
            new DiagnosisEvidenceDto("状态", String.valueOf(source.getOrDefault("status", "-")))
        ));
        return reason;
    }

    /**
     * 将通用对象转成整数，方便兼容 Map 中的数值类型。
     */
    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
