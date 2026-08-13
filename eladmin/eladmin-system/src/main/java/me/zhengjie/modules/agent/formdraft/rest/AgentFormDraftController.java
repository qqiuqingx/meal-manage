package me.zhengjie.modules.agent.formdraft.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftClaimResult;
import me.zhengjie.modules.agent.formdraft.domain.dto.AgentFormDraftSummary;
import me.zhengjie.modules.agent.formdraft.service.AgentFormDraftService;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeContext;
import me.zhengjie.modules.agent.security.AgentCustomerDataScopeResolver;
import me.zhengjie.utils.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录客服领取和恢复本人 Agent 表单草稿。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/form-drafts")
public class AgentFormDraftController {
    private final AgentFormDraftService draftService;
    private final AgentCustomerDataScopeResolver dataScopeResolver;

    /** 领取 READY 或 CLAIMED 草稿的完整类型化 payload。 */
    @PostMapping("/{draftId}/claim")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentFormDraftClaimResult> claim(@PathVariable String draftId) {
        try {
            AgentCustomerDataScopeContext.bind(dataScopeResolver.resolveCurrent());
            return ResponseEntity.ok(draftService.claim(draftId, SecurityUtils.getCurrentUserId()));
        } finally {
            AgentCustomerDataScopeContext.clear();
        }
    }

    /** 查询本人草稿的不含敏感信息摘要。 */
    @GetMapping("/{draftId}/summary")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentFormDraftSummary> summary(@PathVariable String draftId) {
        return ResponseEntity.ok(draftService.summary(draftId, SecurityUtils.getCurrentUserId()));
    }
}
