package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.AgentDiagnosisFeedback;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackQueryCriteria;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackRequest;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackResponse;
import me.zhengjie.modules.agent.domain.dto.AgentDiagnosisFeedbackStatsDto;
import me.zhengjie.modules.agent.service.AgentDiagnosisFeedbackService;
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
 * 智能排查诊断结果客服反馈接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/feedback")
public class AgentDiagnosisFeedbackController {

    private final AgentDiagnosisFeedbackService feedbackService;

    /**
     * 提交客服对诊断结果的反馈，并在必要时沉淀规则缺口。
     *
     * @param request 反馈请求，包含诊断请求、预测原因、反馈结论和真实原因
     * @return 反馈记录结果，包含反馈 ID 和处理状态
     */
    @PostMapping
    @PreAuthorize("@el.check('agentDiagnosis:feedback')")
    public ResponseEntity<AgentDiagnosisFeedbackResponse> submit(@Validated @RequestBody AgentDiagnosisFeedbackRequest request) {
        return ResponseEntity.ok(feedbackService.submit(request));
    }

    /**
     * 分页查询诊断反馈记录，用于客服反馈复盘和运营分析。
     *
     * @param criteria 查询条件，支持按请求、会话、反馈结论、日期和餐次过滤
     * @return 诊断反馈分页结果
     */
    @GetMapping
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<PageResult<AgentDiagnosisFeedback>> query(AgentDiagnosisFeedbackQueryCriteria criteria) {
        return ResponseEntity.ok(feedbackService.query(criteria));
    }

    /**
     * 统计诊断反馈采纳率和真实原因分布。
     *
     * @param criteria 统计条件，复用反馈查询过滤项
     * @return 反馈统计结果
     */
    @GetMapping("/stats")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentDiagnosisFeedbackStatsDto> stats(AgentDiagnosisFeedbackQueryCriteria criteria) {
        return ResponseEntity.ok(feedbackService.stats(criteria));
    }
}
