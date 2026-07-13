package me.zhengjie.modules.agent.query.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.query.domain.dto.AgentDailyCustomerStatsDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationCountDto;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationDailyRequest;
import me.zhengjie.modules.agent.query.domain.dto.AgentOperationOrderRequest;
import me.zhengjie.modules.agent.query.service.AgentOperationQueryService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.customer.order.domain.CustomerOrder;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealBalanceDto;
import me.zhengjie.modules.customer.order.domain.dto.OrderMealVerifiedCountDto;
import me.zhengjie.modules.customer.order.mapper.CustomerOrderMapper;
import me.zhengjie.modules.customer.order.service.OrderMealBalanceCalculator;
import me.zhengjie.modules.customer.pkg.domain.ParentPackage;
import me.zhengjie.modules.customer.pkg.mapper.ParentPackageMapper;
import me.zhengjie.modules.meal.domain.MealPlan;
import me.zhengjie.modules.meal.domain.MealPlanCustomer;
import me.zhengjie.modules.meal.mapper.MealPlanCustomerMapper;
import me.zhengjie.modules.meal.mapper.MealPlanMapper;
import me.zhengjie.modules.meal.service.MealPlanService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

/** 基于真实排餐和订单表计算运营指标的实现。 */
@Service
@RequiredArgsConstructor
public class AgentOperationQueryServiceImpl implements AgentOperationQueryService {
    private final MealPlanMapper mealPlanMapper;
    private final MealPlanCustomerMapper mealPlanCustomerMapper;
    private final CustomerOrderMapper customerOrderMapper;
    private final ParentPackageMapper parentPackageMapper;
    private final MealPlanService mealPlanService;

