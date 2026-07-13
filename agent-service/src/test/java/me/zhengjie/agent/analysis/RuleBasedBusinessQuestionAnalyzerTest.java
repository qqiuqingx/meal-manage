package me.zhengjie.agent.analysis;

import me.zhengjie.agent.analysis.domain.BusinessQuestionAnalysis;
import me.zhengjie.agent.query.domain.AgentQueryDomain;
import me.zhengjie.agent.query.domain.AgentQueryDimension;
import me.zhengjie.agent.query.domain.AgentQueryMetric;
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
}
