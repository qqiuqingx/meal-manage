package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentBusinessQueryAudit;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentBusinessQueryAuditStatsDto;
import me.zhengjie.modules.agent.service.AgentBusinessQueryAuditService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 业务只读查询审计接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/business-query-audits")
public class AgentBusinessQueryAuditController {

    private final AgentBusinessQueryAuditService auditService;

    /**
     * 分页查询业务只读查询审计记录。
     *
     * @param criteria 查询条件，支持按客服、会话、请求、领域、客户、订单和时间范围过滤
     * @return 业务查询审计分页结果
     */
    @GetMapping
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<PageResult<AgentBusinessQueryAudit>> query(AgentBusinessQueryAuditCriteria criteria) {
        return ResponseEntity.ok(auditService.query(criteria));
    }

    /**
     * 统计业务只读查询次数、失败、部分成功、缓存命中和工具分布。
     *
     * @param criteria 统计过滤条件，复用审计查询条件
     * @return 业务查询审计统计结果
     */
    @GetMapping("/stats")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentBusinessQueryAuditStatsDto> stats(AgentBusinessQueryAuditCriteria criteria) {
        return ResponseEntity.ok(auditService.stats(criteria));
    }
}