    /** {@inheritDoc} */
    @Override
    public AgentDailyCustomerStatsDto dailyCustomers(AgentOperationDailyRequest request) {
        LocalDate date = parseRequired(request == null ? null : request.getRecordDate(), "统计日期");
        String mealType = request == null ? null : normalizeMealType(request.getMealType());
        List<MealPlan> plans = mealPlanMapper.selectList(new LambdaQueryWrapper<MealPlan>()
            .eq(MealPlan::getDeleted, false).eq(MealPlan::getRecordDate, date)
            .eq(mealType != null, MealPlan::getMealType, mealType));
        List<PlanCustomerRow> customerRows = loadPlanCustomerRows(plans);
        Map<Long, CustomerOrder> ordersById = ordersById(customerRows);
        Map<String, List<CustomerOrder>> expectedOrdersByMealType = expectedOrdersByMealType(date, mealType);
        Map<Long, String> packageNames = packageNames(customerRows, ordersById, expectedOrdersByMealType);
        List<String> breakdownDimensions = normalizeBreakdownDimensions(request == null ? null : request.getDimensions());
        Set<Long> scheduled = new HashSet<>();
        Set<Long> verified = new HashSet<>();
        Set<Long> unverified = new HashSet<>();
        Set<String> scheduledCustomerMeals = new HashSet<>();
        Set<String> expectedCustomerMeals = new HashSet<>();
        Set<Long> scopedCustomerIds = AgentCustomerDataScopeContext.customerIds();
        long failures = 0L;
        Map<String, Set<Long>> scheduledByMealType = new HashMap<>();
        Map<String, Set<Long>> verifiedByMealType = new HashMap<>();
        Map<String, Set<Long>> unverifiedByMealType = new HashMap<>();
        Map<String, Set<Long>> expectedByMealType = new HashMap<>();
        Map<String, Long> failuresByMealType = new HashMap<>();
        Map<String, Set<Long>> scheduledByDimension = new HashMap<>();
        Map<String, Set<Long>> verifiedByDimension = new HashMap<>();
        Map<String, Set<Long>> unverifiedByDimension = new HashMap<>();
        Map<String, Set<String>> expectedByDimension = new HashMap<>();
        Map<String, Long> failuresByDimension = new HashMap<>();
        for (PlanCustomerRow planCustomerRow : customerRows) {
            MealPlan plan = planCustomerRow.plan;
            MealPlanCustomer row = planCustomerRow.customer;
            if (row == null || Boolean.TRUE.equals(row.getDeleted()) || row.getCustomerId() == null) continue;
            if (scopedCustomerIds != null && !scopedCustomerIds.contains(row.getCustomerId())) continue;
            String dimensionKey = dimensionKey(breakdownDimensions, plan.getMealType(), row, ordersById.get(row.getOrderId()), packageNames);
            if (Integer.valueOf(1).equals(row.getStatus())) {
                scheduled.add(row.getCustomerId());
                scheduledCustomerMeals.add(customerMealKey(row.getCustomerId(), plan.getMealType()));
                addToBreakdown(scheduledByMealType, plan.getMealType(), row.getCustomerId());
                addToBreakdown(scheduledByDimension, dimensionKey, row.getCustomerId());
                if (Integer.valueOf(1).equals(row.getIsVerified())) {
                    verified.add(row.getCustomerId());
                    addToBreakdown(verifiedByMealType, plan.getMealType(), row.getCustomerId());
                    addToBreakdown(verifiedByDimension, dimensionKey, row.getCustomerId());
                } else {
                    unverified.add(row.getCustomerId());
                    addToBreakdown(unverifiedByMealType, plan.getMealType(), row.getCustomerId());
                    addToBreakdown(unverifiedByDimension, dimensionKey, row.getCustomerId());
                }
            } else if (row.getFailReason() != null && !row.getFailReason().trim().isEmpty()) {
                failures++;
                failuresByMealType.merge(plan.getMealType(), 1L, Long::sum);
                if (dimensionKey != null) failuresByDimension.merge(dimensionKey, 1L, Long::sum);
            }
        }
        for (String expectedMealType : expectedMealTypes(mealType)) {
            for (CustomerOrder expectedOrder : expectedOrdersByMealType.getOrDefault(expectedMealType, List.of())) {
                if (expectedOrder == null || expectedOrder.getCustomerId() == null) continue;
                Long customerId = expectedOrder.getCustomerId();
                if (scopedCustomerIds != null && !scopedCustomerIds.contains(customerId)) continue;
                expectedCustomerMeals.add(customerMealKey(customerId, expectedMealType));
                addToBreakdown(expectedByMealType, expectedMealType, customerId);
                addToCustomerMealBreakdown(expectedByDimension,
                    dimensionKey(breakdownDimensions, expectedMealType, null, expectedOrder, packageNames), customerMealKey(customerId, expectedMealType));
            }
        }
        AgentDailyCustomerStatsDto result = new AgentDailyCustomerStatsDto();
        result.setRecordDate(date.toString()); result.setMealType(mealType);
        result.setScheduledCustomerCount(scheduled.size()); result.setVerifiedCustomerCount(verified.size());
        result.setUnverifiedCustomerCount(unverified.size()); result.setMealPlanFailureCount(failures);
        result.setExpectedCustomerCount(expectedCustomerMeals.size());
        expectedCustomerMeals.removeAll(scheduledCustomerMeals);
        result.setUnscheduledCustomerCount(expectedCustomerMeals.size());
        Map<String, Set<Long>> unscheduledByMealType = subtractExpectedCustomers(expectedByMealType, scheduledByMealType);
        result.getMealTypeBreakdown().putAll(countBreakdown(scheduledByMealType));
        result.getMetricMealTypeBreakdown().put("DAILY_SCHEDULED_CUSTOMER_COUNT", countBreakdown(scheduledByMealType));
        result.getMetricMealTypeBreakdown().put("DAILY_VERIFIED_CUSTOMER_COUNT", countBreakdown(verifiedByMealType));
        result.getMetricMealTypeBreakdown().put("DAILY_UNVERIFIED_CUSTOMER_COUNT", countBreakdown(unverifiedByMealType));
        result.getMetricMealTypeBreakdown().put("DAILY_EXPECTED_CUSTOMER_COUNT", countBreakdown(expectedByMealType));
        result.getMetricMealTypeBreakdown().put("DAILY_UNSCHEDULED_CUSTOMER_COUNT", countBreakdown(unscheduledByMealType));
        result.getMetricMealTypeBreakdown().put("MEAL_PLAN_FAILURE_COUNT", orderedCounts(failuresByMealType));
        result.setBreakdownDimensions(breakdownDimensions);
        if (!breakdownDimensions.isEmpty()) {
            result.getMetricDimensionBreakdown().put("DAILY_SCHEDULED_CUSTOMER_COUNT", countBreakdown(scheduledByDimension));
            result.getMetricDimensionBreakdown().put("DAILY_VERIFIED_CUSTOMER_COUNT", countBreakdown(verifiedByDimension));
            result.getMetricDimensionBreakdown().put("DAILY_UNVERIFIED_CUSTOMER_COUNT", countBreakdown(unverifiedByDimension));
            result.getMetricDimensionBreakdown().put("DAILY_EXPECTED_CUSTOMER_COUNT", countCustomerMealBreakdown(expectedByDimension));
            result.getMetricDimensionBreakdown().put("DAILY_UNSCHEDULED_CUSTOMER_COUNT", countCustomerMealBreakdown(removeScheduledCustomerMeals(expectedByDimension, scheduledCustomerMeals)));
            result.getMetricDimensionBreakdown().put("MEAL_PLAN_FAILURE_COUNT", orderedCounts(failuresByDimension));
        }
        result.setQueriedAt(java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentOperationCountDto activeCustomers() {
        Set<Long> scopedCustomerIds = AgentCustomerDataScopeContext.customerIds();
        if (scopedCustomerIds != null && scopedCustomerIds.isEmpty()) return count("ACTIVE_CUSTOMER_COUNT", "AGENT_ACTIVE_CUSTOMER_V1", 0);
        List<CustomerOrder> orders = customerOrderMapper.selectList(new LambdaQueryWrapper<CustomerOrder>()
            .eq(CustomerOrder::getStatus, 1)
            .in(scopedCustomerIds != null, CustomerOrder::getCustomerId, scopedCustomerIds));
        Map<Long, int[]> verifiedByOrder = verifiedByOrder(orders);
        Set<Long> customers = new HashSet<>();
        for (CustomerOrder order : orders) {
            if (order == null || order.getId() == null || order.getCustomerId() == null) continue;
            int[] verified = verifiedByOrder.getOrDefault(order.getId(), new int[3]);
            OrderMealBalanceDto balance = OrderMealBalanceCalculator.calculate(order, verified[0], verified[1], verified[2]);
            if (balance.getRemainingBreakfast() > 0 || balance.getRemainingLunchDinner() > 0) customers.add(order.getCustomerId());
        }
        return count("ACTIVE_CUSTOMER_COUNT", "AGENT_ACTIVE_CUSTOMER_V1", customers.size());
    }

    /** 批量汇总未删除核销数，供活跃客户统计复用订单余额统一口径。 */
    private Map<Long, int[]> verifiedByOrder(List<CustomerOrder> orders) {
        List<Long> orderIds = new ArrayList<>();
        if (orders != null) for (CustomerOrder order : orders) if (order != null && order.getId() != null) orderIds.add(order.getId());
        if (orderIds.isEmpty()) return Map.of();
        Map<Long, int[]> result = new HashMap<>();
        List<OrderMealVerifiedCountDto> rows = customerOrderMapper.sumVerifiedCountByOrderIds(orderIds);
        if (rows == null) return result;
        for (OrderMealVerifiedCountDto row : rows) {
            if (row == null || row.getOrderId() == null) continue;
            int[] values = result.computeIfAbsent(row.getOrderId(), ignored -> new int[3]);
            int count = row.getVerifiedCount() == null ? 0 : Math.max(row.getVerifiedCount(), 0);
            if ("BREAKFAST".equals(row.getMealType())) values[0] += count;
            else if ("LUNCH".equals(row.getMealType())) values[1] += count;
            else if ("DINNER".equals(row.getMealType())) values[2] += count;
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AgentOperationCountDto expiringOrders(AgentOperationOrderRequest request) {
        LocalDate start = request == null || request.getStartDate() == null ? LocalDate.now(ZoneId.of("Asia/Shanghai")) : parseRequired(request.getStartDate(), "开始日期");
        LocalDate end = request == null || request.getEndDate() == null ? start.plusDays(7) : parseRequired(request.getEndDate(), "结束日期");
        if (end.isBefore(start) || end.isAfter(start.plusDays(31))) throw new IllegalArgumentException("订单到期日期范围必须在 0 至 31 天内");
        Set<Long> scopedCustomerIds = AgentCustomerDataScopeContext.customerIds();
        if (scopedCustomerIds != null && scopedCustomerIds.isEmpty()) return count("EXPIRING_ORDER_COUNT", "AGENT_EXPIRING_ORDER_V1", 0);
        long total = customerOrderMapper.selectCount(new LambdaQueryWrapper<CustomerOrder>()
            .eq(CustomerOrder::getStatus, 1).between(CustomerOrder::getEndDate, start, end)
            .in(scopedCustomerIds != null, CustomerOrder::getCustomerId, scopedCustomerIds));
        return count("EXPIRING_ORDER_COUNT", "AGENT_EXPIRING_ORDER_V1", total);
    }

    private AgentOperationCountDto count(String code, String definitionId, long total) {
        AgentOperationCountDto result = new AgentOperationCountDto();
        result.setMetricCode(code); result.setMetricDefinitionId(definitionId); result.setTotal(total);
        result.setQueriedAt(java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString());
        return result;
    }
    private LocalDate parseRequired(String value, String field) {
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException(field + "必须使用 yyyy-MM-dd 格式", exception); }
    }
    private String normalizeMealType(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String result = value.trim().toUpperCase();
        if (!"BREAKFAST".equals(result) && !"LUNCH".equals(result) && !"DINNER".equals(result)) throw new IllegalArgumentException("餐次仅支持 BREAKFAST、LUNCH 或 DINNER");
        return result;
    }
    /** 返回本次口径应计算的餐次集合；未筛选时分别计算三个餐次并以客户+餐次去重。 */
    private List<String> expectedMealTypes(String mealType) {
        return mealType == null ? List.of("BREAKFAST", "LUNCH", "DINNER") : List.of(mealType);
    }

    /** 读取指定计划的客户排餐记录，保留所属餐次用于受控聚合。 */
    private List<PlanCustomerRow> loadPlanCustomerRows(List<MealPlan> plans) {
        List<PlanCustomerRow> result = new ArrayList<>();
        if (plans == null) return result;
        for (MealPlan plan : plans) {
            if (plan == null || plan.getId() == null) continue;
            List<MealPlanCustomer> rows = mealPlanCustomerMapper.selectByMealPlanId(plan.getId());
            if (rows != null) for (MealPlanCustomer row : rows) result.add(new PlanCustomerRow(plan, row));
        }
        return result;
    }

    /** 批量加载排餐客户关联订单，仅用于来源和套餐维度聚合。 */
    private Map<Long, CustomerOrder> ordersById(List<PlanCustomerRow> rows) {
        List<Long> orderIds = rows.stream().map(item -> item.customer == null ? null : item.customer.getOrderId())
            .filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, CustomerOrder> result = new HashMap<>();
        if (orderIds.isEmpty()) return result;
        List<CustomerOrder> orders = customerOrderMapper.selectBatchIds(orderIds);
        if (orders != null) for (CustomerOrder order : orders) if (order != null && order.getId() != null) result.put(order.getId(), order);
        return result;
    }

    /** 将本轮相关父套餐 ID 映射为展示名称，避免向前端暴露不具业务含义的内部 ID。 */
    private Map<Long, String> packageNames(List<PlanCustomerRow> rows, Map<Long, CustomerOrder> ordersById,
                                           Map<String, List<CustomerOrder>> expectedOrdersByMealType) {
        Set<Long> packageIds = new HashSet<>();
        for (PlanCustomerRow item : rows) {
            if (item.customer != null && item.customer.getParentPackageId() != null) packageIds.add(item.customer.getParentPackageId());
            CustomerOrder order = item.customer == null ? null : ordersById.get(item.customer.getOrderId());
            if (order != null && order.getParentPackageId() != null) packageIds.add(order.getParentPackageId());
        }
        if (expectedOrdersByMealType != null) expectedOrdersByMealType.values().forEach(orders -> {
            if (orders != null) for (CustomerOrder order : orders) if (order != null && order.getParentPackageId() != null) packageIds.add(order.getParentPackageId());
        });
        Map<Long, String> result = new HashMap<>();
        if (packageIds.isEmpty()) return result;
        List<ParentPackage> packages = parentPackageMapper.selectBatchIds(packageIds);
        if (packages != null) for (ParentPackage parentPackage : packages) {
            if (parentPackage != null && parentPackage.getId() != null && parentPackage.getPackageName() != null) result.put(parentPackage.getId(), parentPackage.getPackageName());
        }
        return result;
    }

    /** 按本次餐次范围一次读取有效订单，保证总数与维度聚合不重复触发资格过滤。 */
    private Map<String, List<CustomerOrder>> expectedOrdersByMealType(LocalDate date, String mealType) {
        // 早餐、午餐和晚餐的资格计算彼此独立；全餐次统计并行读取以避免三个慢查询串行占用 Agent 超时预算。
        return expectedMealTypes(mealType).parallelStream().collect(java.util.stream.Collectors.toMap(
            expectedMealType -> expectedMealType,
            expectedMealType -> {
                List<CustomerOrder> orders = mealPlanService.findExpectedCustomerOrders(date, expectedMealType);
                return orders == null ? List.of() : orders;
            },
            (left, right) -> left,
            HashMap::new));
    }

    /** 校验本次实际可执行的分组维度，禁止内部接口被自由字段调用。 */
    private List<String> normalizeBreakdownDimensions(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (String value : values) {
            String dimension = value == null ? "" : value.trim().toUpperCase();
            if (!"MEAL_TYPE".equals(dimension) && !"PACKAGE".equals(dimension) && !"CUSTOMER_SOURCE".equals(dimension)) {
                throw new IllegalArgumentException("运营统计分组仅支持 MEAL_TYPE、PACKAGE、CUSTOMER_SOURCE");
            }
            result.add(dimension);
        }
        if (result.size() > 2) throw new IllegalArgumentException("运营统计最多支持两个分组维度");
        return new ArrayList<>(result);
    }

    /** 为一个排餐或有效订单生成受控维度值；缺失归属使用明确的未标注桶。 */
    private String dimensionKey(List<String> dimensions, String mealType, MealPlanCustomer row, CustomerOrder order, Map<Long, String> packageNames) {
        if (dimensions == null || dimensions.isEmpty()) return null;
        List<String> values = new ArrayList<>();
        for (String dimension : dimensions) {
            if ("MEAL_TYPE".equals(dimension)) values.add(mealType == null ? "未标注餐次" : mealType);
            else if ("PACKAGE".equals(dimension)) {
                Long packageId = row != null && row.getParentPackageId() != null ? row.getParentPackageId() : order == null ? null : order.getParentPackageId();
                values.add(packageId == null ? "未配置套餐" : packageNames.getOrDefault(packageId, "未配置套餐"));
            } else values.add(order == null || order.getCustomerSource() == null || order.getCustomerSource().trim().isEmpty() ? "未标注来源" : order.getCustomerSource().trim());
        }
        return String.join(" / ", values);
    }

    /** 向餐次客户集合写入一条已通过数据范围过滤的聚合记录。 */
    private void addToBreakdown(Map<String, Set<Long>> breakdown, String mealType, Long customerId) {
        if (mealType != null && customerId != null) breakdown.computeIfAbsent(mealType, ignored -> new HashSet<>()).add(customerId);
    }

    /** 将客户集合分组转换为稳定的数值分组，避免 DTO 暴露客户标识。 */
    private Map<String, Long> countBreakdown(Map<String, Set<Long>> breakdown) {
        Map<String, Long> result = new java.util.TreeMap<>();
        if (breakdown != null) breakdown.forEach((mealType, customerIds) -> result.put(mealType, (long) customerIds.size()));
        return new java.util.LinkedHashMap<>(result);
    }

    /** 返回应服务客户中尚未生成成功排餐的餐次分组，不修改原应服务集合。 */
    private Map<String, Set<Long>> subtractExpectedCustomers(Map<String, Set<Long>> expected, Map<String, Set<Long>> scheduled) {
        Map<String, Set<Long>> result = new HashMap<>();
        expected.forEach((mealType, customerIds) -> {
            Set<Long> remaining = new HashSet<>(customerIds);
            remaining.removeAll(scheduled.getOrDefault(mealType, Set.of()));
            result.put(mealType, remaining);
        });
        return result;
    }

    /** 将失败记录计数转为稳定顺序的分组，键不包含客户或订单标识。 */
    private Map<String, Long> orderedCounts(Map<String, Long> counts) {
        return new java.util.LinkedHashMap<>(new java.util.TreeMap<>(counts));
    }

    /** 向按维度聚合的客户+餐次集合写入一条记录，保留待排餐指标的餐次去重规则。 */
    private void addToCustomerMealBreakdown(Map<String, Set<String>> breakdown, String dimension, String customerMeal) {
        if (dimension != null && customerMeal != null) breakdown.computeIfAbsent(dimension, ignored -> new HashSet<>()).add(customerMeal);
    }

    /** 统计按维度聚合的客户+餐次集合。 */
    private Map<String, Long> countCustomerMealBreakdown(Map<String, Set<String>> breakdown) {
        Map<String, Long> result = new java.util.TreeMap<>();
        if (breakdown != null) breakdown.forEach((dimension, customerMeals) -> result.put(dimension, (long) customerMeals.size()));
        return new java.util.LinkedHashMap<>(result);
    }

    /** 从应服务维度集合中剔除已成功排餐的同一客户和餐次，不修改原集合。 */
    private Map<String, Set<String>> removeScheduledCustomerMeals(Map<String, Set<String>> expected, Set<String> scheduledCustomerMeals) {
        Map<String, Set<String>> result = new HashMap<>();
        expected.forEach((dimension, customerMeals) -> {
            Set<String> remaining = new HashSet<>(customerMeals);
            remaining.removeAll(scheduledCustomerMeals);
            result.put(dimension, remaining);
        });
        return result;
    }

    /** 排餐记录及其所属计划的轻量聚合上下文，不包含可展示的客户敏感字段。 */
    private static final class PlanCustomerRow {
        private final MealPlan plan;
        private final MealPlanCustomer customer;
        private PlanCustomerRow(MealPlan plan, MealPlanCustomer customer) { this.plan = plan; this.customer = customer; }
    }
    private String customerMealKey(Long customerId, String mealType) { return customerId + "|" + mealType; }
}
