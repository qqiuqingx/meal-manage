package me.zhengjie.agent.api.controller;

import jakarta.validation.Valid;
import me.zhengjie.agent.api.contract.AgentExecutionEnvelope;
import me.zhengjie.agent.api.contract.ChatMessageRequest;
import me.zhengjie.agent.api.error.AgentContractException;
import me.zhengjie.agent.application.BusinessAgentRunner;
import me.zhengjie.agent.application.conversation.ConversationPatch;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import org.slf4j.MDC;

/** v2 通用聊天内部接口；所有普通查询和排餐诊断统一进入 LLM Tool Calling Runner。 */
@RestController
@RequestMapping("/api/agent/v2")
public class AgentV2ChatController {

    private final BusinessAgentRunner businessAgentRunner;

    /** 生产 v2 入口使用统一 LLM Tool Calling Runner。 */
    @Autowired
    public AgentV2ChatController(BusinessAgentRunner businessAgentRunner) {
        this.businessAgentRunner = businessAgentRunner;
    }

    /**
     * 接收可信执行信封并委托统一业务 Agent 执行。
     *
     * @param requestId 请求链路标识
     * @param accessContext 主系统签发的短期访问上下文
     * @param envelope 主系统组装的可信上下文和客户端消息
     * @return 保持前端展示字段并携带 contractVersion 的聊天结果
     */
    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                  @RequestHeader("X-Agent-Access-Context") String accessContext,
                                  @Valid @RequestBody AgentExecutionEnvelope envelope) {
        if (!"v2".equals(envelope.getContractVersion())) {
            throw new AgentContractException("Unsupported contract version");
        }
        ChatMessageRequest message = envelope.getMessageRequest();
        if (message == null) {
            throw new IllegalArgumentException("messageRequest must not be null");
        }
        AgentChatRequest request = toChatRequest(envelope, message);
        AgentAccessContextHolder.bind(accessContext, request.getSessionId(), request.getClientMessageId());
        AgentAccessContextHolder.bindAvailableTools(request.getAvailableTools());
        if (envelope.getSessionVersion() != null) MDC.put("sessionVersion", String.valueOf(envelope.getSessionVersion()));
        try {
            AgentChatResponse response = businessAgentRunner.run(request);
            response.setRequestId(resolveRequestId(requestId));
            response.setClientMessageId(message.getClientMessageId());
            response.setContractVersion(envelope.getContractVersion());
            response.setExpectedSessionVersion(envelope.getSessionVersion());
            response.setConversationPatch(new ConversationPatch(response.getSlots(), response.getConversationStage(),
                response.getLastBusinessQueryContext()));
            return response;
        } finally {
            AgentAccessContextHolder.clear();
            MDC.remove("sessionVersion");
        }
    }

    /** 将 v2 可信执行信封转换为应用层聊天请求。 */
    private AgentChatRequest toChatRequest(AgentExecutionEnvelope envelope,
                                           ChatMessageRequest message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(message.getSessionId());
        request.setClientMessageId(message.getClientMessageId());
        request.setMessage(message.getMessage());
        request.setContextSlots(envelope.getContextSnapshot());
        request.setAvailableTools(envelope.getAvailableTools());
        request.setSessionVersion(envelope.getSessionVersion());
        request.setLastBusinessQueryContext(envelope.getLastBusinessQueryContext());
        request.setFormDraftContext(envelope.getFormDraftContext());
        return request;
    }

    private String resolveRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
    }
}
