package me.zhengjie.modules.agent.session.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.domain.dto.DiagnosisSlots;
import me.zhengjie.modules.agent.security.AgentAccessContextService;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.session.domain.AgentChatMessage;
import me.zhengjie.modules.agent.session.domain.AgentChatSession;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionCreateRequest;
import me.zhengjie.modules.agent.session.mapper.AgentChatMessageMapper;
import me.zhengjie.modules.agent.session.mapper.AgentChatSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 使用真实 MySQL 验证同一会话的并发消息按行锁顺序合并槽位。
 *
 * <p>该测试只在显式设置 {@code AGENT_DB_CONCURRENCY_TEST=true} 时执行，
 * 避免普通单元测试意外连接或修改开发数据库。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AGENT_DB_CONCURRENCY_TEST", matches = "true")
class AgentChatSessionConcurrencyIntegrationTest {

    @MockBean(name = "serverEndpointExporter")
    private ServerEndpointExporter serverEndpointExporter;

    @MockBean
    private AgentDiagnosisFacadeService diagnosisFacadeService;

    @MockBean
    private AgentAccessContextService accessContextService;

    @MockBean
    private AgentBusinessQueryAuditService businessQueryAuditService;

    @Autowired
    private AgentChatSessionServiceImpl service;

    @Autowired
    private AgentChatSessionMapper sessionMapper;

    @Autowired
    private AgentChatMessageMapper messageMapper;

    private String sessionId;

    /**
     * 为当前测试创建独立空会话，并配置不访问外部 Agent 服务的受控响应。
     */
    @BeforeEach
    void setUp() {
        AgentChatSessionCreateRequest createRequest = new AgentChatSessionCreateRequest();
        createRequest.setTitle("Agent 同会话并发集成测试");
        sessionId = service.createSession(createRequest).getSessionId();
        when(accessContextService.issue(any(), any())).thenReturn("test-access-context");
    }

    /**
     * 仅删除当前测试会话产生的消息和会话记录，不影响其他测试或业务数据。
     */
    @AfterEach
    void cleanUp() {
        if (sessionId == null) {
            return;
        }
        messageMapper.delete(new LambdaQueryWrapper<AgentChatMessage>()
            .eq(AgentChatMessage::getSessionId, sessionId));
        sessionMapper.delete(new LambdaQueryWrapper<AgentChatSession>()
            .eq(AgentChatSession::getSessionId, sessionId));
    }

    /**
     * 同时发送客户和日期两条消息，验证两个事务观察到连续版本且最终槽位均被保留。
     *
     * @throws Exception 并发任务启动、等待或执行失败
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shouldMergeDifferentSlotsWhenTwoMessagesUpdateSameSessionConcurrently() throws Exception {
        List<Long> observedVersions = Collections.synchronizedList(new ArrayList<Long>());
        when(diagnosisFacadeService.chatMealPlan(any(AgentChatRequest.class), any(), any()))
            .thenAnswer(invocation -> buildResponse(invocation.getArgument(0), observedVersions));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AgentChatResponse> customerFuture = executor.submit(
                () -> chatAfterStart(ready, start, "msg-customer", "客户是 C90001"));
            Future<AgentChatResponse> dateFuture = executor.submit(
                () -> chatAfterStart(ready, start, "msg-date", "查询日期是 2026-07-29"));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发任务未在 5 秒内就绪");
            start.countDown();
            assertEquals("ANSWERED", customerFuture.get(20, TimeUnit.SECONDS).getStatus());
            assertEquals("ANSWERED", dateFuture.get(20, TimeUnit.SECONDS).getStatus());
        } finally {
            executor.shutdownNow();
        }

        AgentChatSession persisted = sessionMapper.selectOne(new LambdaQueryWrapper<AgentChatSession>()
            .eq(AgentChatSession::getSessionId, sessionId));
        assertNotNull(persisted);
        assertEquals("C90001", persisted.getCustomerCode());
        assertEquals("2026-07-29", persisted.getRecordDate());
        assertEquals(Integer.valueOf(2), persisted.getVersion());
        assertEquals(Long.valueOf(4L), messageMapper.selectCount(
            new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getSessionId, sessionId)));

        List<Long> sortedVersions = new ArrayList<Long>(observedVersions);
        Collections.sort(sortedVersions);
        assertEquals(java.util.Arrays.asList(0L, 1L), sortedVersions);
    }

    /**
     * 等所有任务就绪后发送一条带唯一幂等 ID 的会话消息。
     *
     * @param ready 任务就绪闩锁
     * @param start 同步启动闩锁
     * @param clientMessageId 前端幂等消息 ID
     * @param message 消息正文
     * @return 聊天响应
     * @throws Exception 等待中断或聊天执行失败
     */
    private AgentChatResponse chatAfterStart(CountDownLatch ready,
                                             CountDownLatch start,
                                             String clientMessageId,
                                             String message) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "并发启动信号未在 5 秒内到达");
        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(sessionId);
        request.setClientMessageId(clientMessageId);
        request.setMessage(message);
        return service.chat(request, "req-" + clientMessageId);
    }

    /**
     * 按消息内容构造只包含本轮新增槽位的下游响应，模拟真实 Agent 增量回传。
     *
     * @param request 主系统传给 Agent 的请求快照
     * @param observedVersions 并发请求实际观察到的会话版本
     * @return 受控 Agent 响应
     */
    private AgentChatResponse buildResponse(AgentChatRequest request, List<Long> observedVersions) {
        observedVersions.add(request.getSessionVersion());
        DiagnosisSlots slots = new DiagnosisSlots();
        if (request.getMessage().contains("C90001")) {
            slots.setCustomerCode("C90001");
        }
        if (request.getMessage().contains("2026-07-29")) {
            slots.setRecordDate("2026-07-29");
        }

        AgentChatResponse response = new AgentChatResponse();
        response.setSessionId(request.getSessionId());
        response.setExpectedSessionVersion(request.getSessionVersion());
        response.setStatus("ANSWERED");
        response.setConversationStage("READY");
        response.setAssistantMessage("测试响应");
        response.setSlots(slots);
        return response;
    }
}
