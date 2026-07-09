package me.zhengjie.modules.agent.session.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionCreateRequest;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionDetailDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionQueryCriteria;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionSummaryDto;
import me.zhengjie.modules.agent.session.domain.dto.AgentChatSessionTitleUpdateRequest;
import me.zhengjie.modules.agent.session.service.AgentChatSessionService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能排查多会话管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/chat-sessions")
public class AgentChatSessionController {

    private final AgentChatSessionService chatSessionService;

    /**
     * 分页查询当前客服的会话列表。
     *
     * @param criteria 查询条件
     * @return 会话分页结果
     */
    @GetMapping
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<PageResult<AgentChatSessionSummaryDto>> query(AgentChatSessionQueryCriteria criteria) {
        return ResponseEntity.ok(chatSessionService.querySessions(criteria));
    }

    /**
     * 显式创建新会话，便于前端先占位再进入聊天。
     *
     * @param request 创建请求
     * @return 新会话摘要
     */
    @PostMapping
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentChatSessionSummaryDto> create(@RequestBody(required = false) AgentChatSessionCreateRequest request) {
        return ResponseEntity.ok(chatSessionService.createSession(request));
    }

    /**
     * 查询单个会话详情。
     *
     * @param sessionId 会话ID
     * @return 会话详情
     */
    @GetMapping("/{sessionId}")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentChatSessionDetailDto> get(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatSessionService.getSession(sessionId));
    }

    /**
     * 更新会话归档状态。
     *
     * @param sessionId 会话ID
     * @param archived 是否归档
     * @return 无内容响应
     */
    @PutMapping("/{sessionId}/archive")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<Void> archive(@PathVariable String sessionId, @RequestParam boolean archived) {
        chatSessionService.updateArchiveStatus(sessionId, archived);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新会话标题。
     *
     * @param sessionId 会话ID
     * @param request 标题更新请求
     * @return 无内容响应
     */
    @PutMapping("/{sessionId}/title")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<Void> updateTitle(@PathVariable String sessionId,
                                            @Validated @RequestBody AgentChatSessionTitleUpdateRequest request) {
        chatSessionService.updateTitle(sessionId, request.getTitle());
        return ResponseEntity.ok().build();
    }
}
