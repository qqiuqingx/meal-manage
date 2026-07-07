package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentActionAudit;
import me.zhengjie.modules.agent.domain.dto.AgentActionAuditQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmRequest;
import me.zhengjie.modules.agent.domain.dto.AgentActionConfirmResponse;
import me.zhengjie.modules.agent.service.AgentActionConfirmService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能排查动作草稿人工确认接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/action-drafts")
public class AgentActionDraftController {

    private final AgentActionConfirmService actionConfirmService;

    /**
     * 按会话或请求查询动作确认审计记录，追踪动作建议的人工确认结果。
     */
    @GetMapping("/audits")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<PageResult<AgentActionAudit>> queryAudits(AgentActionAuditQueryCriteria criteria) {
        return ResponseEntity.ok(actionConfirmService.queryAudits(criteria));
    }

    /**
     * 人工确认动作草稿，后端负责权限、幂等、高风险二次确认和正式执行校验。
     *
     * @param request 动作草稿确认请求，包含幂等键、会话信息和待确认动作
     * @return 动作确认结果，包含审计 ID、状态、失败原因和执行结果
     */
    @PostMapping("/confirm")
    @PreAuthorize("@el.check('agentDiagnosis:confirm')")
    public ResponseEntity<AgentActionConfirmResponse> confirm(@Validated @RequestBody AgentActionConfirmRequest request) {
        return ResponseEntity.ok(actionConfirmService.confirm(request));
    }
}
