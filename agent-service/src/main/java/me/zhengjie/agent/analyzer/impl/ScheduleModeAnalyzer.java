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
public class ScheduleModeAnalyzer implements DiagnosisAnalyzer {

    /**
     * 判断订单排餐模式是否允许目标日期和餐次通过。
     */
    @Override
    public List<DiagnosisReasonDto> analyze(DiagnosisContextDto context) {
        if (context.getOrders() == null || context.getOrders().isEmpty()) {
            return List.of();
        }

        for (Map<String, Object> order : context.getOrders()) {
            if (!Integer.valueOf(1).equals(asInteger(order.get("status")))) {
                continue;
            }
            if (matchesSchedule(context.getRecordDate(), context.getMealType(), order)) {
                return List.of();
            }
        }

        Map<String, Object> representative = context.getOrders().get(0);
        DiagnosisReasonDto reason = new DiagnosisReasonDto();
        reason.setCode("SCHEDULE_MODE_MISMATCH");
        reason.setTitle("排餐模式不匹配");
        reason.setLevel("MEDIUM");
        reason.setDescription("订单排餐模式不允许目标日期生效。");
        reason.setSuggestion("核对 scheduleMode 和 deliveryDates 配置。");
        reason.setEvidence(List.of(
            new DiagnosisEvidenceDto("目标日期", context.getRecordDate()),
            new DiagnosisEvidenceDto("目标餐次", context.getMealType()),
            new DiagnosisEvidenceDto("订单编号", String.valueOf(representative.getOrDefault("orderCode", "-"))),
            new DiagnosisEvidenceDto("排餐模式", String.valueOf(representative.getOrDefault("scheduleMode", "-")))
        ));
        return List.of(reason);
    }

    /**
     * 按排餐模式判断目标日期是否可排。
     */
    private boolean matchesSchedule(String recordDate, String mealType, Map<String, Object> order) {
        String scheduleMode = String.valueOf(order.getOrDefault("scheduleMode", "DAILY"));
        LocalDate date = LocalDate.parse(recordDate);
        return switch (scheduleMode) {
            case "DAILY" -> true;
            case "WEEKDAY" -> !isWeekend(date.getDayOfWeek());
            case "WEEKEND" -> isWeekend(date.getDayOfWeek());
            case "SCHEDULE" -> matchesDeliveryDates(order.get("deliveryDates"), recordDate, mealType);
            default -> true;
        };
    }

    /**
     * 在指定送餐日期列表中查找目标日期或目标餐次。
     */
    private boolean matchesDeliveryDates(Object deliveryDatesValue, String recordDate, String mealType) {
        if (!(deliveryDatesValue instanceof List<?> deliveryDates)) {
            return false;
        }
        for (Object item : deliveryDates) {
            if (item instanceof String date && recordDate.equals(date)) {
                return true;
            }
            if (item instanceof Map<?, ?> map) {
                Object date = map.get("date");
                if (!recordDate.equals(String.valueOf(date))) {
                    continue;
                }
                Object mealTypesValue = map.get("mealTypes");
                if (mealTypesValue instanceof List<?> mealTypes && mealTypes.contains(mealType)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断日期是否落在周末。
     */
    private boolean isWeekend(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
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
