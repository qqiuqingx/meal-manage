package me.zhengjie.modules.customer.profile.util;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.profile.domain.dto.CustomerMealScheduleCellDto;
import me.zhengjie.modules.customer.profile.domain.dto.ExcludedDateDto;
import me.zhengjie.modules.meal.util.ScheduleKeyUtil;
import me.zhengjie.utils.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户用餐统计页的月度应排餐日期计算。
 */
public final class CustomerMealStatsScheduleUtil {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<String> ALL_MEAL_TYPES = Arrays.asList("BREAKFAST", "LUNCH", "DINNER");

    private CustomerMealStatsScheduleUtil() {
    }

    public static List<ScheduleDay> buildMonthScheduleDays(List<CustomerOrder> orders,
                                                           List<ExcludedDateDto> excludedDates,
                                                           String statsMonth,
                                                           String mealBucket) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        YearMonth month = parseMonth(statsMonth);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        Map<String, ScheduleDay> dayMap = new LinkedHashMap<>();

        for (CustomerOrder order : orders) {
            if (order == null) {
                continue;
            }
            LocalDate start = maxDate(monthStart, order.getStartDate());
            LocalDate end = minDate(monthEnd, order.getEndDate());
            if (start == null || end == null || start.isAfter(end)) {
                continue;
            }
            LocalDate current = start;
            while (!current.isAfter(end)) {
                List<String> mealTypes = buildMatchedMealTypes(order, current, excludedDates, mealBucket);
                if (!mealTypes.isEmpty()) {
                    String date = current.toString();
                    ScheduleDay day = dayMap.computeIfAbsent(date, key -> new ScheduleDay(date));
                    day.addMealTypes(mealTypes);
                }
                current = current.plusDays(1);
            }
        }

