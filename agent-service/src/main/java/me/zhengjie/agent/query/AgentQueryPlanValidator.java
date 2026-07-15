package me.zhengjie.agent.query;

import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentMetricCatalog;
import me.zhengjie.agent.query.domain.AgentMetricDefinition;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.tool.AgentBusinessToolRegistry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对模型产生的 QueryPlan 执行白名单和边界校验，禁止将计划降级为任意查询。
 */
@Component
public class AgentQueryPlanValidator {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_RECENT_LIMIT = 50;
    private static final int MAX_DATE_RANGE_DAYS = 31;
    private static final int MAX_TOOL_COUNT = 6;
    private static final int MAX_GROUP_COUNT = 100;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 50;
    private static final Set<String> DETAIL_LEVELS = Set.of("SUMMARY", "DETAIL");
    private static final Set<String> MEAL_TYPES = Set.of("BREAKFAST", "LUNCH", "DINNER");
    private static final Set<String> V3_SUBJECTS = Set.of("MEAL_PLAN", "CUSTOMER", "DISH");
    private static final Set<String> V3_RELATIONS = Set.of("MEAL_PLAN_CUSTOMER", "MEAL_PLAN_DISH");
    private static final Set<String> V3_FACTS = Set.of("CUSTOMER_CODE", "DISH_NAME", "ALLERGY_FILTERED", "ALLERGY_REASONS");
    private static final Set<String> V3_GROUP_BY = Set.of("CUSTOMER_CODE");
    private static final Set<AgentQueryDimension> EXECUTABLE_OPERATION_DIMENSIONS = EnumSet.of(
        AgentQueryDimension.MEAL_TYPE, AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE);
    private static final Map<AgentQueryDomain, Set<AgentQueryAction>> ALLOWED_ACTIONS = allowedActions();

    /**
     * 校验计划的版本、领域动作组合、实体条件及查询边界。
     *
     * @param plan 待执行的受控查询计划
     * @return 校验错误及需要客服补充的字段
     */
    public AgentQueryPlanValidationResult validate(AgentQueryPlan plan) {
        List<AgentQueryPlanValidationError> errors = new ArrayList<>();
        List<String> missingFields = new ArrayList<>();
        if (plan == null) {
            errors.add(error("plan", "PLAN_NULL", "查询计划不能为空"));
            return new AgentQueryPlanValidationResult(errors, missingFields);
        }
        if (!AgentQueryPlan.SCHEMA_VERSION.equals(plan.getVersion())
            && !AgentQueryPlan.SCHEMA_VERSION_V2.equals(plan.getVersion())
            && !AgentQueryPlan.SCHEMA_VERSION_V3.equals(plan.getVersion())) {
            errors.add(error("version", "VERSION_UNSUPPORTED", "仅支持 QueryPlan 版本 1.0、2.0 或 3.0"));
        }
        if (plan.getDomain() == null) errors.add(error("domain", "DOMAIN_REQUIRED", "必须指定查询领域"));
        if (plan.getAction() == null) errors.add(error("action", "ACTION_REQUIRED", "必须指定查询动作"));
        if (plan.getDomain() != null && plan.getAction() != null
            && !ALLOWED_ACTIONS.get(plan.getDomain()).contains(plan.getAction())) {
            errors.add(error("action", "ACTION_NOT_ALLOWED", "该领域不支持此查询动作"));
        }
        validateEntities(plan.getEntities(), errors);
        validateFilters(plan.getFilters(), errors);
        validateToolSpecificConstraints(plan, errors);
        validateToolBudget(plan, errors);
        validateMetrics(plan, errors);
        validateAggregation(plan, errors);
        validateV3(plan, errors);
        if (!DETAIL_LEVELS.contains(normalize(plan.getDetailLevel()))) {
            errors.add(error("detailLevel", "DETAIL_LEVEL_INVALID", "明细级别仅支持 SUMMARY 或 DETAIL"));
        }
        validateRequiredConditions(plan, errors, missingFields);
        return new AgentQueryPlanValidationResult(errors, missingFields);
    }

