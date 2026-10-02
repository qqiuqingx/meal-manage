package me.zhengjie.modules.customer.order.util;

import me.zhengjie.modules.customer.order.domain.CustomerOrder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 订单开始餐次工具类。
 */
public final class OrderStartMealTypeUtil {

    public static final String ORDER_MEAL_TYPE_ALL = "ALL";
    public static final String ORDER_MEAL_TYPE_LUNCH_DINNER = "LUNCH_DINNER";
    public static final String ORDER_MEAL_TYPE_LUNCH = "LUNCH";
    public static final String ORDER_MEAL_TYPE_DINNER = "DINNER";

    public static final String MEAL_TYPE_BREAKFAST = "BREAKFAST";
    public static final String MEAL_TYPE_LUNCH = "LUNCH";
    public static final String MEAL_TYPE_DINNER = "DINNER";

    private OrderStartMealTypeUtil() {
    }

    /**
     * 计算可承接排餐的开始日期；导入订单在保留业务开始日的同时跳过已承接的历史期间。
     *
     * @param order 客户订单，普通订单的导入日期为空
     * @return max(订单开始日, 导入日期次日)；缺少订单或开始日时返回 null
     */
    public static LocalDate resolveScheduleStartDate(CustomerOrder order) {
        if (order == null || order.getStartDate() == null) {
            return null;
        }
        LocalDate startDate = order.getStartDate();
        if (order.getImportDate() == null) {
            return startDate;
        }
        LocalDate importNextDate = order.getImportDate().plusDays(1);
        return startDate.isAfter(importNextDate) ? startDate : importNextDate;
    }

    /**
     * 计算来源表能覆盖的核销日期，过去月份的表不能包含后续月份消费。
     * @param sourceMonth 来源月份，未版本化的既有订单可为空
     * @param importDate 该快照采用的日级边界
     * @return 不晚于月末的覆盖日；没有导入边界时为空
     */
    public static LocalDate importCoverageEnd(LocalDate sourceMonth, LocalDate importDate) {
        if (importDate == null || sourceMonth == null) {
            return importDate;
        }
        LocalDate monthEnd = YearMonth.from(sourceMonth).atEndOfMonth();
        return importDate.isBefore(monthEnd) ? importDate : monthEnd;
    }

    /**
     * 查询订单当前采用的数量快照所覆盖的历史日期。
     * @param order 当前订单
     * @return 历史核销汇总覆盖日；普通订单为空
     */
    public static LocalDate resolveImportCoverageEnd(CustomerOrder order) {
        return order == null ? null : importCoverageEnd(order.getImportMonth(), order.getImportDate());
    }

    public static String normalizeOrderMealType(String orderMealType) {
        return isBlank(orderMealType) ? ORDER_MEAL_TYPE_ALL : orderMealType.trim().toUpperCase();
    }

    public static String defaultStartMealType(String orderMealType) {
        switch (normalizeOrderMealType(orderMealType)) {
            case ORDER_MEAL_TYPE_LUNCH_DINNER:
            case ORDER_MEAL_TYPE_LUNCH:
                return MEAL_TYPE_LUNCH;
            case ORDER_MEAL_TYPE_DINNER:
                return MEAL_TYPE_DINNER;
            case ORDER_MEAL_TYPE_ALL:
            default:
                return MEAL_TYPE_BREAKFAST;
        }
    }

    public static String normalizeStartMealType(String orderMealType, String startMealType) {
        return isBlank(startMealType) ? defaultStartMealType(orderMealType) : startMealType.trim().toUpperCase();
    }

    public static boolean isStartMealTypeAllowed(String orderMealType, String startMealType) {
        return allowedStartMealTypes(orderMealType).contains(normalizeStartMealType(orderMealType, startMealType));
    }

    public static List<String> allowedStartMealTypes(String orderMealType) {
        switch (normalizeOrderMealType(orderMealType)) {
            case ORDER_MEAL_TYPE_LUNCH_DINNER:
                return Arrays.asList(MEAL_TYPE_LUNCH, MEAL_TYPE_DINNER);
            case ORDER_MEAL_TYPE_LUNCH:
                return Collections.singletonList(MEAL_TYPE_LUNCH);
            case ORDER_MEAL_TYPE_DINNER:
                return Collections.singletonList(MEAL_TYPE_DINNER);
            case ORDER_MEAL_TYPE_ALL:
            default:
                return Arrays.asList(MEAL_TYPE_BREAKFAST, MEAL_TYPE_LUNCH, MEAL_TYPE_DINNER);
        }
    }

    public static boolean hasStartedForMeal(LocalDate startDate, String startMealType, LocalDate targetDate, String targetMealType) {
        if (startDate == null || targetDate == null) {
            return true;
        }
        if (targetDate.isBefore(startDate)) {
            return false;
        }
        if (targetDate.isAfter(startDate)) {
            return true;
        }
        return mealOrder(targetMealType) >= mealOrder(normalizeStartMealType(ORDER_MEAL_TYPE_ALL, startMealType));
    }

    public static String mealTypeDesc(String mealType) {
        if (MEAL_TYPE_BREAKFAST.equals(mealType)) {
            return "早餐";
        }
        if (MEAL_TYPE_LUNCH.equals(mealType)) {
            return "午餐";
        }
        if (MEAL_TYPE_DINNER.equals(mealType)) {
            return "晚餐";
        }
        if (ORDER_MEAL_TYPE_LUNCH_DINNER.equals(mealType)) {
            return "午餐+晚餐";
        }
        if (ORDER_MEAL_TYPE_ALL.equals(mealType)) {
            return "早+午餐+晚餐";
        }
        return mealType;
    }

    private static int mealOrder(String mealType) {
        if (MEAL_TYPE_BREAKFAST.equals(mealType)) {
            return 1;
        }
        if (MEAL_TYPE_LUNCH.equals(mealType)) {
            return 2;
        }
        if (MEAL_TYPE_DINNER.equals(mealType)) {
            return 3;
        }
        return Integer.MAX_VALUE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
