package me.zhengjie.agent.chat;

import me.zhengjie.agent.domain.chat.ChatIntent;
import me.zhengjie.agent.domain.chat.MissingSlot;
import me.zhengjie.agent.domain.dto.ChatExtractionResult;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessQueryIntentPolicyTest {
    private final BusinessQueryIntentPolicy policy = new BusinessQueryIntentPolicy();

    @Test
    void mapsOnlyRegisteredCompatibilityIntent() {
        ChatExtractionResult extraction = new ChatExtractionResult();
        extraction.setRuleIntent(ChatIntent.CUSTOMER_ORDER_QUERY.name());

        assertEquals(ChatIntent.CUSTOMER_ORDER_QUERY, policy.compatibilityIntent(extraction));

        extraction.setRuleIntent(ChatIntent.DIAGNOSE.name());
        assertNull(policy.compatibilityIntent(extraction));
        extraction.setRuleIntent("UNKNOWN_INTENT");
        assertNull(policy.compatibilityIntent(extraction));
    }

    @Test
    void keepsAmountAndCustomerSlotRulesOutsideDefaultHandler() {
        assertTrue(policy.isAmountQuery("这个订单多少钱"));
        assertFalse(policy.isAmountQuery("查询客户订单"));
        assertEquals(MissingSlot.CUSTOMER,
            policy.missingSlotsForInsight(new DiagnosisSlots()).get(0));

        DiagnosisSlots identified = new DiagnosisSlots();
        identified.setOrderCode("O1001");
        assertTrue(policy.missingSlotsForInsight(identified).isEmpty());
        assertTrue(policy.isCustomerInsightIntent(ChatIntent.CUSTOMER_VERIFICATION_QUERY));
        assertFalse(policy.isCustomerInsightIntent(ChatIntent.DIAGNOSE));
    }
}
