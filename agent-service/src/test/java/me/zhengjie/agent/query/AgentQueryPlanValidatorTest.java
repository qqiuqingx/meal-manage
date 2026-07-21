package me.zhengjie.agent.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryFilters;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.AgentQueryPlan;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.analysis.domain.MealScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQueryPlanValidatorTest {

    private final AgentQueryPlanValidator validator = new AgentQueryPlanValidator();

    @Test
    void shouldAcceptBoundedOrderMealBalancePlan() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.ORDER);
        plan.setAction(AgentQueryAction.LIST);
        AgentEntityReference entities = new AgentEntityReference();
        entities.setCustomerCode("B3303");
        plan.setEntities(entities);
        AgentQueryFilters filters = new AgentQueryFilters();
        filters.setPage(1);
        filters.setSize(10);
        plan.setFilters(filters);
        plan.setMetrics(List.of(AgentQueryMetric.MEAL_BALANCE));

        assertTrue(validator.validate(plan).isValid());
    }

    @Test
    void shouldAcceptUnboundedMealPlanHistoryList() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.MEAL_PLAN);
        plan.setAction(AgentQueryAction.LIST);
        plan.setToolNames(List.of("listMealPlans"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertTrue(result.isValid());
        assertFalse(result.requiresFollowUp());
    }

    @Test
    void shouldRejectUnsafeOrOutOfBoundPlanValues() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.REFUND);
        plan.setAction(AgentQueryAction.LIST);
        AgentEntityReference entities = new AgentEntityReference();
        entities.setCustomerCode("B3303");
        plan.setEntities(entities);
        AgentQueryFilters filters = new AgentQueryFilters();
        filters.setStartDate("2026-07-01");
        filters.setEndDate("2026-08-15");
        filters.setSize(51);
        plan.setFilters(filters);
        plan.setToolNames(List.of("a", "b", "c", "d", "e", "f", "g"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "DATE_RANGE_INVALID".equals(error.code())));
        assertTrue(result.errors().stream().anyMatch(error -> "PAGE_SIZE_INVALID".equals(error.code())));
        assertTrue(result.errors().stream().anyMatch(error -> "TOOL_BUDGET_EXCEEDED".equals(error.code())));
    }

    @Test
    void shouldRejectUnknownEnumsDuringJsonDeserialization() {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"version\":\"1.0\",\"domain\":\"ORDER_AMOUNT\",\"action\":\"LIST\"}";

        assertThrows(Exception.class, () -> mapper.readValue(json, AgentQueryPlan.class));
    }

    @Test
    void shouldRejectUnregisteredBusinessTool() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.CUSTOMER);
        plan.setAction(AgentQueryAction.OVERVIEW);
        AgentEntityReference entities = new AgentEntityReference();
        entities.setCustomerCode("B3303");
        plan.setEntities(entities);
        plan.setToolNames(List.of("executeAnySql"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "TOOL_NOT_REGISTERED".equals(error.code())));
    }

    @Test
    void shouldRejectBreakfastForCandidateDishPreview() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.DISH); plan.setAction(AgentQueryAction.LIST);
        AgentEntityReference entities = new AgentEntityReference(); entities.setCustomerId(3303L); plan.setEntities(entities);
        AgentQueryFilters filters = new AgentQueryFilters(); filters.setRecordDate("2026-07-11"); filters.setMealType("BREAKFAST"); plan.setFilters(filters);
        plan.setToolNames(List.of("previewDishCandidates"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "CANDIDATE_DISH_MEAL_TYPE_INVALID".equals(error.code())));
    }

    @Test
    void shouldRejectBreakfastForScheduledMenu() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.DISH); plan.setAction(AgentQueryAction.LIST);
        AgentQueryFilters filters = new AgentQueryFilters(); filters.setRecordDate("2026-07-11"); filters.setMealType("BREAKFAST"); plan.setFilters(filters);
        plan.setToolNames(List.of("listScheduledDishes"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "SCHEDULED_MENU_MEAL_TYPE_INVALID".equals(error.code())));
    }

    @Test
    void shouldAcceptAllMealScopeForBoundedV3MealPlanAnalysis() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V3);
        plan.setDomain(AgentQueryDomain.MEAL_PLAN); plan.setAction(AgentQueryAction.LIST);
        AgentQueryFilters filters = new AgentQueryFilters(); filters.setRecordDate("2026-07-13"); filters.setPage(1); filters.setSize(50); plan.setFilters(filters);
        plan.setMealScope(MealScope.ALL_AVAILABLE);
        plan.setSubjects(List.of("MEAL_PLAN", "CUSTOMER", "DISH"));
        plan.setRelations(List.of("MEAL_PLAN_CUSTOMER", "MEAL_PLAN_DISH"));
        plan.setRequestedFacts(List.of("CUSTOMER_CODE", "DISH_NAME", "ALLERGY_FILTERED", "ALLERGY_REASONS"));
        plan.setOperation("FILTER_AND_GROUP"); plan.setGroupBy(List.of("CUSTOMER_CODE"));
        plan.setToolNames(List.of("listMealPlans"));

        assertTrue(validator.validate(plan).isValid());
    }

    /** 运营统计只能使用 2.0 协议中登记的指标、维度和工具。 */
    @Test
    void shouldAcceptRegisteredV2OperationStatistic() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.BREAKDOWN);
        AgentQueryFilters filters = new AgentQueryFilters();
        filters.setRecordDate("2026-07-12");
        plan.setFilters(filters);
        plan.setMetrics(List.of(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT));
        plan.setDimensions(List.of(AgentQueryDimension.MEAL_TYPE));
        plan.setMetricVersion("2026.07");
        plan.setToolNames(List.of("getDailyCustomerWorkload"));

        assertTrue(validator.validate(plan).isValid());
    }

    /** 自由维度和旧协议不能绕过运营指标目录。 */
    @Test
    void shouldRejectV1OperationStatistic() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.SUMMARY);
        plan.setMetrics(List.of(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT));
        plan.setToolNames(List.of("getDailyCustomerWorkload"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(error -> "STATISTICS_REQUIRES_V2".equals(error.code())));
    }

    /** 每日聚合缺少日期时必须返回结构化补充字段，不能请求下游接口后再报错。 */
    @Test
    void shouldRequireRecordDateForDailyOperationMetrics() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.SUMMARY);
        plan.setMetrics(List.of(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT));
        plan.setToolNames(List.of("getDailyCustomerWorkload"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertFalse(result.isValid());
        assertTrue(result.requiresFollowUp());
        assertTrue(result.missingFields().contains("recordDate"));
    }

    /** 已落地的客户来源维度可以经过指标目录和 QueryPlan 白名单执行。 */
    @Test
    void shouldAcceptExecutableOperationDimension() {
        AgentQueryPlan plan = new AgentQueryPlan();
        plan.setVersion(AgentQueryPlan.SCHEMA_VERSION_V2);
        plan.setDomain(AgentQueryDomain.OPERATION_STATISTICS);
        plan.setAction(AgentQueryAction.BREAKDOWN);
        AgentQueryFilters filters = new AgentQueryFilters(); filters.setRecordDate("2026-07-12"); plan.setFilters(filters);
        plan.setMetrics(List.of(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT));
        plan.setDimensions(List.of(AgentQueryDimension.CUSTOMER_SOURCE));
        plan.setToolNames(List.of("getDailyCustomerWorkload"));

        AgentQueryPlanValidationResult result = validator.validate(plan);

        assertTrue(result.isValid());
    }
}
