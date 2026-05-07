package me.zhengjie.modules.customer.order.util;

import java.time.LocalDate;
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
