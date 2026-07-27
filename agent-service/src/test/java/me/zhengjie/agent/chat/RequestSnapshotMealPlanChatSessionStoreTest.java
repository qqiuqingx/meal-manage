package me.zhengjie.agent.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 无状态存储不得把上一轮 JVM 内对象作为下一轮真相。 */
class RequestSnapshotMealPlanChatSessionStoreTest {
    @Test
    void createsFreshSessionForEachRequest() {
        RequestSnapshotMealPlanChatSessionStore store = new RequestSnapshotMealPlanChatSessionStore();
        MealPlanChatSession first = store.getOrCreate("s-1");
        first.getSlots().setCustomerCode("C1001");
        store.save(first);
        MealPlanChatSession second = store.getOrCreate("s-1");
        assertNotSame(first, second);
        assertEquals("s-1", second.getSessionId());
        org.junit.jupiter.api.Assertions.assertNull(second.getSlots().getCustomerCode());
    }
}
