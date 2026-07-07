package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentRuleGap;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapResponse;
import me.zhengjie.modules.agent.domain.dto.AgentRuleGapStatusRequest;
import me.zhengjie.modules.agent.service.AgentRuleGapService;
import me.zhengjie.utils.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能排查规则缺口维护接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/rule-gaps")
public class AgentRuleGapController {

    private final AgentRuleGapService ruleGapService;

    /**
     * 分页查询诊断反馈沉淀出的规则缺口，供运营或规则维护人员处理。
     *
     * @param criteria 查询条件，支持按状态、缺口类型、真实原因和日期过滤
     * @return 规则缺口分页结果
     */
    @GetMapping
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<PageResult<AgentRuleGap>> query(AgentRuleGapQueryCriteria criteria) {
        return ResponseEntity.ok(ruleGapService.query(criteria));
    }

    /**
     * 更新规则缺口处理状态，可分配处理人并记录处理备注。
     *
     * @param id 规则缺口 ID
     * @param request 状态更新请求，包含目标状态、处理人和处理备注
     * @return 状态更新结果
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@el.check('agentDiagnosis:rule')")
    public ResponseEntity<AgentRuleGapResponse> updateStatus(@PathVariable Long id,
                                                             @Validated @RequestBody AgentRuleGapStatusRequest request) {
        return ResponseEntity.ok(ruleGapService.updateStatus(id, request));
    }
}
