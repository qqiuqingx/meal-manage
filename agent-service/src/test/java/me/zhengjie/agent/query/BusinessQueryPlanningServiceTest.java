package me.zhengjie.agent.query;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.domain.AgentEntityReference;
import me.zhengjie.agent.query.domain.AgentQueryAction;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.MealScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 业务问题分析只能映射到固定领域、动作和白名单工具。 */
class BusinessQueryPlanningServiceTest {
    private final BusinessQueryPlanningService service = new BusinessQueryPlanningService();

    @Test
    void shouldPlanCustomerOrderAndHistoryDomainsWithoutModelProvidedTools() {
        BusinessQuestionAnalysis customer = analysis(AgentQueryDomain.CUSTOMER);
        customer.getEntities().setCustomerCode("B3303");
        assertPlan(customer, AgentQueryAction.OVERVIEW, "customerOverview");

        BusinessQuestionAnalysis order = analysis(AgentQueryDomain.ORDER);
        order.getEntities().setOrderCode("O3303");
        assertPlan(order, AgentQueryAction.DETAIL, "orderDetail");

        assertPlan(analysis(AgentQueryDomain.VERIFICATION), AgentQueryAction.LIST, "listVerifications");
        assertPlan(analysis(AgentQueryDomain.REFUND), AgentQueryAction.LIST, "listRefunds");
    }

    @Test
    void shouldRequireClarificationWhenAnalysisCannotExpressRequiredStableReference() {
        assertNull(service.plan(analysis(AgentQueryDomain.PACKAGE)));
        assertNull(service.plan(analysis(AgentQueryDomain.BUSINESS_RULE)));
    }

    @Test
    void shouldPlanAllAvailableScheduledMenuWithoutEmptyMealType() {
        BusinessQuestionAnalysis analysis = analysis(AgentQueryDomain.DISH);
        analysis.setQueryTarget(BusinessQueryTarget.SCHEDULED_MENU);
        analysis.setMealScope(MealScope.ALL_AVAILABLE);
        analysis.getFilters().setRecordDate("2026-07-13");

        var plan = service.plan(analysis);

        assertEquals(AgentQueryAction.LIST, plan.getAction());
        assertEquals(List.of("listScheduledDishes"), plan.getToolNames());
        assertEquals(MealScope.ALL_AVAILABLE, plan.getMealScope());
        assertNull(plan.getFilters().getMealType());
    }

    @Test
    void shouldCompileMealPlanAllergySemanticPlanWithoutToolFromAnalysis() {
        BusinessQuestionAnalysis analysis = analysis(AgentQueryDomain.MEAL_PLAN);
        analysis.setQueryTarget(BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS);
        analysis.getFilters().setRecordDate("2026-07-13");
        analysis.getFilters().setMealType("LUNCH");
        analysis.setSubjects(List.of("MEAL_PLAN", "CUSTOMER", "DISH"));
        analysis.setRelations(List.of("MEAL_PLAN_CUSTOMER", "MEAL_PLAN_DISH"));
        analysis.setRequestedFacts(List.of("CUSTOMER_CODE", "DISH_NAME", "ALLERGY_FILTERED", "ALLERGY_REASONS"));
        analysis.setOperation("FILTER_AND_GROUP");
        analysis.setGroupBy(List.of("CUSTOMER_CODE"));

        var plan = service.plan(analysis);

        assertEquals("3.0", plan.getVersion());
        assertEquals(List.of("listMealPlans"), plan.getToolNames());
        assertEquals(50, plan.getFilters().getSize());
    }

    @Test
    void shouldPlanAllMealScopeForMealPlanAllergyAnalysisWithoutMealType() {
        BusinessQuestionAnalysis analysis = analysis(AgentQueryDomain.MEAL_PLAN);
        analysis.setQueryTarget(BusinessQueryTarget.MEAL_PLAN_ALLERGY_ANALYSIS);
        analysis.setMealScope(MealScope.ALL_AVAILABLE);
        analysis.getFilters().setRecordDate("2026-07-13");
        analysis.getFilters().setStartDate("");
        analysis.getFilters().setEndDate("");
        analysis.getFilters().setRecentLimit(0);
        analysis.getEntities().setCustomerName("");
        analysis.setSubjects(List.of("MEAL_PLAN", "CUSTOMER", "DISH"));
        analysis.setRelations(List.of("MEAL_PLAN_CUSTOMER", "MEAL_PLAN_DISH"));
        analysis.setRequestedFacts(List.of("CUSTOMER_CODE", "DISH_NAME", "ALLERGY_FILTERED", "ALLERGY_REASONS"));
        analysis.setOperation("FILTER_AND_GROUP");
        analysis.setGroupBy(List.of("CUSTOMER_CODE"));

        var plan = service.plan(analysis);

        assertEquals(MealScope.ALL_AVAILABLE, plan.getMealScope());
        assertNull(plan.getFilters().getMealType());
        assertNull(plan.getFilters().getStartDate());
        assertNull(plan.getFilters().getEndDate());
        assertNull(plan.getFilters().getRecentLimit());
        assertNull(plan.getEntities().getCustomerName());
        assertEquals(List.of("listMealPlans"), plan.getToolNames());
    }

    @Test
    void shouldCompileCustomerProfileCountAndNormalizeUnusedZeroLimits() {
        BusinessQuestionAnalysis analysis = analysis(AgentQueryDomain.OPERATION_STATISTICS);
        analysis.setMetrics(List.of(AgentQueryMetric.CUSTOMER_PROFILE_COUNT));
        analysis.getFilters().setPage(0); analysis.getFilters().setSize(0); analysis.getFilters().setRecentLimit(0);

        var plan = service.plan(analysis);

        assertEquals(AgentQueryAction.SUMMARY, plan.getAction());
        assertEquals(List.of("getCustomerProfileCount"), plan.getToolNames());
        assertNull(plan.getFilters().getPage());
        assertNull(plan.getFilters().getSize());
        assertNull(plan.getFilters().getRecentLimit());
    }

    /** 活跃客户余额明细必须固定请求安全上限 50，不能落入执行器默认的 10 条分页。 */
    @Test
    void shouldUseFiftyItemPageForActiveCustomerBalanceDetail() {
        BusinessQuestionAnalysis analysis = analysis(AgentQueryDomain.OPERATION_STATISTICS);
        analysis.setMetrics(List.of(AgentQueryMetric.ACTIVE_CUSTOMER_MEAL_BALANCE_DETAIL));

        var plan = service.plan(analysis);

        assertEquals(AgentQueryAction.BREAKDOWN, plan.getAction());
        assertEquals(50, plan.getLimit());
        assertEquals(1, plan.getFilters().getPage());
        assertEquals(50, plan.getFilters().getSize());
        assertEquals(List.of("listActiveCustomerMealBalances"), plan.getToolNames());
    }

    private BusinessQuestionAnalysis analysis(AgentQueryDomain domain) {
        BusinessQuestionAnalysis analysis = new BusinessQuestionAnalysis();
        analysis.setDomains(List.of(domain));
        analysis.setEntities(new AgentEntityReference());
        analysis.setConfidence(0.95D);
        return analysis;
    }

    private void assertPlan(BusinessQuestionAnalysis analysis, AgentQueryAction action, String tool) {
        var plan = service.plan(analysis);
        assertEquals(action, plan.getAction());
        assertEquals(List.of(tool), plan.getToolNames());
        assertEquals("RULE", plan.getAnalysisSource());
    }
}