    /** 校验 V3 语义对象、关系、事实和分组只能来自登记白名单。 */
    private void validateV3(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        if (!AgentQueryPlan.SCHEMA_VERSION_V3.equals(plan.getVersion())) return;
        if (plan.getDomain() != AgentQueryDomain.MEAL_PLAN || plan.getAction() != AgentQueryAction.LIST
            || plan.getToolNames() == null || !plan.getToolNames().equals(List.of("listMealPlans"))) {
            errors.add(error("plan", "V3_GRAPH_NOT_ALLOWED", "V3 排餐分析只能使用登记的范围排餐查询图"));
        }
        if (!V3_SUBJECTS.equals(toSet(plan.getSubjects())) || !V3_RELATIONS.equals(toSet(plan.getRelations()))
            || !V3_FACTS.equals(toSet(plan.getRequestedFacts())) || !"FILTER_AND_GROUP".equals(plan.getOperation())
            || !V3_GROUP_BY.equals(toSet(plan.getGroupBy()))) {
            errors.add(error("plan", "V3_SEMANTIC_NOT_ALLOWED", "V3 语义包含未登记对象、关系、事实或操作"));
        }
        AgentQueryFilters filters = plan.getFilters();
        boolean allMeals = plan.getMealScope() == me.zhengjie.agent.analysis.domain.MealScope.ALL_AVAILABLE;
        boolean singleMeal = plan.getMealScope() != null && plan.getMealScope() != me.zhengjie.agent.analysis.domain.MealScope.ALL_AVAILABLE
            && filters != null && plan.getMealScope().name().equals(normalize(filters.getMealType()));
        if (filters == null || !notBlank(filters.getRecordDate()) || allMeals && notBlank(filters.getMealType()) || !allMeals && !singleMeal) {
            errors.add(error("filters", "V3_SCOPE_REQUIRED", "V3 排餐分析必须指定单日日期，并使用单一餐次或全部餐次范围"));
        }
        if (filters != null && (filters.getPage() == null || filters.getSize() == null || filters.getSize() > MAX_PAGE_SIZE)) {
            errors.add(error("filters", "V3_PAGE_REQUIRED", "V3 排餐分析必须使用受控分页"));
        }
    }