        return new ArrayList<>(dayMap.values());
    }

    /**
     * 构建不应用客户排除日期的月度基础应排餐日期，用于日历编辑时展示可恢复餐次。
     */
    public static List<ScheduleDay> buildMonthBaseScheduleDays(List<CustomerOrder> orders,
                                                               String statsMonth,
                                                               String mealBucket) {
        return buildMonthScheduleDays(orders, Collections.emptyList(), statsMonth, mealBucket);
    }

    /**
     * 按订单、日期和午晚餐生成数量日历单元格，基础计划为一份，未命中的日期为零份。
     *
     * @param order 客户订单
     * @param excludedDates 客户排除日期
     * @param statsMonth 查询月份
     * @return 订单有效期内的午晚餐数量单元格
     */
    public static List<CustomerMealScheduleCellDto> buildMonthMealScheduleCells(CustomerOrder order,
                                                                                List<ExcludedDateDto> excludedDates,
                                                                                String statsMonth) {
        if (order == null) {
            return Collections.emptyList();
        }
        YearMonth month = parseMonth(statsMonth);
        LocalDate start = maxDate(month.atDay(1), order.getStartDate());
        LocalDate end = minDate(month.atEndOfMonth(), order.getEndDate());
        if (start == null || end == null || start.isAfter(end)) {
            return Collections.emptyList();
        }

        List<CustomerMealScheduleCellDto> cells = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            for (String mealType : Arrays.asList("LUNCH", "DINNER")) {
                if (!orderContainsMealType(order, mealType)) {
                    continue;
                }
                String startMealType = OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType());
                if (!OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(), startMealType, current, mealType)) {
                    continue;
                }

                boolean baseScheduled = scheduleModeMatches(order, current)
                        && scheduledDeliveryDateContainsMealType(order, current, mealType);
                boolean excluded = isExcluded(excludedDates, current, mealType);
                int baseQuantity = baseScheduled ? 1 : 0;
                CustomerMealScheduleCellDto cell = new CustomerMealScheduleCellDto();
                cell.setOrderId(order.getId());
                cell.setDate(current.toString());
                cell.setMealType(mealType);
                cell.setBaseQuantity(baseQuantity);
                cell.setQuantity(excluded ? 0 : baseQuantity);
                cell.setDefaultIncludesSoup(safeInt(order.getSoupCount()) > 0);
                cell.setGeneratedCount(0);
                cell.setFailedCount(0);
                cell.setVerifiedCount(0);
                cell.setManualOverride(false);
                cell.setExcluded(excluded);
                cells.add(cell);
            }
            current = current.plusDays(1);
        }
        return cells;
    }

    private static YearMonth parseMonth(String statsMonth) {
        if (StringUtils.isBlank(statsMonth)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(statsMonth, MONTH_FORMATTER);
        } catch (Exception e) {
            throw new BadRequestException("统计月份格式错误，请使用 yyyy-MM 格式");
        }
    }

    private static List<String> buildMatchedMealTypes(CustomerOrder order,
                                                     LocalDate date,
                                                     List<ExcludedDateDto> excludedDates,
                                                     String mealBucket) {
        if (!scheduleModeMatches(order, date)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String mealType : mealTypesForBucket(mealBucket)) {
            if (!orderContainsMealType(order, mealType)) {
                continue;
            }
            String startMealType = OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType());
            if (!OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(), startMealType, date, mealType)) {
                continue;
            }
            if (!scheduledDeliveryDateContainsMealType(order, date, mealType)) {
                continue;
            }
            if (isExcluded(excludedDates, date, mealType)) {
                continue;
            }
            result.add(mealType);
        }
        return result;
    }

    private static List<String> mealTypesForBucket(String mealBucket) {
        if ("BREAKFAST".equals(mealBucket)) {
            return Collections.singletonList("BREAKFAST");
        }
        return Arrays.asList("LUNCH", "DINNER");
    }

    private static boolean orderContainsMealType(CustomerOrder order, String mealType) {
        String orderMealType = OrderStartMealTypeUtil.normalizeOrderMealType(order.getMealType());
        if ("BREAKFAST".equals(mealType)) {
            return "ALL".equals(orderMealType) && safeInt(order.getBreakfastCount()) > 0;
        }
        if ("LUNCH".equals(mealType)) {
            return ("ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType) || "LUNCH".equals(orderMealType))
                    && safeInt(order.getLunchDinnerCount()) > 0;
        }
        if ("DINNER".equals(mealType)) {
            return ("ALL".equals(orderMealType) || "LUNCH_DINNER".equals(orderMealType) || "DINNER".equals(orderMealType))
                    && safeInt(order.getLunchDinnerCount()) > 0;
        }
        return false;
    }

    private static boolean scheduleModeMatches(CustomerOrder order, LocalDate date) {
        String scheduleMode = order.getScheduleMode();
        if (StringUtils.isBlank(scheduleMode) || "DAILY".equals(scheduleMode)) {
            return true;
        }
        if ("SCHEDULE".equals(scheduleMode)) {
            return parseDeliveryDates(order.getDeliveryDates()).containsKey(date.toString());
        }
        if ("WEEKDAY".equals(scheduleMode)) {
            return ScheduleKeyUtil.isWeekday(date);
        }
        if ("WEEKEND".equals(scheduleMode)) {
            return ScheduleKeyUtil.isWeekend(date);
        }
        return false;
    }

    private static boolean scheduledDeliveryDateContainsMealType(CustomerOrder order, LocalDate date, String mealType) {
        if (!"SCHEDULE".equals(order.getScheduleMode())) {
            return true;
        }
        List<String> mealTypes = parseDeliveryDates(order.getDeliveryDates()).get(date.toString());
        return mealTypes != null && mealTypes.contains(mealType);
    }

    private static Map<String, List<String>> parseDeliveryDates(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[{")) {
                List<DeliveryDateWithMealTypes> items = JSON.parseArray(json, DeliveryDateWithMealTypes.class);
                for (DeliveryDateWithMealTypes item : items) {
                    if (item == null || StringUtils.isBlank(item.getDate())) {
                        continue;
                    }
                    List<String> mealTypes = item.getMealTypes() == null || item.getMealTypes().isEmpty()
                            ? ALL_MEAL_TYPES
                            : item.getMealTypes();
                    result.put(item.getDate(), mealTypes);
                }
            } else {
                List<String> dates = JSON.parseArray(json, String.class);
                for (String date : dates) {
                    if (StringUtils.isNotBlank(date)) {
                        result.put(date, ALL_MEAL_TYPES);
                    }
                }
            }
        } catch (Exception e) {
            return Collections.emptyMap();
        }
        return result;
    }

    private static boolean isExcluded(List<ExcludedDateDto> excludedDates, LocalDate date, String mealType) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return false;
        }
        String targetDate = date.toString();
        for (ExcludedDateDto excludedDate : excludedDates) {
            if (excludedDate == null || excludedDate.getMealTypes() == null) {
                continue;
            }
            if (targetDate.equals(excludedDate.getDate()) && excludedDate.getMealTypes().contains(mealType)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate maxDate(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate minDate(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    @Data
    public static class ScheduleDay {
        private String date;
        private List<String> mealTypes = new ArrayList<>();
        private List<String> scheduledMealTypes = new ArrayList<>();
        private List<String> baseMealTypes = new ArrayList<>();
        private List<String> excludedMealTypes = new ArrayList<>();
        private List<String> addedMealTypes = new ArrayList<>();

        public ScheduleDay() {
        }

        public ScheduleDay(String date) {
            this.date = date;
        }

        private void addMealTypes(List<String> values) {
            addUniqueMealTypes(mealTypes, values);
        }

        public void addBaseMealTypes(List<String> values) {
            addUniqueMealTypes(baseMealTypes, values);
        }

        public void addExcludedMealType(String mealType) {
            addUniqueMealTypes(excludedMealTypes, Collections.singletonList(mealType));
        }

        public void addAddedMealType(String mealType) {
            addUniqueMealTypes(addedMealTypes, Collections.singletonList(mealType));
        }

        private void addUniqueMealTypes(List<String> target, List<String> values) {
            if (values == null) {
                return;
            }
            for (String value : values) {
                if (StringUtils.isNotBlank(value) && !target.contains(value)) {
                    target.add(value);
                }
            }
        }

        public void addScheduledMealType(String mealType) {
            addUniqueMealTypes(scheduledMealTypes, Collections.singletonList(mealType));
        }
    }

    @Data
    private static class DeliveryDateWithMealTypes {
        private String date;
        private List<String> mealTypes;
    }
}
