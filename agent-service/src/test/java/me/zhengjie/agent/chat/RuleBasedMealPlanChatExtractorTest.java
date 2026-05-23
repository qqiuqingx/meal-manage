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
        ChatExtractionResult result = extractor.extract("帮我看下客户 C10001 明天午餐为什么没排出来", new DiagnosisSlots());

        assertEquals(ChatIntent.DIAGNOSE, result.getIntent());
        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals("2026-05-23", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
        assertEquals(List.of(), result.getMissingSlots());
    }

    @Test
    void shouldExtractCustomerIdTodayAndBreakfast() {
        ChatExtractionResult result = extractor.extract("查客户ID 1001 今天早餐", new DiagnosisSlots());

        assertEquals(1001L, result.getSlots().getCustomerId());
        assertEquals("2026-05-22", result.getSlots().getRecordDate());
        assertEquals("BREAKFAST", result.getSlots().getMealType());
    }

    @Test
    void shouldUseExistingSlotsWhenUserOnlyRepliesMealType() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("C10001");
        existing.setRecordDate("2026-05-24");

        ChatExtractionResult result = extractor.extract("晚餐", existing);

        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals("2026-05-24", result.getSlots().getRecordDate());
        assertEquals("DINNER", result.getSlots().getMealType());
        assertEquals(List.of(), result.getMissingSlots());
    }

    @Test
    void shouldDetectMissingCustomerDateAndMealType() {
        ChatExtractionResult result = extractor.extract("帮我排查一下", new DiagnosisSlots());

        assertEquals(ChatIntent.DIAGNOSE, result.getIntent());
        assertTrue(result.getMissingSlots().contains(MissingSlot.CUSTOMER));
        assertTrue(result.getMissingSlots().contains(MissingSlot.RECORD_DATE));
        assertTrue(result.getMissingSlots().contains(MissingSlot.MEAL_TYPE));
    }

    @Test
    void shouldDetectResetIntent() {
        ChatExtractionResult result = extractor.extract("清空会话，重新开始", new DiagnosisSlots());

        assertEquals(ChatIntent.RESET, result.getIntent());
    }

    @Test
    void shouldDetectOutOfScopeIntent() {
        ChatExtractionResult result = extractor.extract("帮我改一下订单地址", new DiagnosisSlots());

        assertEquals(ChatIntent.OUT_OF_SCOPE, result.getIntent());
    }

    @Test
    void shouldDetectFollowUpWhenDiagnosisExists() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("C10001");
        existing.setRecordDate("2026-05-22");
        existing.setMealType("LUNCH");

        ChatExtractionResult result = extractor.extract("为什么订单无效？", existing);

        assertEquals(ChatIntent.FOLLOW_UP, result.getIntent());
    }
}
