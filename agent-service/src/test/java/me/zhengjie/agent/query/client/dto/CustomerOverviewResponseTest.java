package me.zhengjie.agent.query.client.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 主系统扩展概览字段时，Agent 仅保留展示白名单字段而不能因未知字段中断查询。 */
class CustomerOverviewResponseTest {

    @Test
    void shouldIgnoreUnknownOverviewAndNestedFields() throws Exception {
        String json = "{\"present\":true,\"customerId\":71,\"customerCode\":\"B3303\","
            + "\"addresses\":[{\"maskedAddress\":\"***\"}],\"mealBalance\":{\"remainingBreakfast\":2},"
            + "\"latestRefund\":{\"refundId\":9,\"customerId\":71,\"verifiedLunchDinnerCount\":1,\"operateTime\":\"2026-07-13 09:30:00\"}}";

        CustomerOverviewResponse response = new ObjectMapper().readValue(json, CustomerOverviewResponse.class);
        Map<String, Object> presentation = response.toPresentationMap();

        assertEquals(71L, response.getCustomerId());
        assertEquals(2, response.getMealBalance().getRemainingBreakfast());
        assertEquals(9L, response.getLatestRefund().getRefundId());
        assertEquals("2026-07-13 09:30:00", response.getLatestRefund().getOperateTime());
        assertFalse(presentation.containsKey("addresses"));
        assertTrue(((Map<?, ?>) presentation.get("latestRefund")).containsKey("refundId"));
    }
}
