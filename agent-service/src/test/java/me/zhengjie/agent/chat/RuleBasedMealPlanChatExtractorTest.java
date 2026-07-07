package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedMealPlanChatExtractorTest {

    private final RuleBasedMealPlanChatExtractor extractor = new RuleBasedMealPlanChatExtractor(
        Clock.fixed(Instant.parse("2026-05-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void shouldExtractCustomerCodeTomorrowAndLunch() {
        ChatExtractionResult result = extractor.extract("看下 C10001 明天午餐为什么没排出来", new DiagnosisSlots());

        assertEquals(ChatIntent.DIAGNOSE, result.getIntent());
        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals("2026-05-23", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
        assertEquals("HIGH", result.getSlots().getCustomerConfidence());
        assertEquals("HIGH", result.getSlots().getRecordDateConfidence());
        assertEquals("HIGH", result.getSlots().getMealTypeConfidence());
        assertEquals(List.of(), result.getMissingSlots());
        assertEquals(List.of(), result.getAmbiguousSlots());
    }

    @Test
    void shouldOverrideDateAndMealTypeForRelativeChange() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("C10001");
        existing.setRecordDate("2026-05-22");
        existing.setMealType("LUNCH");

        ChatExtractionResult result = extractor.extract("换成后天晚餐", existing);

        assertEquals(ChatIntent.DIAGNOSE, result.getIntent());
        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals("2026-05-24", result.getSlots().getRecordDate());
        assertEquals("DINNER", result.getSlots().getMealType());
        assertEquals("MEDIUM", result.getSlots().getCustomerConfidence());
        assertEquals("HIGH", result.getSlots().getRecordDateConfidence());
        assertEquals("HIGH", result.getSlots().getMealTypeConfidence());
        assertEquals("CONTEXT_INHERIT", result.getSlots().getCustomerSource());
        assertEquals("CORRECTION_OVERRIDE", result.getSlots().getRecordDateSource());
        assertEquals("CORRECTION_OVERRIDE", result.getSlots().getMealTypeSource());
    }

    @Test
    void shouldChooseCorrectedMealType() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("C10001");
        existing.setRecordDate("2026-05-24");
        existing.setMealType("LUNCH");

        ChatExtractionResult result = extractor.extract("不是午餐，是晚餐", existing);

        assertEquals("DINNER", result.getSlots().getMealType());
        assertEquals("HIGH", result.getSlots().getMealTypeConfidence());
        assertEquals("CORRECTION_OVERRIDE", result.getSlots().getMealTypeSource());
    }

    @Test
    void shouldExtractCustomerCodeWithLabel() {
        ChatExtractionResult result = extractor.extract("客户编号 C10001", new DiagnosisSlots());

        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals("HIGH", result.getSlots().getCustomerConfidence());
        assertTrue(result.getMissingSlots().contains(MissingSlot.RECORD_DATE));
        assertTrue(result.getMissingSlots().contains(MissingSlot.MEAL_TYPE));
    }

    @Test
    void shouldExtractCustomerId() {
        ChatExtractionResult result = extractor.extract("客户ID 123", new DiagnosisSlots());

        assertEquals(123L, result.getSlots().getCustomerId());
        assertEquals("HIGH", result.getSlots().getCustomerConfidence());
        assertTrue(result.getMissingSlots().contains(MissingSlot.RECORD_DATE));
        assertTrue(result.getMissingSlots().contains(MissingSlot.MEAL_TYPE));
    }

    @Test
    void shouldExtractTodayBreakfastAlias() {
        ChatExtractionResult result = extractor.extract("今天早饭", new DiagnosisSlots());

        assertEquals("2026-05-22", result.getSlots().getRecordDate());
        assertEquals("BREAKFAST", result.getSlots().getMealType());
        assertEquals("HIGH", result.getSlots().getRecordDateConfidence());
        assertEquals("HIGH", result.getSlots().getMealTypeConfidence());
    }

    @Test
    void shouldResolveNextWeekday() {
        ChatExtractionResult result = extractor.extract("下周一午餐", new DiagnosisSlots());

        assertEquals("2026-05-25", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
        assertEquals("HIGH", result.getSlots().getRecordDateConfidence());
    }

    @Test
    void shouldDetectRetryIntent() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("C10001");
        existing.setRecordDate("2026-05-22");
        existing.setMealType("LUNCH");

        ChatExtractionResult result = extractor.extract("重新排查", existing);

        assertEquals(ChatIntent.RETRY, result.getIntent());
        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals("2026-05-22", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
    }

    @Test
    void shouldDetectResetIntent() {
        ChatExtractionResult result = extractor.extract("清空会话", new DiagnosisSlots());

        assertEquals(ChatIntent.RESET, result.getIntent());
        assertEquals(List.of(), result.getAmbiguousSlots());
    }

    @Test
    void shouldDetectAmbiguousCustomerAndRequireConfirmation() {
        ChatExtractionResult result = extractor.extract("客户 123 今天午餐", new DiagnosisSlots());

        assertEquals(ChatIntent.DIAGNOSE, result.getIntent());
        assertEquals("123", result.getSlots().getCustomerCode());
        assertEquals("LOW", result.getSlots().getCustomerConfidence());
        assertEquals(List.of(MissingSlot.CUSTOMER), result.getAmbiguousSlots());
        assertEquals(List.of(), result.getMissingSlots());
    }

    @Test
    void shouldDetectFollowUpWhenDiagnosisExistsAndNoAmbiguity() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("C10001");
        existing.setRecordDate("2026-05-22");
        existing.setMealType("LUNCH");

        ChatExtractionResult result = extractor.extract("为什么候选菜为空？", existing);

        assertEquals(ChatIntent.FOLLOW_UP, result.getIntent());
    }
}