    private Set<String> toSet(List<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    /** 校验指标、维度和口径版本，保证统计只能使用登记能力。 */
    private void validateMetrics(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        boolean aggregation = plan.getDomain() == AgentQueryDomain.OPERATION_STATISTICS
            || plan.getDomain() == AgentQueryDomain.NATURAL_LANGUAGE_REPORT;
        if (plan.getMetrics() == null || plan.getMetrics().isEmpty()) {
            if (aggregation) {
                errors.add(error("metrics", "METRIC_REQUIRED", "运营统计必须指定已登记指标"));
            }
            return;
        }
        for (AgentQueryMetric metric : plan.getMetrics()) {
            AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
            if (aggregation && definition == null) {
                errors.add(error("metrics", "METRIC_NOT_REGISTERED", "查询计划包含未登记指标"));
            } else if (aggregation && definition.getDomain() != plan.getDomain()
                && plan.getDomain() != AgentQueryDomain.NATURAL_LANGUAGE_REPORT) {
                errors.add(error("metrics", "METRIC_DOMAIN_MISMATCH", "指标不属于查询计划声明的领域"));
            } else if (aggregation && notBlank(plan.getMetricVersion()) && !definition.getMetricVersion().equals(plan.getMetricVersion())) {
                errors.add(error("metricVersion", "METRIC_VERSION_UNSUPPORTED", "指标口径版本不受支持"));
            }
            if (aggregation && definition != null && plan.getToolNames() != null && !plan.getToolNames().isEmpty()
                && !plan.getToolNames().contains(definition.getToolName())) {
                errors.add(error("toolNames", "METRIC_TOOL_MISMATCH", "指标必须使用目录登记的只读工具"));
            }
        }
    }

    /** 校验受控聚合边界，限制报表维度、分组数、排序与日期范围。 */
    private void validateAggregation(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        boolean aggregation = plan.getDomain() == AgentQueryDomain.OPERATION_STATISTICS
            || plan.getDomain() == AgentQueryDomain.NATURAL_LANGUAGE_REPORT;
        if (!aggregation) return;
        if (!AgentQueryPlan.SCHEMA_VERSION_V2.equals(plan.getVersion())) {
            errors.add(error("version", "STATISTICS_REQUIRES_V2", "运营统计仅支持 QueryPlan 2.0"));
        }
        if (plan.getMetrics() != null && plan.getMetrics().size() > 3) {
            errors.add(error("metrics", "METRIC_LIMIT_EXCEEDED", "单次报表最多查询 3 个指标"));
        }
        if (plan.getDimensions() != null && plan.getDimensions().size() > 2) {
            errors.add(error("dimensions", "DIMENSION_LIMIT_EXCEEDED", "单次报表最多按 2 个维度分组"));
        }
        if (plan.getAction() == AgentQueryAction.BREAKDOWN && (plan.getDimensions() == null || plan.getDimensions().isEmpty())) {
            errors.add(error("dimensions", "DIMENSION_REQUIRED", "分组统计必须指定可执行维度"));
        }
        if (plan.getAction() == AgentQueryAction.SUMMARY && plan.getDimensions() != null && !plan.getDimensions().isEmpty()) {
            errors.add(error("action", "ACTION_DIMENSION_CONFLICT", "含分组维度的统计必须使用 BREAKDOWN 动作"));
        }
        if (plan.getLimit() != null && (plan.getLimit() < 1 || plan.getLimit() > MAX_GROUP_COUNT)) {
            errors.add(error("limit", "GROUP_LIMIT_INVALID", "分组结果数必须在 1 至 100 之间"));
        }
        if (plan.getDimensions() == null || plan.getMetrics() == null) return;
        for (AgentQueryDimension dimension : plan.getDimensions()) {
            if (!EXECUTABLE_OPERATION_DIMENSIONS.contains(dimension)) {
                errors.add(error("dimensions", "DIMENSION_NOT_EXECUTABLE", "当前运营统计接口尚未支持该分组维度"));
            }
            for (AgentQueryMetric metric : plan.getMetrics()) {
                AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
                if (definition != null && !definition.getDimensions().contains(dimension)) {
                    errors.add(error("dimensions", "DIMENSION_NOT_SUPPORTED", "指标不支持该统计维度"));
                }
            }
        }
        validateMetricDateRanges(plan, errors);
        validateSingleDateMetrics(plan, errors);
    }

    /** 每日指标只能落为单个业务日期，不能以日期范围替代。 */
    private void validateSingleDateMetrics(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        if (plan.getMetrics() == null) return;
        AgentQueryFilters filters = plan.getFilters();
        for (AgentQueryMetric metric : plan.getMetrics()) {
            AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
            if (definition != null && definition.isRequiresSingleDate()
                && (filters == null || !notBlank(filters.getRecordDate()) || notBlank(filters.getStartDate()) || notBlank(filters.getEndDate()))) {
                errors.add(error("filters", "METRIC_SINGLE_DATE_REQUIRED", "该指标必须解析为单个业务日期"));
            }
        }
    }

    private void validateMetricDateRanges(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        AgentQueryFilters filters = plan.getFilters();
        if (filters == null || !notBlank(filters.getStartDate()) || !notBlank(filters.getEndDate()) || plan.getMetrics() == null) return;
        try {
            long rangeDays = ChronoUnit.DAYS.between(LocalDate.parse(filters.getStartDate()), LocalDate.parse(filters.getEndDate()));
            for (AgentQueryMetric metric : plan.getMetrics()) {
                AgentMetricDefinition definition = AgentMetricCatalog.definition(metric);
                if (definition != null && rangeDays > definition.getMaxDateRangeDays()) {
                    errors.add(error("filters", "METRIC_DATE_RANGE_EXCEEDED", "超过指标允许的最大日期范围"));
                }
            }
        } catch (DateTimeParseException ignored) {
            // 日期格式错误已由 validateFilters 统一返回。
        }
    }

    private void validateRequiredConditions(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors,
                                            List<String> missingFields) {
        if (plan.getDomain() == null || plan.getAction() == null) return;
        AgentEntityReference entities = plan.getEntities();
        AgentQueryFilters filters = plan.getFilters();
        boolean customer = entities != null && (entities.getCustomerId() != null || notBlank(entities.getCustomerCode()) || notBlank(entities.getCustomerName()));
        boolean order = entities != null && (entities.getOrderId() != null || notBlank(entities.getOrderCode()));
        boolean mealPlan = entities != null && entities.getMealPlanRecordId() != null;
        boolean packageRef = entities != null && entities.getPackageId() != null;
        boolean recordDate = filters != null && notBlank(filters.getRecordDate());
        boolean aggregation = plan.getDomain() == AgentQueryDomain.OPERATION_STATISTICS
            || plan.getDomain() == AgentQueryDomain.NATURAL_LANGUAGE_REPORT;
        if (plan.getDomain() == AgentQueryDomain.CUSTOMER && plan.getAction() != AgentQueryAction.LIST && !customer) {
            missing(missingFields, "customer");
        }
        if (plan.getDomain() == AgentQueryDomain.ORDER && plan.getAction() == AgentQueryAction.DETAIL && !order) missing(missingFields, "order");
        if (plan.getDomain() == AgentQueryDomain.ORDER && plan.getAction() != AgentQueryAction.DETAIL && !customer && !order) missing(missingFields, "customerOrOrder");
        if (plan.getDomain() == AgentQueryDomain.MEAL_PLAN && !AgentQueryPlan.SCHEMA_VERSION_V3.equals(plan.getVersion()) && !mealPlan && !customer) missing(missingFields, "customer");
        if (plan.getDomain() == AgentQueryDomain.MEAL_PLAN && !mealPlan && !recordDate) missing(missingFields, "recordDate");
        if ((plan.getDomain() == AgentQueryDomain.VERIFICATION || plan.getDomain() == AgentQueryDomain.REFUND) && !customer && !order) missing(missingFields, "customerOrOrder");
        if (plan.getDomain() == AgentQueryDomain.PACKAGE && plan.getAction() == AgentQueryAction.DETAIL && !packageRef) missing(missingFields, "package");
        if (plan.getDomain() == AgentQueryDomain.DISH && plan.getAction() == AgentQueryAction.LIST && !recordDate) missing(missingFields, "recordDate");
        if (aggregation && containsDailyWorkloadMetric(plan.getMetrics()) && !recordDate) missing(missingFields, "recordDate");
        if (!missingFields.isEmpty()) errors.add(error("entities", "REQUIRED_CONDITION_MISSING", "缺少执行查询所需的业务对象或日期条件"));
    }

    /** 判断计划是否包含主系统仅支持单日聚合的每日工作量指标。 */
    private boolean containsDailyWorkloadMetric(List<AgentQueryMetric> metrics) {
        if (metrics == null) return false;
        return metrics.contains(AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT)
            || metrics.contains(AgentQueryMetric.DAILY_VERIFIED_CUSTOMER_COUNT)
            || metrics.contains(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT)
            || metrics.contains(AgentQueryMetric.DAILY_EXPECTED_CUSTOMER_COUNT)
            || metrics.contains(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT)
            || metrics.contains(AgentQueryMetric.MEAL_PLAN_FAILURE_COUNT);
    }

    private void validateEntities(AgentEntityReference entities, List<AgentQueryPlanValidationError> errors) {
        if (entities == null) return;
        validateText("entities.customerCode", entities.getCustomerCode(), MAX_IDENTIFIER_LENGTH, errors);
        validateText("entities.orderCode", entities.getOrderCode(), MAX_IDENTIFIER_LENGTH, errors);
        validateText("entities.customerName", entities.getCustomerName(), MAX_NAME_LENGTH, errors);
        if (entities.getCustomerId() != null && entities.getCustomerId() <= 0) errors.add(error("entities.customerId", "ID_INVALID", "客户 ID 必须为正数"));
        if (entities.getOrderId() != null && entities.getOrderId() <= 0) errors.add(error("entities.orderId", "ID_INVALID", "订单 ID 必须为正数"));
    }

    private void validateFilters(AgentQueryFilters filters, List<AgentQueryPlanValidationError> errors) {
        if (filters == null) return;
        LocalDate recordDate = parseDate("filters.recordDate", filters.getRecordDate(), errors);
        LocalDate startDate = parseDate("filters.startDate", filters.getStartDate(), errors);
        LocalDate endDate = parseDate("filters.endDate", filters.getEndDate(), errors);
        if (startDate != null && endDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days < 0 || days > MAX_DATE_RANGE_DAYS) errors.add(error("filters", "DATE_RANGE_INVALID", "日期范围必须在 0 至 31 天内"));
        }
        if (recordDate != null && (startDate != null || endDate != null)) errors.add(error("filters", "DATE_FILTER_CONFLICT", "单日与日期范围条件不能同时使用"));
        if (notBlank(filters.getMealType()) && !MEAL_TYPES.contains(normalize(filters.getMealType()))) errors.add(error("filters.mealType", "MEAL_TYPE_INVALID", "餐次仅支持 BREAKFAST、LUNCH 或 DINNER"));
        if (filters.getPage() != null && filters.getPage() < 1) errors.add(error("filters.page", "PAGE_INVALID", "页码必须大于等于 1"));
        if (filters.getSize() != null && (filters.getSize() < 1 || filters.getSize() > MAX_PAGE_SIZE)) errors.add(error("filters.size", "PAGE_SIZE_INVALID", "单页条数必须在 1 至 50 之间"));
        if (filters.getRecentLimit() != null && (filters.getRecentLimit() < 1 || filters.getRecentLimit() > MAX_RECENT_LIMIT)) errors.add(error("filters.recentLimit", "RECENT_LIMIT_INVALID", "最近记录数必须在 1 至 50 之间"));
    }

