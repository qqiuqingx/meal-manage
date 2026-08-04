package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.AgentChatRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatMessageRequest;
import me.zhengjie.modules.agent.domain.dto.AgentChatResponse;
import me.zhengjie.modules.agent.service.AgentDiagnosisFacadeService;
import me.zhengjie.modules.agent.session.service.AgentChatSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台智能排查接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/meal-plan")
public class AgentDiagnosisController {

    private final AgentDiagnosisFacadeService diagnosisFacadeService;
    private final AgentChatSessionService chatSessionService;

    @PostMapping("/chat")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentChatResponse> chatMealPlan(@RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                          @Validated @RequestBody AgentChatMessageRequest request) {
        AgentChatRequest command = new AgentChatRequest();
        command.setSessionId(request.getSessionId());
        command.setClientMessageId(request.getClientMessageId());
        command.setMessage(request.getMessage());
        return ResponseEntity.ok(chatSessionService.chat(command, requestId));
    }
}
