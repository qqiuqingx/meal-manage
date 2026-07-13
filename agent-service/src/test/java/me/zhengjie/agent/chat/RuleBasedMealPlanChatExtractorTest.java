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

    @Test
    void shouldExtractMealPlanQueryForCustomerDateAndMeal() {
        RuleBasedMealPlanChatExtractor extractor = new RuleBasedMealPlanChatExtractor(Clock.fixed(
            Instant.parse("2026-07-11T01:00:00Z"), ZoneId.of("Asia/Shanghai")));

        ChatExtractionResult result = extractor.extract("B3303 今天午餐排了吗", new DiagnosisSlots());

        assertEquals(ChatIntent.MEAL_PLAN_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
        assertEquals("2026-07-11", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
    }

    @Test
    void shouldTreatRefundHistoryAsReadOnlyQuery() {
        ChatExtractionResult result = extractor.extract("B3303 最近退过餐吗", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_REFUND_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
    }

    @Test
    void shouldRejectAmountQueryWithoutCreatingBusinessPlan() {
        ChatExtractionResult result = extractor.extract("B3303 订单多少钱", new DiagnosisSlots());

        assertEquals(ChatIntent.OUT_OF_SCOPE, result.getIntent());
    }

    @Test
    void shouldExtractStructuredMealBalanceRuleQuestion() {
        ChatExtractionResult result = extractor.extract("午餐核销扣哪个池", new DiagnosisSlots());

        assertEquals(ChatIntent.BUSINESS_RULE_QUERY, result.getIntent());
    }

    @Test
    void shouldExtractMealPlanAndDietaryRuleQuestions() {
        assertEquals(ChatIntent.BUSINESS_RULE_QUERY, extractor.extract("排餐模式怎么匹配餐次", new DiagnosisSlots()).getIntent());
        assertEquals(ChatIntent.BUSINESS_RULE_QUERY, extractor.extract("过敏菜为什么会被过滤", new DiagnosisSlots()).getIntent());
        assertEquals(ChatIntent.BUSINESS_RULE_QUERY, extractor.extract("退餐对餐数有什么影响", new DiagnosisSlots()).getIntent());
    }

    /** 客服常用的客户概览问法不应被误判为需要日期和餐次的排餐诊断。 */
    @Test
    void shouldExtractCustomerOverviewIntentWithoutDateAndMealType() {
        ChatExtractionResult result = extractor.extract("B3303 目前什么情况？", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
        assertTrue(result.getMissingSlots().isEmpty());
    }

    @Test
    void shouldExtractCustomerPackageQuestion() {
        ChatExtractionResult result = extractor.extract("B3303 签了什么套餐", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_PACKAGE_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
    }

    @Test
    void shouldExtractDishIngredientQuestion() {
        ChatExtractionResult result = extractor.extract("B3303 今天午餐菜里有什么配料", new DiagnosisSlots());

        assertEquals(ChatIntent.DISH_INGREDIENT_QUERY, result.getIntent());
        assertEquals("2026-05-22", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
    }

    @Test
    void shouldExtractCustomerCandidateDishQuestion() {
        ChatExtractionResult result = extractor.extract("B3303 今天午餐有哪些菜可以吃", new DiagnosisSlots());

        assertEquals(ChatIntent.DISH_CANDIDATE_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
        assertEquals("2026-05-22", result.getSlots().getRecordDate());
        assertEquals("LUNCH", result.getSlots().getMealType());
    }

    @Test
    void shouldExtractOrderCodeWithoutTreatingItAsCustomerCode() {
        ChatExtractionResult result = extractor.extract("订单 O20260711001 什么情况", new DiagnosisSlots());

        assertEquals("O20260711001", result.getSlots().getOrderCode());
        assertEquals(null, result.getSlots().getCustomerCode());
    }

    /** 带订单编号的有效期问法必须走订单详情而不是排餐诊断。 */
    @Test
    void shouldExtractOrderDetailIntentForExpiryQuestion() {
        ChatExtractionResult result = extractor.extract("订单 O20260711001 什么时候到期", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_ORDER_QUERY, result.getIntent());
        assertEquals("O20260711001", result.getSlots().getOrderCode());
    }

    /** 排餐客户记录 ID 可作为排餐详情受控实体标识。 */
    @Test
    void shouldExtractMealPlanRecordIdForDirectDetailQuery() {
        ChatExtractionResult result = extractor.extract("排餐记录ID 9001 吃什么", new DiagnosisSlots());

        assertEquals(ChatIntent.MEAL_PLAN_QUERY, result.getIntent());
        assertEquals(9001L, result.getSlots().getMealPlanRecordId());
        assertTrue(result.getMissingSlots().isEmpty());
    }

    @Test
    void shouldExtractUnverifiedMealPlanQuestion() {
        ChatExtractionResult result = extractor.extract("B3303 今天午餐已排餐但未核销吗", new DiagnosisSlots());

        assertEquals(ChatIntent.MEAL_PLAN_UNVERIFIED_QUERY, result.getIntent());
    }

    @Test
    void shouldClearOrderContextWhenCustomerChanges() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("B3303");
        existing.setOrderCode("O20260711001");

        ChatExtractionResult result = extractor.extract("换成 C10001 看看订单", existing);

        assertEquals("C10001", result.getSlots().getCustomerCode());
        assertEquals(null, result.getSlots().getOrderCode());
    }

    @Test
    void shouldExtractMealBalanceNoPlanQuestion() {
        ChatExtractionResult result = extractor.extract("B3303 有餐数但今天午餐没排", new DiagnosisSlots());

        assertEquals(ChatIntent.MEAL_BALANCE_NO_PLAN_QUERY, result.getIntent());
    }

    @Test
    void shouldExtractMealBalanceChangeQuestion() {
        ChatExtractionResult result = extractor.extract("B3303 为什么餐数少了", new DiagnosisSlots());

        assertEquals(ChatIntent.MEAL_BALANCE_CHANGE_QUERY, result.getIntent());
    }

    /** 本月退餐问法应生成受控月度范围而非无界历史查询。 */
    @Test
    void shouldExtractCurrentMonthRangeForRefundQuery() {
        ChatExtractionResult result = extractor.extract("B3303 本月退过餐吗", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_REFUND_QUERY, result.getIntent());
        assertEquals("2026-05-01", result.getSlots().getStartDate());
        assertEquals("2026-05-22", result.getSlots().getEndDate());
        assertEquals(null, result.getSlots().getRecordDate());
    }

    /** 明确单日问法必须清理上一轮的范围槽位，避免 QueryPlan 日期条件冲突。 */
    @Test
    void shouldClearRangeWhenUserSwitchesToSingleDate() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("B3303");
        existing.setStartDate("2026-05-01");
        existing.setEndDate("2026-05-22");

        ChatExtractionResult result = extractor.extract("今天核销记录", existing);

        assertEquals("2026-05-22", result.getSlots().getRecordDate());
        assertEquals(null, result.getSlots().getStartDate());
        assertEquals(null, result.getSlots().getEndDate());
    }

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
    void shouldExtractNonCPrefixCustomerCode() {
        ChatExtractionResult result = extractor.extract("B3303 这个客户还剩多少餐数", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
        assertEquals("HIGH", result.getSlots().getCustomerConfidence());
        assertTrue(result.getMissingSlots().contains(MissingSlot.CUSTOMER) == false);
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

    @Test
    void shouldDetectVerificationIntent() {
        ChatExtractionResult result = extractor.extract("B3303 核销了多少餐", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_VERIFICATION_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
    }

    @Test
    void shouldDetectMealBalanceIntentForTotalMealQuestionWithContextCustomer() {
        DiagnosisSlots existing = new DiagnosisSlots();
        existing.setCustomerCode("B2201");

        ChatExtractionResult result = extractor.extract("他一共多少餐？", existing);

        assertEquals(ChatIntent.CUSTOMER_MEAL_BALANCE_QUERY, result.getIntent());
        assertEquals("B2201", result.getSlots().getCustomerCode());
        assertEquals("MEDIUM", result.getSlots().getCustomerConfidence());
    }

    @Test
    void shouldDetectOrderIntentAndActiveOrderFilter() {
        ChatExtractionResult result = extractor.extract("B3303 有哪些进行中订单", new DiagnosisSlots());

        assertEquals(ChatIntent.CUSTOMER_ORDER_QUERY, result.getIntent());
        assertEquals("B3303", result.getSlots().getCustomerCode());
        assertEquals(1, result.getSlots().getOrderStatus());
    }
}