    private void validateToolBudget(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        if (plan.getToolNames() != null && plan.getToolNames().size() > MAX_TOOL_COUNT) errors.add(error("toolNames", "TOOL_BUDGET_EXCEEDED", "单轮最多调用 6 个工具"));
        if (plan.getToolNames() != null && plan.getToolNames().stream().anyMatch(name -> !notBlank(name) || name.length() > MAX_IDENTIFIER_LENGTH)) errors.add(error("toolNames", "TOOL_NAME_INVALID", "工具名称不能为空且长度不得超过 64"));
        if (plan.getToolNames() != null && plan.getToolNames().stream().anyMatch(name -> !AgentBusinessToolRegistry.isRegistered(name))) {
            errors.add(error("toolNames", "TOOL_NOT_REGISTERED", "查询计划包含未登记的业务工具"));
        }
    }

    /** 校验候选菜预览等工具的额外输入边界，避免把通用槽位传入不支持的内部接口。 */
    private void validateToolSpecificConstraints(AgentQueryPlan plan, List<AgentQueryPlanValidationError> errors) {
        if (plan.getToolNames() == null || plan.getFilters() == null) return;
        String mealType = normalize(plan.getFilters().getMealType());
        if (plan.getToolNames().contains("previewDishCandidates") && !"LUNCH".equals(mealType) && !"DINNER".equals(mealType)) {
            errors.add(error("filters.mealType", "CANDIDATE_DISH_MEAL_TYPE_INVALID", "候选菜预览仅支持午餐或晚餐"));
        }
        if (plan.getToolNames().contains("listScheduledDishes") && !mealType.isEmpty()
            && !"LUNCH".equals(mealType) && !"DINNER".equals(mealType)) {
            errors.add(error("filters.mealType", "SCHEDULED_MENU_MEAL_TYPE_INVALID", "公共排期菜单仅支持午餐或晚餐"));
        }
    }

