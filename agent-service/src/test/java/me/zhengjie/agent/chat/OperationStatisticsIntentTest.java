package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 运营统计问法必须进入受控统计链路，不能退回客户诊断。 */
class OperationStatisticsIntentTest {
    private final RuleBasedSlotExtractor extractor = new RuleBasedSlotExtractor(
        Clock.fixed(Instant.parse("2026-07-13T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

    @Test
    void shouldRecognizeDailyUnverifiedCustomerQuestion() {
        ChatExtractionResult result = extractor.extract("今天午餐待核销客户有多少", null);

        assertEquals(ChatIntent.OPERATION_STATISTICS_QUERY, result.getIntent());
        assertEquals("2026-07-13", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
    }

    /** 统计问句中的“客户数”不得被宽松姓名规则识别为客户焦点。 */
    @Test
    void shouldNotTreatOperationCustomerCountAsCustomerName() {
        ChatExtractionResult result = extractor.extract("今天待排餐客户数是多少", null);

        assertEquals(ChatIntent.OPERATION_STATISTICS_QUERY, result.getIntent());
        assertEquals(null, result.getSlots().getCustomerName());
        assertEquals(null, result.getSlots().getCustomerId());
        assertEquals(null, result.getSlots().getCustomerCode());
    }

    @Test
    void shouldRecognizeAmbiguousRemainingCustomerQuestion() {
        ChatExtractionResult result = extractor.extract("今天还有多少客户", null);

        assertEquals(ChatIntent.OPERATION_STATISTICS_QUERY, result.getIntent());
    }

    @Test
    void shouldRecognizeSystemCustomerTotalQuestion() {
        ChatExtractionResult result = extractor.extract("现在系统中还有多少客户", null);

        assertEquals(ChatIntent.OPERATION_STATISTICS_QUERY, result.getIntent());
    }

    @Test
    void shouldResolveClarificationOptionWithExistingDate() {
        DiagnosisSlots context = new DiagnosisSlots();
        context.setRecordDate("2026-07-13");

        ChatExtractionResult result = extractor.extract("待核销", context);

        assertEquals(ChatIntent.OPERATION_STATISTICS_QUERY, result.getIntent());
        assertEquals("2026-07-13", result.getSlots().getRecordDate());
    }

    @Test
    void shouldRecognizeMealBalanceWithoutPlanAsOperationStatistic() {
        ChatExtractionResult result = extractor.extract("现在还有多少客户有餐数没有排餐", null);

        assertEquals(ChatIntent.OPERATION_STATISTICS_QUERY, result.getIntent());
    }
}
