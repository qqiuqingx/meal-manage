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
     * 按订单、日期和午晚餐生成数量日历单元格，并应用购买餐数顺序分配。
     *
     * @param order 客户订单
     * @param excludedDates 客户排除日期
     * @param statsMonth 查询月份
     * @param additions 截至查询月底的该订单全部有效人工数量覆盖
     * @return 订单有效期内有业务意义的数量单元格，基础份数保留其他覆盖和客户统一停餐
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
            for (String mealType : ALL_MEAL_TYPES) {
                if (!orderContainsMealType(order, mealType)) {
                    continue;
                }
                String startMealType = OrderStartMealTypeUtil.normalizeStartMealType(order.getMealType(), order.getStartMealType());
                if (!OrderStartMealTypeUtil.hasStartedForMeal(order.getStartDate(), startMealType, current, mealType)) {
                    continue;
                }

                String key = cellKey(current, mealType);
                boolean excluded = isExcluded(excludedDates, current, mealType);
                int quantity = quantities.getOrDefault(key, 0);
                CustomerMealScheduleAddition addition = additionByCell.get(key);
                int baseQuantity = addition == null
                        ? quantity
                        : buildOrderQuantities(order, excludedDates, withoutCellAddition(additions, addition), end)
                                .getOrDefault(key, 0);
                CustomerMealScheduleCellDto cell = new CustomerMealScheduleCellDto();
                cell.setOrderId(order.getId());
                cell.setDate(current.toString());
                cell.setMealType(mealType);
                cell.setBaseQuantity(baseQuantity);
                cell.setQuantity(quantity);
                cell.setSoupQuantity(quantity > 0 && addition != null ? addition.getSoupQuantity() : null);
                cell.setDefaultIncludesSoup(!"BREAKFAST".equals(mealType) && safeInt(order.getSoupCount()) > 0);
                cell.setGeneratedCount(0);
                cell.setFailedCount(0);
                cell.setVerifiedCount(0);
                cell.setManualOverride(addition != null);
                cell.setCustomerExcluded(excluded);
                cell.setOrderExcluded(addition != null && addition.getQuantity() != null
                        && addition.getQuantity() == 0);
                cells.add(cell);
            }
            current = current.plusDays(1);
        }
        return cells;
    }

    /**
     * 移除本次计算单元格的覆盖，其余月份和单元格覆盖继续参与数量池分配。
     *
     * @param additions 订单全部历史覆盖
     * @param target 要从基础量计算中暂时移除的覆盖
     * @return 除目标单元格外的有效覆盖
     */
    private static List<CustomerMealScheduleAddition> withoutCellAddition(
            List<CustomerMealScheduleAddition> additions, CustomerMealScheduleAddition target) {
        if (additions == null || additions.isEmpty()) {
            return Collections.emptyList();
        }
        List<CustomerMealScheduleAddition> result = new ArrayList<>(additions.size());
        for (CustomerMealScheduleAddition addition : additions) {
            if (addition == null || addition == target
                    || target.getRecordDate().equals(addition.getRecordDate())
                    && target.getMealType().equals(addition.getMealType())
                    && (target.getOrderId() == null || target.getOrderId().equals(addition.getOrderId()))) {
                continue;
            }
            result.add(addition);
        }
        return result;
    }

    /**
     * 从首次送餐起按早餐和午晚餐两个购买池顺序分配目标份数，午晚餐池先扣除导入前历史核销基数。
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
        boolean paused = Integer.valueOf(4).equals(order.getStatus());
        if (lastDate.isBefore(order.getStartDate())) {
            return Collections.emptyMap();
        }
        Map<String, CustomerMealScheduleAddition> additionByCell = new LinkedHashMap<>();
        if (additions != null) {
            for (CustomerMealScheduleAddition addition : additions) {
                if (addition != null && addition.getRecordDate() != null && addition.getMealType() != null
                        && (order.getId() == null || order.getId().equals(addition.getOrderId()))) {
                    int quantity = addition.getQuantity() == null ? 1 : addition.getQuantity();
                    if (quantity < 0) {
                        throw new BadRequestException("目标份数不能小于0");
                    }
                    if (addition.getSoupQuantity() != null
                            && (addition.getSoupQuantity() < 0 || addition.getSoupQuantity() > quantity)) {
                        throw new BadRequestException("含汤份数必须在0到目标份数之间");
                    }
                    additionByCell.put(cellKey(addition.getRecordDate(), addition.getMealType()), addition);
                }
            }
        }
        int breakfastRemaining = safeInt(order.getBreakfastCount());
        int lunchDinnerRemaining = Math.max(safeInt(order.getLunchDinnerCount())
                - safeInt(order.getImportedVerifiedCount()), 0);
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
                boolean baseScheduleActive = !paused || order.getPauseEffectiveDate() != null
                        && !date.isAfter(order.getPauseEffectiveDate().minusDays(1));
                boolean baseScheduled = baseScheduleActive && scheduleModeMatches(order, date)
                        && scheduledDeliveryDateContainsMealType(order, date, mealType);
                if (!baseScheduled && addition == null) {
                    continue;
                }
                int quantity = addition == null || addition.getQuantity() == null ? 1 : addition.getQuantity();
                if (quantity == 0) {
                    result.put(cellKey(date, mealType), 0);
                    continue;
                }
                int remaining = "BREAKFAST".equals(mealType) ? breakfastRemaining : lunchDinnerRemaining;
                if (quantity > remaining) {
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
    private static class DeliveryDateWithMealTypes {
        private String date;
        private List<String> mealTypes;
    }
}