    private LocalDate parseDate(String field, String value, List<AgentQueryPlanValidationError> errors) {
        if (!notBlank(value)) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { errors.add(error(field, "DATE_INVALID", "日期必须使用 yyyy-MM-dd 格式")); return null; }
    }

    private void validateText(String field, String value, int maxLength, List<AgentQueryPlanValidationError> errors) {
        if (value != null && (value.isBlank() || value.length() > maxLength)) errors.add(error(field, "TEXT_INVALID", "文本不能为空且长度不能超出限制"));
    }

    private void missing(List<String> missingFields, String field) { if (!missingFields.contains(field)) missingFields.add(field); }
    private boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private AgentQueryPlanValidationError error(String field, String code, String message) { return new AgentQueryPlanValidationError(field, code, message); }

    private static Map<AgentQueryDomain, Set<AgentQueryAction>> allowedActions() {
        Map<AgentQueryDomain, Set<AgentQueryAction>> result = new EnumMap<>(AgentQueryDomain.class);
        result.put(AgentQueryDomain.CUSTOMER, EnumSet.of(AgentQueryAction.OVERVIEW, AgentQueryAction.LIST, AgentQueryAction.DETAIL, AgentQueryAction.SUMMARY));
        result.put(AgentQueryDomain.ORDER, EnumSet.of(AgentQueryAction.LIST, AgentQueryAction.DETAIL, AgentQueryAction.SUMMARY, AgentQueryAction.EXPLAIN));
        result.put(AgentQueryDomain.MEAL_PLAN, EnumSet.of(AgentQueryAction.LIST, AgentQueryAction.DETAIL, AgentQueryAction.SUMMARY, AgentQueryAction.DIAGNOSE));
        result.put(AgentQueryDomain.VERIFICATION, EnumSet.of(AgentQueryAction.LIST, AgentQueryAction.SUMMARY));
        result.put(AgentQueryDomain.REFUND, EnumSet.of(AgentQueryAction.LIST, AgentQueryAction.SUMMARY));
        result.put(AgentQueryDomain.PACKAGE, EnumSet.of(AgentQueryAction.LIST, AgentQueryAction.DETAIL));
        result.put(AgentQueryDomain.DISH, EnumSet.of(AgentQueryAction.LIST, AgentQueryAction.DETAIL));
        result.put(AgentQueryDomain.BUSINESS_RULE, EnumSet.of(AgentQueryAction.EXPLAIN));
        result.put(AgentQueryDomain.OPERATION_STATISTICS, EnumSet.of(AgentQueryAction.SUMMARY, AgentQueryAction.BREAKDOWN));
        result.put(AgentQueryDomain.NATURAL_LANGUAGE_REPORT, EnumSet.of(AgentQueryAction.SUMMARY, AgentQueryAction.BREAKDOWN));
        return result;
    }
}
