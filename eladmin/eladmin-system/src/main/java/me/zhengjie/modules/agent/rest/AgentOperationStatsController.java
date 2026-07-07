package me.zhengjie.modules.agent.rest;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsDto;
import me.zhengjie.modules.agent.domain.dto.AgentOperationStatsQuery;
import me.zhengjie.modules.agent.service.AgentOperationStatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 智能排查运营看板接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/operation")
public class AgentOperationStatsController {

    private final AgentOperationStatsService operationStatsService;

    /**
     * 查询智能排查运营指标，汇总诊断、反馈、动作确认和规则缺口数据。
     *
     * @param query 运营统计查询条件，支持按日期、餐次和会话过滤
     * @return 运营看板统计结果
     */
    @GetMapping("/stats")
    @PreAuthorize("@el.check('agentDiagnosis:list')")
    public ResponseEntity<AgentOperationStatsDto> stats(AgentOperationStatsQuery query) {
        return ResponseEntity.ok(operationStatsService.stats(query));
    }
}
