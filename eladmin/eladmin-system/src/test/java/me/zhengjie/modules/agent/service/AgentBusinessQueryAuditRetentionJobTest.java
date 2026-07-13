package me.zhengjie.modules.agent.service;

import me.zhengjie.modules.agent.mapper.AgentBusinessQueryAuditMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 审计清理任务只能删除过期审计记录，并支持显式禁用。 */
class AgentBusinessQueryAuditRetentionJobTest {

    @Test
    void shouldDeleteExpiredAuditsWhenRetentionIsConfigured() {
        AgentBusinessQueryAuditMapper mapper = mock(AgentBusinessQueryAuditMapper.class);
        when(mapper.delete(any())).thenReturn(3);
        AgentBusinessQueryAuditRetentionJob job = new AgentBusinessQueryAuditRetentionJob(mapper, 180);

        job.cleanExpiredAudits();

        verify(mapper).delete(any());
    }

    @Test
    void shouldNotDeleteAnythingWhenRetentionIsDisabled() {
        AgentBusinessQueryAuditMapper mapper = mock(AgentBusinessQueryAuditMapper.class);
        AgentBusinessQueryAuditRetentionJob job = new AgentBusinessQueryAuditRetentionJob(mapper, 0);

        job.cleanExpiredAudits();

        verify(mapper, never()).delete(any());
    }
}
