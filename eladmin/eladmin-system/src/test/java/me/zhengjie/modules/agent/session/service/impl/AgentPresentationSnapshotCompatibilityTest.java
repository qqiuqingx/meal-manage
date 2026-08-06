package me.zhengjie.modules.agent.session.service.impl;

import com.alibaba.fastjson2.JSON;
import me.zhengjie.modules.agent.mapper.AgentActionAuditMapper;
import me.zhengjie.modules.agent.mapper.AgentDiagnosisFeedbackMapper;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import me.zhengjie.modules.agent.session.mapper.AgentChatMessageMapper;
import me.zhengjie.modules.agent.session.mapper.AgentChatSessionMapper;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 固定快照样例验证展示描述的新旧兼容，确保恢复只读取持久化结果而不重新查询业务。
 */
@ExtendWith(MockitoExtension.class)
class AgentPresentationSnapshotCompatibilityTest {

    @Mock
    private AgentChatSessionMapper sessionMapper;

    @Mock
    private AgentChatMessageMapper messageMapper;

    @Mock
    private AgentActionAuditMapper actionAuditMapper;

    @Mock
    private AgentDiagnosisFeedbackMapper feedbackMapper;

    @Mock
    private AgentDiagnosisFacadeService diagnosisFacadeService;

    @Mock
    private AgentAccessContextService accessContextService;

    @Mock
    private AgentBusinessQueryAuditService businessQueryAuditService;

    @InjectMocks
    private AgentChatSessionServiceImpl service;

    private AgentChatSession session;

    @BeforeEach
    void setUpSession() {
        session = new AgentChatSession();
        session.setId(1L);
        session.setSessionId("session-compatibility");
        session.setOperator("system");
        session.setArchived(false);
        session.setVersion(4);
    }

    @Test
    void shouldRestoreDeepPresentationSnapshotWithoutCallingAgentOrBusinessQuery() {
        String snapshot = "{\"cards\":[{\"type\":\"SERVICE_CUSTOMER_LIST\",\"data\":{\"items\":[{\"customerCode\":\"C10001\",\"maskedName\":\"历史掩码\",\"mealBalance\":{\"breakfast\":3,\"lunchDinner\":8}}]}}],\"presentations\":[{\"schemaVersion\":\"v1\",\"sourceToolCallId\":\"call-1\",\"cardType\":\"SERVICE_CUSTOMER_LIST\",\"decisionSource\":\"SYSTEM\",\"title\":\"客户订单\",\"layout\":\"TABS\",\"defaultView\":\"TABLE\",\"availableViews\":[\"TABLE\",\"TEXT\"],\"summary\":{\"dataPath\":\"items[0]\",\"fields\":[{\"field\":\"customerCode\",\"label\":\"客户编号\",\"format\":\"TEXT\"}]},\"table\":{\"dataPath\":\"items\",\"columns\":[{\"field\":\"customerCode\",\"label\":\"客户编号\",\"format\":\"TEXT\"},{\"field\":\"mealBalance.breakfast\",\"label\":\"早餐\",\"format\":\"NUMBER\"}],\"sections\":[{\"id\":\"balance\",\"title\":\"餐数\",\"dataPath\":\"items[].mealBalance\",\"columns\":[{\"field\":\"lunchDinner\",\"label\":\"午晚餐\",\"format\":\"NUMBER\"}]}]},\"chart\":{\"type\":\"BAR\",\"dataPath\":\"data.breakdown\",\"dimensionField\":\"packageName\",\"metricFields\":[\"customerCount\"],\"dimensionLabel\":\"套餐\",\"metricLabels\":[\"客户数\"]}}],\"warnings\":[\"结果已截断\"],\"toolTraceSummary\":[{\"toolName\":\"searchServiceCustomers\",\"status\":\"SUCCESS\"}]}";

        AgentChatResponse response = replay(snapshot);

        Map<String, Object> expectedSnapshot = JSON.parseObject(snapshot, Map.class);
        assertEquals(expectedSnapshot.get("presentations"), response.getPresentations());
        Map<String, Object> card = ((List<Map<String, Object>>) expectedSnapshot.get("cards")).get(0);
        Map<String, Object> cardData = (Map<String, Object>) card.get("data");
        Map<String, Object> firstItem = ((List<Map<String, Object>>) cardData.get("items")).get(0);
        assertEquals("历史掩码", firstItem.get("maskedName"));
        assertEquals("结果已截断", response.getWarnings().get(0));
        assertEquals("searchServiceCustomers", response.getToolTraceSummary().get(0).get("toolName"));
        verifyNoLiveQuery();
    }

    @Test
    void shouldRestoreCardsOnlySnapshotAndKeepHistoricalMaskedName() {
        String snapshot = "{\"cards\":[{\"type\":\"SERVICE_CUSTOMER_LIST\",\"data\":{\"maskedName\":\"旧姓名掩码\"}}],\"warnings\":[],\"toolTraceSummary\":[{\"status\":\"SUCCESS\"}]}";

        AgentChatResponse response = replay(snapshot);

        assertTrue(response.getPresentations().isEmpty());
        assertEquals("旧姓名掩码", ((Map<String, Object>) response.getCards().get(0).get("data")).get("maskedName"));
        verifyNoLiveQuery();
    }

    @Test
    void shouldIgnoreInvalidPresentationValueAndContinueRestoringOtherResults() {
        String snapshot = "{\"cards\":[{\"type\":\"SERVICE_CUSTOMER_LIST\"}],\"presentations\":[{\"schemaVersion\":\"v1\"},\"not-a-map\"],\"warnings\":[\"查询告警\"],\"toolTraceSummary\":[{\"toolName\":\"searchServiceCustomers\",\"status\":\"FAILED\"}]}";

        AgentChatResponse response = replay(snapshot);

        assertTrue(response.getPresentations().isEmpty());
        assertEquals("SERVICE_CUSTOMER_LIST", response.getCards().get(0).get("type"));
        assertEquals("查询告警", response.getWarnings().get(0));
        assertEquals("FAILED", response.getToolTraceSummary().get(0).get("status"));
        verifyNoLiveQuery();
    }

    /** 使用主系统真实幂等恢复入口读取固定业务快照。 */
    private AgentChatResponse replay(String businessResultJson) {
        AgentChatMessage userMessage = new AgentChatMessage();
        userMessage.setSessionId(session.getSessionId());
        userMessage.setRequestId("request-compatibility");
        userMessage.setClientMessageId("message-compatibility");
        userMessage.setRole("USER");

        AgentChatMessage assistantMessage = new AgentChatMessage();
        assistantMessage.setSessionId(session.getSessionId());
        assistantMessage.setRequestId("request-compatibility");
        assistantMessage.setRole("ASSISTANT");
        assistantMessage.setStatus("ANSWERED");
        assistantMessage.setContent("历史回答");
        assistantMessage.setBusinessResultJson(businessResultJson);

        when(sessionMapper.selectBySessionIdForUpdate(session.getSessionId())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(userMessage, assistantMessage);

        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(session.getSessionId());
        request.setClientMessageId(userMessage.getClientMessageId());
        request.setMessage("重复提交");
        return service.chat(request, "request-retry");
    }

    /** 校验幂等恢复未触发 Agent、审计或任何新的业务查询。 */
    private void verifyNoLiveQuery() {
        verify(diagnosisFacadeService, never()).chatMealPlan(any(), any(), any());
        verify(businessQueryAuditService, never()).record(any(), any(), anyLong());
        verify(messageMapper, never()).insert(any());
    }
}
