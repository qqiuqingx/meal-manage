package me.zhengjie.agent.analyzer.impl;

import me.zhengjie.agent.analyzer.DiagnosisAnalyzer;
import me.zhengjie.agent.domain.dto.DiagnosisContextDto;
import me.zhengjie.agent.domain.dto.DiagnosisEvidenceDto;
import me.zhengjie.agent.domain.dto.DiagnosisReasonDto;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OrderEffectiveAnalyzer implements DiagnosisAnalyzer {

    /**
     * 判断订单是否在目标日期和餐次范围内生效。
     */
    @Override
    public List<DiagnosisReasonDto> analyze(DiagnosisContextDto context) {
        if (context.getOrders() == null || context.getOrders().isEmpty()) {
            return List.of();
        }

        for (Map<String, Object> order : context.getOrders()) {
            if (!isActiveOrder(order)) {
                continue;
            }
            if (!isDateInRange(context.getRecordDate(), order)) {
                return List.of(buildReason("订单未在目标日期生效", "ORDER_NOT_EFFECTIVE", "目标日期不在订单有效期内", context, order));
            }
            if (!isMealTypeMatched(context.getMealType(), order)) {
                return List.of(buildReason("订单未在目标日期生效", "ORDER_NOT_EFFECTIVE", "订单餐次与目标餐次不匹配", context, order));
            }
            if (!isStartMealTypeMatched(context.getMealType(), context.getRecordDate(), order)) {
                return List.of(buildReason("订单未在目标日期生效", "ORDER_NOT_EFFECTIVE", "订单开始餐次不允许目标餐次生效", context, order));
            }
            return List.of();
        }

        return List.of(buildReason("订单未在目标日期生效", "ORDER_NOT_EFFECTIVE", "没有找到有效订单", context, context.getOrders().get(0)));
    }

    /**
     * 判断订单是否处于进行中状态。
     */
    private boolean isActiveOrder(Map<String, Object> order) {
        return Integer.valueOf(1).equals(asInteger(order.get("status")));
    }

    /**
     * 判断目标日期是否落在订单有效期内。
     */
    private boolean isDateInRange(String recordDate, Map<String, Object> order) {
        LocalDate target = LocalDate.parse(recordDate);
        LocalDate start = LocalDate.parse(String.valueOf(order.get("startDate")));
        Object endDateValue = order.get("endDate");
        if (endDateValue == null) {
            return !target.isBefore(start);
        }
        LocalDate end = LocalDate.parse(String.valueOf(endDateValue));
        return !target.isBefore(start) && !target.isAfter(end);
    }

    /**
     * 判断订单餐次范围是否覆盖当前餐次。
     */
    private boolean isMealTypeMatched(String mealType, Map<String, Object> order) {
        String orderMealType = String.valueOf(order.getOrDefault("mealType", "ALL"));
        return switch (orderMealType) {
            case "ALL" -> true;
            case "LUNCH_DINNER" -> "LUNCH".equals(mealType) || "DINNER".equals(mealType);
            default -> orderMealType.equals(mealType);
        };
    }

    /**
     * 判断订单开始餐次是否允许目标日期当天的餐次生效。
     */
    private boolean isStartMealTypeMatched(String mealType, String recordDate, Map<String, Object> order) {
        String startMealType = String.valueOf(order.getOrDefault("startMealType", "BREAKFAST"));
        LocalDate target = LocalDate.parse(recordDate);
        LocalDate start = LocalDate.parse(String.valueOf(order.get("startDate")));
        if (!target.equals(start)) {
            return true;
        }
        return switch (startMealType) {
            case "BREAKFAST" -> true;
            case "LUNCH" -> "LUNCH".equals(mealType) || "DINNER".equals(mealType);
            case "DINNER" -> "DINNER".equals(mealType);
            default -> true;
        };
    }

    /**
     * 构造订单未生效的诊断原因和证据。
     */
    private DiagnosisReasonDto buildReason(String title, String code, String description,
                                           DiagnosisContextDto context, Map<String, Object> order) {
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode(code);
        reason.setTitle(title);
        reason.setLevel("HIGH");
        reason.setDescription(description);
        reason.setSuggestion("先核对订单有效期、餐次和开始餐次配置。");
        List<DiagnosisEvidenceDto> evidence = new ArrayList<>();
        evidence.add(new DiagnosisEvidenceDto("目标日期", context.getRecordDate()));
        evidence.add(new DiagnosisEvidenceDto("目标餐次", context.getMealType()));
        evidence.add(new DiagnosisEvidenceDto("订单编号", String.valueOf(order.getOrDefault("orderCode", "-"))));
        evidence.add(new DiagnosisEvidenceDto("订单开始日期", String.valueOf(order.getOrDefault("startDate", "-"))));
        evidence.add(new DiagnosisEvidenceDto("订单结束日期", String.valueOf(order.getOrDefault("endDate", "-"))));
        evidence.add(new DiagnosisEvidenceDto("订单餐次", String.valueOf(order.getOrDefault("mealType", "-"))));
        evidence.add(new DiagnosisEvidenceDto("开始餐次", String.valueOf(order.getOrDefault("startMealType", "-"))));
        reason.setEvidence(evidence);
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
