package me.zhengjie.agent.application.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.agent.guardrail.ToolExecutionContext;
import me.zhengjie.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会话上下文合并器的跨轮焦点、日期互斥和安全来源测试。 */
class ConversationContextUpdaterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 首轮成功排餐查询应从强类型工具输入生成客户、日期、餐次和最近查询摘要。 */
    @Test
    void shouldCreateBusinessFocusFromSuccessfulToolInput() {
        ToolExecutionContext context = context();
        context.record(ToolRegistry.LIST_MEAL_PLANS, "MEAL_PLAN_LIST",
            "{\"customerCode\":\"B3303\",\"recordDate\":\"2026-08-09\",\"mealType\":\"LUNCH\"}",
            "{\"items\":[]}", true);

        ConversationContextUpdater.ContextUpdate update = updater().update(null, null, context.facts(),
            "2026-08-09T10:00:00+08:00");

        assertEquals("B3303", update.slots().getCustomerCode());
        assertEquals("2026-08-09", update.slots().getRecordDate());
        assertEquals("LUNCH", update.slots().getMealType());
        assertNull(update.slots().getStartDate());
        assertNull(update.slots().getEndDate());
        assertEquals("listMealPlans", update.lastBusinessQueryContext().get("lastToolName"));
        assertEquals(List.of("listMealPlans"), update.lastBusinessQueryContext().get("successfulToolNames"));
        assertEquals(Map.of("customerCode", "B3303", "recordDate", "2026-08-09", "mealType", "LUNCH"),
            update.lastBusinessQueryContext().get("filters"));
        assertEquals("2026-08-09T10:00:00+08:00", update.lastBusinessQueryContext().get("queriedAt"));
        assertEquals(false, update.lastBusinessQueryContext().get("partial"));
    }

    /** 第二轮只提交餐次时应继承首轮客户和日期，并替换餐次。 */
    @Test
    void shouldPreserveFocusWhenFollowUpOnlyChangesMealType() {
        DiagnosisSlots previous = slots("B3303", "O-1", "2026-08-09", null, null, "LUNCH");
        ToolExecutionContext context = context();
        context.record(ToolRegistry.LIST_MEAL_PLANS, "MEAL_PLAN_LIST", "{\"mealType\":\"DINNER\"}",
            "{\"items\":[]}", true);

        ConversationContextUpdater.ContextUpdate update = updater().update(previous, null, context.facts(),
            "2026-08-09T10:01:00+08:00");

        assertEquals("B3303", update.slots().getCustomerCode());
        assertEquals("O-1", update.slots().getOrderCode());
        assertEquals("2026-08-09", update.slots().getRecordDate());
        assertEquals("DINNER", update.slots().getMealType());
        assertEquals("DINNER", ((Map<?, ?>) update.lastBusinessQueryContext().get("filters")).get("mealType"));
    }

    /** 明确切换客户时必须清除旧订单和排餐记录焦点。 */
    @Test
    void shouldClearOrderFocusWhenCustomerChanges() {
        DiagnosisSlots previous = slots("B3303", "O-1", "2026-08-09", null, null, "LUNCH");
        previous.setOrderId(101L);
        previous.setMealPlanRecordId(201L);
        ToolExecutionContext context = context();
        context.record(ToolRegistry.GET_SERVICE_CUSTOMER_DETAIL, "SERVICE_CUSTOMER_DETAIL",
            "{\"customerCode\":\"C4404\"}", "{\"data\":{}}", true);

        ConversationContextUpdater.ContextUpdate update = updater().update(previous, null, context.facts(),
            "2026-08-09T10:02:00+08:00");

        assertEquals("C4404", update.slots().getCustomerCode());
        assertNull(update.slots().getOrderId());
        assertNull(update.slots().getOrderCode());
        assertNull(update.slots().getMealPlanRecordId());
    }

    /** 只提交新客户 ID 时也不能把旧客户编号留在新的客户焦点上。 */
    @Test
    void shouldClearOldCustomerCodeWhenCustomerIdChanges() {
        DiagnosisSlots previous = slots("B3303", "O-1", "2026-08-09", null, null, "LUNCH");
        previous.setCustomerId(101L);
        ToolExecutionContext context = context();
        context.record(ToolRegistry.GET_SERVICE_CUSTOMER_DETAIL, "SERVICE_CUSTOMER_DETAIL",
            "{\"customerId\":102}", "{\"data\":{}}", true);

        ConversationContextUpdater.ContextUpdate update = updater().update(previous, null, context.facts(),
            "2026-08-09T10:02:30+08:00");

        assertEquals(102L, update.slots().getCustomerId());
        assertNull(update.slots().getCustomerCode());
        assertNull(update.slots().getOrderCode());
    }

    /** 单日和日期范围必须互斥，日期范围还必须是完整且有序的日期对。 */
    @Test
    void shouldSwitchBetweenSingleDateAndDateRange() {
        DiagnosisSlots previous = slots("B3303", null, "2026-08-01", null, null, null);
        ToolExecutionContext rangeContext = context();
        rangeContext.record(ToolRegistry.LIST_VERIFICATIONS, "VERIFICATION_LIST",
            "{\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-09\"}", "{\"items\":[]}", true);

        ConversationContextUpdater.ContextUpdate range = updater().update(previous, null, rangeContext.facts(),
            "2026-08-09T10:03:00+08:00");
        assertNull(range.slots().getRecordDate());
        assertEquals("2026-08-01", range.slots().getStartDate());
        assertEquals("2026-08-09", range.slots().getEndDate());

        ToolExecutionContext singleContext = context();
        singleContext.record(ToolRegistry.LIST_VERIFICATIONS, "VERIFICATION_LIST",
            "{\"recordDate\":\"2026-08-10\"}", "{\"items\":[]}", true);
        ConversationContextUpdater.ContextUpdate single = updater().update(range.slots(), null,
            singleContext.facts(), "2026-08-10T10:00:00+08:00");
        assertEquals("2026-08-10", single.slots().getRecordDate());
        assertNull(single.slots().getStartDate());
        assertNull(single.slots().getEndDate());
    }

    /** 模糊姓名、失败工具和公共菜单查询不得创建或替换客户焦点。 */
    @Test
    void shouldIgnoreFuzzyCustomerAndFailedFacts() {
        DiagnosisSlots previous = slots("B3303", null, "2026-08-01", null, null, null);
        ToolExecutionContext context = context();
        context.record(ToolRegistry.SEARCH_CUSTOMER_PROFILES, "CUSTOMER_PROFILE_LIST",
            "{\"customerName\":\"张三\"}", "{\"items\":[]}", true);
        context.record(ToolRegistry.SEARCH_SERVICE_CUSTOMERS, "SERVICE_CUSTOMER_LIST",
            "{\"customerCode\":\"C9999\"}", "{\"errorCode\":\"ACCESS_DENIED\"}", false);
        context.record(ToolRegistry.LIST_SCHEDULED_DISHES, "DISH_LIST",
            "{\"recordDate\":\"2026-08-09\",\"mealTypes\":[\"LUNCH\",\"DINNER\"]}",
            "{\"items\":[]}", true);

        ConversationContextUpdater.ContextUpdate update = updater().update(previous, null, context.facts(),
            "2026-08-09T10:04:00+08:00");

        assertEquals("B3303", update.slots().getCustomerCode());
        assertEquals("2026-08-09", update.slots().getRecordDate());
        assertNull(update.slots().getMealType());
        assertTrue(update.lastBusinessQueryContext().get("partial") instanceof Boolean);
        assertTrue((Boolean) update.lastBusinessQueryContext().get("partial"));
    }

    /** 无成功工具事实时应返回原有安全上下文，不能从失败输入更新槽位。 */
    @Test
    void shouldPreservePreviousContextWhenNoToolSucceeds() {
        DiagnosisSlots previous = slots("B3303", "O-1", "2026-08-01", null, null, "LUNCH");
        Map<String, Object> previousSummary = Map.of("lastToolName", "listMealPlans", "partial", false);
        ToolExecutionContext context = context();
        context.record(ToolRegistry.LIST_MEAL_PLANS, "MEAL_PLAN_LIST",
            "{\"customerCode\":\"C9999\",\"mealType\":\"DINNER\"}",
            "{\"errorCode\":\"ACCESS_DENIED\"}", false);

        ConversationContextUpdater.ContextUpdate update = updater().update(previous, previousSummary, context.facts(),
            "2026-08-09T10:05:00+08:00");

        assertEquals("B3303", update.slots().getCustomerCode());
        assertEquals("O-1", update.slots().getOrderCode());
        assertEquals("LUNCH", update.slots().getMealType());
        assertEquals(previousSummary, update.lastBusinessQueryContext());
        assertFalse(update.slots() == previous);
    }

    /** 构造最小工具执行上下文。 */
    private ToolExecutionContext context() {
        return new ToolExecutionContext(objectMapper, 6, 100);
    }

    /** 构造使用固定工具名上限的合并器。 */
    private ConversationContextUpdater updater() {
        return new ConversationContextUpdater(objectMapper, 6);
    }

    /** 构造测试用的业务焦点槽位。 */
    private DiagnosisSlots slots(String customerCode, String orderCode, String recordDate,
                                 String startDate, String endDate, String mealType) {
        DiagnosisSlots slots = new DiagnosisSlots();
        slots.setCustomerCode(customerCode);
        slots.setOrderCode(orderCode);
        slots.setRecordDate(recordDate);
        slots.setStartDate(startDate);
        slots.setEndDate(endDate);
        slots.setMealType(mealType);
        return slots;
    }
}
