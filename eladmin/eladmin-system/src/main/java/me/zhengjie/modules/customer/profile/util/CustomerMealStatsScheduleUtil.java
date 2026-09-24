package me.zhengjie.modules.customer.profile.util;

import com.alibaba.fastjson2.JSON;
import lombok.Data;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.util.OrderStartMealTypeUtil;
import me.zhengjie.modules.customer.profile.domain.CustomerMealScheduleAddition;
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

    /**
     * 按订单模式、客户排除和历史数量覆盖构建当前月仍有购买餐数的计划日期。
     *
     * @param orders 当前客户订单
     * @param excludedDates 客户完整排除日期
     * @param statsMonth 查询月份
     * @param mealBucket 早餐或午晚餐餐数池
     * @param additionsByOrder 截至当前月末的历史数量覆盖
     * @return 当前月仍有目标份数的日期与餐次
     */
    public static List<ScheduleDay> buildMonthScheduleDays(List<CustomerOrder> orders,
                                                           List<ExcludedDateDto> excludedDates,
                                                           String statsMonth,
                                                           String mealBucket,
                                                           Map<Long, List<CustomerMealScheduleAddition>> additionsByOrder) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        YearMonth month = parseMonth(statsMonth);
        LocalDate monthEnd = month.atEndOfMonth();
        Map<String, ScheduleDay> dayMap = new LinkedHashMap<>();

        for (CustomerOrder order : orders) {
            if (order == null) {
                continue;
            }
            List<CustomerMealScheduleAddition> additions = additionsByOrder == null
                    ? Collections.emptyList() : additionsByOrder.getOrDefault(order.getId(), Collections.emptyList());
            Map<String, Integer> quantities = buildOrderQuantities(order, excludedDates, additions, monthEnd);
            for (LocalDate date = month.atDay(1); !date.isAfter(monthEnd); date = date.plusDays(1)) {
                for (String mealType : mealTypesForBucket(mealBucket)) {
                    if (quantities.getOrDefault(cellKey(date, mealType), 0) > 0) {
                        ScheduleDay day = dayMap.computeIfAbsent(date.toString(), ScheduleDay::new);
                        day.addMealTypes(Collections.singletonList(mealType));
                    }
                }
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
        return buildMonthScheduleDays(orders, Collections.emptyList(), statsMonth, mealBucket, Collections.emptyMap());
    }

    /**
     * 按订单、日期和午晚餐生成数量日历单元格，并应用购买餐数顺序分配。
     *
     * @param order 客户订单
     * @param excludedDates 客户排除日期
     * @param statsMonth 查询月份
     * @param additions 截至查询月底的该订单全部有效人工数量覆盖
     * @return 订单有效期内的午晚餐数量单元格
     */
    public static List<CustomerMealScheduleCellDto> buildMonthMealScheduleCells(CustomerOrder order,
                                                                                List<ExcludedDateDto> excludedDates,
                                                                                String statsMonth,
                                                                                List<CustomerMealScheduleAddition> additions) {
        if (order == null) {
            return Collections.emptyList();
        }
        YearMonth month = parseMonth(statsMonth);
        LocalDate start = maxDate(month.atDay(1), order.getStartDate());
        LocalDate end = minDate(month.atEndOfMonth(), order.getEndDate());
        if (start == null || end == null || start.isAfter(end)) {
            return Collections.emptyList();
        }

        Map<String, Integer> baseQuantities = buildOrderQuantities(order, Collections.emptyList(),
                Collections.emptyList(), end);
        Map<String, Integer> quantities = buildOrderQuantities(order, excludedDates, additions, end);
        Map<String, CustomerMealScheduleAddition> additionByCell = new LinkedHashMap<>();
        if (additions != null) {
            for (CustomerMealScheduleAddition addition : additions) {
                if (addition != null && addition.getRecordDate() != null && addition.getMealType() != null) {
                    additionByCell.put(cellKey(addition.getRecordDate(), addition.getMealType()), addition);
                }
            }
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

                String key = cellKey(current, mealType);
                boolean excluded = isExcluded(excludedDates, current, mealType);
                int baseQuantity = baseQuantities.getOrDefault(key, 0);
                int quantity = quantities.getOrDefault(key, 0);
                CustomerMealScheduleAddition addition = additionByCell.get(key);
                CustomerMealScheduleCellDto cell = new CustomerMealScheduleCellDto();
                cell.setOrderId(order.getId());
                cell.setDate(current.toString());
                cell.setMealType(mealType);
                cell.setBaseQuantity(baseQuantity);
                cell.setQuantity(quantity);
                cell.setSoupQuantity(quantity > 0 && addition != null ? addition.getSoupQuantity() : null);
                cell.setDefaultIncludesSoup(safeInt(order.getSoupCount()) > 0);
                cell.setGeneratedCount(0);
                cell.setFailedCount(0);
                cell.setVerifiedCount(0);
                cell.setManualOverride(quantity > 0 && addition != null);
                cell.setExcluded(excluded);
                cells.add(cell);
            }
            current = current.plusDays(1);
        }
        return cells;
    }

    /**
     * 从首次送餐起按早餐和午晚餐两个购买池顺序分配目标份数。
     *
     * @param order 来源订单
     * @param excludedDates 客户完整排除日期
     * @param additions 截至 endDate 的该订单全部有效数量覆盖
     * @param endDate 计算终点（含当天）
     * @return 日期#餐次到目标份数的映射，未出现的单元格为零份
     */
    public static Map<String, Integer> buildOrderQuantities(CustomerOrder order,
                                                            List<ExcludedDateDto> excludedDates,
                                                            List<CustomerMealScheduleAddition> additions,
                                                            LocalDate endDate) {
        if (order == null || order.getStartDate() == null || endDate == null) {
            return Collections.emptyMap();
        }
        LocalDate lastDate = minDate(endDate, order.getEndDate());
        if (Integer.valueOf(4).equals(order.getStatus()) && order.getPauseEffectiveDate() != null) {
            lastDate = minDate(lastDate, order.getPauseEffectiveDate().minusDays(1));
        }
        if (lastDate.isBefore(order.getStartDate())) {
            return Collections.emptyMap();
        }
        Map<String, CustomerMealScheduleAddition> additionByCell = new LinkedHashMap<>();
        if (additions != null) {
            for (CustomerMealScheduleAddition addition : additions) {
                if (addition != null && addition.getRecordDate() != null && addition.getMealType() != null
                        && (order.getId() == null || order.getId().equals(addition.getOrderId()))) {
                    additionByCell.put(cellKey(addition.getRecordDate(), addition.getMealType()), addition);
                }
            }
        }
        int breakfastRemaining = safeInt(order.getBreakfastCount());
        int lunchDinnerRemaining = safeInt(order.getLunchDinnerCount());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (LocalDate date = order.getStartDate(); !date.isAfter(lastDate); date = date.plusDays(1)) {
            for (String mealType : ALL_MEAL_TYPES) {
                if (!orderContainsMealType(order, mealType) || !OrderStartMealTypeUtil.hasStartedForMeal(
                        order.getStartDate(), OrderStartMealTypeUtil.normalizeStartMealType(
                                order.getMealType(), order.getStartMealType()), date, mealType)
                        || isExcluded(excludedDates, date, mealType)) {
                    continue;
                }
                CustomerMealScheduleAddition addition = additionByCell.get(cellKey(date, mealType));
                boolean baseScheduled = scheduleModeMatches(order, date)
                        && scheduledDeliveryDateContainsMealType(order, date, mealType);
                if (!baseScheduled && addition == null) {
                    continue;
                }
                int quantity = addition == null || addition.getQuantity() == null ? 1 : addition.getQuantity();
                int remaining = "BREAKFAST".equals(mealType) ? breakfastRemaining : lunchDinnerRemaining;
                if (quantity <= 0 || quantity > remaining) {
                    continue;
                }
                result.put(cellKey(date, mealType), quantity);
                if ("BREAKFAST".equals(mealType)) {
                    breakfastRemaining -= quantity;
                } else {
                    lunchDinnerRemaining -= quantity;
                }
            }
            if (breakfastRemaining == 0 && lunchDinnerRemaining == 0) {
                break;
            }
        }
        return result;
    }

    /**
     * 构建计划日期餐次的唯一键。
     *
     * @param date 配送日期
     * @param mealType 早餐、午餐或晚餐
     * @return 日期#餐次
     */
    public static String cellKey(LocalDate date, String mealType) {
        return date + "#" + mealType;
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
