package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
import me.zhengjie.agent.query.domain.LastBusinessQueryContext;
import me.zhengjie.agent.analysis.domain.BusinessQueryTarget;
import me.zhengjie.agent.analysis.domain.BusinessInteractionMode;
import me.zhengjie.agent.analysis.domain.MealScope;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则分析器应在模型不可用时保留安全且可解释的统计识别能力。 */
class RuleBasedBusinessQuestionAnalyzerTest {
    private final RuleBasedBusinessQuestionAnalyzer analyzer = new RuleBasedBusinessQuestionAnalyzer();

    @Test
    void shouldRequireClarificationForAmbiguousRemainingCustomers() {
        BusinessQuestionAnalysis result = analyzer.analyze("今天还有多少客户", null);

        assertEquals(AgentQueryDomain.OPERATION_STATISTICS, result.getDomains().get(0));
        assertTrue(result.isRequiresClarification());
        assertEquals("remainingMeaning", result.getAmbiguities().get(0).getField());
    }

    @Test
    void shouldRecognizeUnverifiedCustomerWorkload() {
        BusinessQuestionAnalysis result = analyzer.analyze("今天午餐待核销客户有多少", null);

        assertEquals(AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT, result.getMetrics().get(0));
        assertEquals("LUNCH", result.getFilters().getMealType());
    }

    @Test
    void shouldDistinguishMealBalanceCustomersFromDailyUnscheduledCustomers() {
        BusinessQuestionAnalysis active = analyzer.analyze("现在还有多少客户有餐数", null);
        BusinessQuestionAnalysis unscheduled = analyzer.analyze("现在还有多少客户有餐数没有排餐", null);

        assertEquals(AgentQueryMetric.ACTIVE_CUSTOMER_COUNT, active.getMetrics().get(0));
        assertEquals(AgentQueryMetric.DAILY_UNSCHEDULED_CUSTOMER_COUNT, unscheduled.getMetrics().get(0));
        assertEquals(me.zhengjie.agent.analysis.domain.BusinessTemporalExpression.CURRENT_DAY,
            unscheduled.getTemporal().getExpression());
    }

    /** 集合追问的自然说法必须落到餐数余额指标，不能被规则兜底误判为未知问题。 */
    @Test
    void shouldRecognizeNaturalMealBalanceFollowUp() {
        BusinessQuestionAnalysis result = analyzer.analyze("他们分别还剩多少餐呢", null);

        assertEquals(AgentQueryDomain.ORDER, result.getDomains().get(0));
        assertEquals(AgentQueryMetric.MEAL_BALANCE, result.getMetrics().get(0));
        assertTrue(!result.isRequiresClarification());
    }

    /** 客户创建和首次购买时间属于客户概览，不应回退为未知业务澄清。 */
    @Test
    void shouldRecognizeCustomerTimelineQuestions() {
        BusinessQuestionAnalysis created = analyzer.analyze("B2200 这个客户是什么时候添加的", null);
        BusinessQuestionAnalysis purchased = analyzer.analyze("B2200 这个客户是什么时候购买的呢", null);
        BusinessQuestionAnalysis overview = analyzer.analyze("查一下 B2200 的客户信息", null);

        assertEquals(BusinessQueryTarget.CUSTOMER, created.getQueryTarget());
        assertEquals(AgentQueryDomain.CUSTOMER, created.getDomains().get(0));
        assertEquals(BusinessQueryTarget.CUSTOMER, purchased.getQueryTarget());
        assertTrue(!purchased.isRequiresClarification());
        assertEquals(BusinessQueryTarget.CUSTOMER, overview.getQueryTarget());
    }

    @Test
    void shouldRecognizeSystemCustomerCountAsProfileTotal() {
        BusinessQuestionAnalysis result = analyzer.analyze("现在系统中还有多少客户", null);

        assertEquals(AgentQueryMetric.CUSTOMER_PROFILE_COUNT, result.getMetrics().get(0));
        assertTrue(!result.isRequiresClarification());
    }

    @Test
    void shouldKeepCustomerMealPlanTargetWhenDateNeedsClarification() {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode("B3303");

        BusinessQuestionAnalysis result = analyzer.analyze("查 B3303 的排餐", slots);

        assertEquals(BusinessQueryTarget.CUSTOMER_MEAL_PLAN, result.getQueryTarget());
        assertEquals(AgentQueryDomain.MEAL_PLAN, result.getDomains().get(0));
        assertTrue(result.isRequiresClarification());
    }

    @Test
    void shouldRecognizeSameSourceDailyMetricsAsControlledReport() {
        BusinessQuestionAnalysis result = analyzer.analyze("今天午餐已排餐和待核销客户分别多少", null);

        assertEquals(List.of(AgentQueryMetric.DAILY_SCHEDULED_CUSTOMER_COUNT,
            AgentQueryMetric.DAILY_UNVERIFIED_CUSTOMER_COUNT), result.getMetrics());
        assertEquals(AgentQueryDomain.OPERATION_STATISTICS, result.getDomains().get(0));
    }

    /** 套餐和来源属于登记维度，应从自然语言映射为受控枚举。 */
    @Test
    void shouldRecognizePackageAndCustomerSourceDimensions() {
        BusinessQuestionAnalysis result = analyzer.analyze("今天待排餐客户按套餐和来源分组", null);

        assertEquals(List.of(AgentQueryDimension.PACKAGE, AgentQueryDimension.CUSTOMER_SOURCE), result.getDimensions());
        assertTrue(!result.isRequiresClarification());
    }

    /** 三个维度会超过报表预算，必须追问而不能静默忽略其中一个。 */
    @Test
    void shouldRequireClarificationWhenMoreThanTwoDimensionsRequested() {
        BusinessQuestionAnalysis result = analyzer.analyze("今天待核销客户按餐次、套餐和来源分组", null);

        assertTrue(result.isRequiresClarification());
        assertEquals("dimensions", result.getAmbiguities().get(0).getField());
    }

    @Test
    void shouldUseAllAvailableScopeForPublicMenuWithoutMealType() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setRecordDate("2026-07-13");

        BusinessQuestionAnalysis result = analyzer.analyze("今天的菜单是什么", slots);

        assertEquals(BusinessQueryTarget.SCHEDULED_MENU, result.getQueryTarget());
        assertEquals(MealScope.ALL_AVAILABLE, result.getMealScope());
        assertEquals(AgentQueryDomain.DISH, result.getDomains().get(0));
    }

    @Test
    void shouldRecognizeRiceOnlyComplaintAsMenuCorrection() {
        DiagnosisSlots slots = new DiagnosisSlots(); slots.setRecordDate("2026-07-13");
        LastBusinessQueryContext previous = new LastBusinessQueryContext(); previous.setQueryTarget(BusinessQueryTarget.SCHEDULED_MENU);

        BusinessQuestionAnalysis result = analyzer.analyze("怎么全是米饭", slots, previous);

        assertEquals(BusinessInteractionMode.CORRECTION, result.getInteractionMode());
        assertTrue(result.getCorrection().isRequiresReplan());
        assertEquals(List.of("ONLY_RICE_RETURNED"), result.getCorrection().getObservations());
    }

}
