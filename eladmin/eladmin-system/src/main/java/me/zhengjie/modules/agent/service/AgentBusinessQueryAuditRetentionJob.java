package me.zhengjie.modules.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import me.zhengjie.modules.agent.domain.AgentBusinessQueryAudit;
import me.zhengjie.modules.agent.mapper.AgentBusinessQueryAuditMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 定期清理超过保留期限的 Agent 业务查询审计记录。 */
@Slf4j
@Component
public class AgentBusinessQueryAuditRetentionJob {
    private final AgentBusinessQueryAuditMapper auditMapper;
    private final int retentionDays;

    public AgentBusinessQueryAuditRetentionJob(AgentBusinessQueryAuditMapper auditMapper,
                                               @Value("${agent.audit.retention-days:180}") int retentionDays) {
        this.auditMapper = auditMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 清理过期的业务查询审计记录；保留天数小于 1 时禁用清理，避免错误配置导致全量删除。
     * 任务只按创建时间删除本审计表数据，不会影响聊天会话、消息、反馈或任何业务表。
     */
    @Scheduled(cron = "${agent.audit.retention-cron:0 15 3 * * ?}")
    public void cleanExpiredAudits() {
        if (retentionDays < 1) {
            log.warn("Agent 业务查询审计清理已禁用，retentionDays={}", retentionDays);
            return;
        }
        Timestamp cutoff = Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        int deleted = auditMapper.delete(new LambdaQueryWrapper<AgentBusinessQueryAudit>()
            .lt(AgentBusinessQueryAudit::getCreateTime, cutoff));
        if (deleted > 0) log.info("已清理 {} 条超过 {} 天的 Agent 业务查询审计记录", deleted, retentionDays);
    }
}
