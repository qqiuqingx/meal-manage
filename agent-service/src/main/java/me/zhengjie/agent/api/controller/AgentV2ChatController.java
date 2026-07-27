package me.zhengjie.agent.api.controller;

import jakarta.validation.Valid;
import me.zhengjie.agent.api.contract.AgentExecutionEnvelope;
import me.zhengjie.agent.api.contract.ChatMessageRequest;
import me.zhengjie.agent.api.error.AgentContractException;
import me.zhengjie.agent.chat.MealPlanChatService;
import me.zhengjie.agent.application.conversation.ConversationPatch;
import me.zhengjie.agent.domain.dto.AgentChatRequest;
import me.zhengjie.agent.domain.dto.AgentChatResponse;
import me.zhengjie.agent.security.AgentAccessContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import org.slf4j.MDC;

/** v2 通用聊天内部接口；旧 meal-plan 路径继续保留兼容。 */
@RestController
@RequestMapping("/api/agent/v2")
public class AgentV2ChatController {

    private final MealPlanChatService chatService;

    public AgentV2ChatController(MealPlanChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 接收可信执行信封并委托兼容聊天服务处理。
     *
     * @param requestId 请求链路标识
     * @param accessContext 主系统签发的短期访问上下文
     * @param envelope 主系统组装的可信上下文和客户端消息
     * @return 保持历史字段兼容且携带 contractVersion 的聊天结果
     */
    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                  @RequestHeader(value = "X-Agent-Access-Context", required = false) String accessContext,
                                  @Valid @RequestBody AgentExecutionEnvelope envelope) {
        if (!"v2".equals(envelope.getContractVersion())) {
            throw new AgentContractException("Unsupported contract version");
        }
        ChatMessageRequest message = envelope.getMessageRequest();
        if (message == null) {
            throw new IllegalArgumentException("messageRequest must not be null");
        }
        AgentChatRequest request = toLegacyRequest(envelope, message);
        AgentAccessContextHolder.bind(accessContext, request.getSessionId());
        AgentAccessContextHolder.bindAvailableTools(request.getAvailableTools());
        if (envelope.getSessionVersion() != null) MDC.put("sessionVersion", String.valueOf(envelope.getSessionVersion()));
        try {
            AgentChatResponse response = chatService.chat(request);
            response.setRequestId(resolveRequestId(requestId));
            response.setClientMessageId(message.getClientMessageId());
            response.setContractVersion(envelope.getContractVersion());
            response.setExpectedSessionVersion(envelope.getSessionVersion());
            response.setConversationPatch(new ConversationPatch(response.getSlots(), response.getConversationStage(),
                response.getPendingBusinessQueryContext(), response.getLastBusinessQueryContext(), response.getActiveTaskStack()));
            return response;
        } finally {
            AgentAccessContextHolder.clear();
            MDC.remove("sessionVersion");
        }
    }

    private AgentChatRequest toLegacyRequest(AgentExecutionEnvelope envelope, ChatMessageRequest message) {
        AgentChatRequest request = new AgentChatRequest();
        request.setSessionId(message.getSessionId());
        request.setClientMessageId(message.getClientMessageId());
        request.setMessage(message.getMessage());
        request.setContextSlots(envelope.getContextSnapshot());
        request.setAvailableTools(envelope.getAvailableTools());
        request.setSessionVersion(envelope.getSessionVersion());
        request.setPendingBusinessQueryContext(envelope.getPendingBusinessQueryContext());
        request.setLastBusinessQueryContext(envelope.getLastBusinessQueryContext());
        request.setActiveTaskStack(envelope.getActiveTaskStack());
        return request;
    }

    private String resolveRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
    }
}
