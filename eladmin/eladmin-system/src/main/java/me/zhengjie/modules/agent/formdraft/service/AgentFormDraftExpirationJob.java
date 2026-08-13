package me.zhengjie.modules.agent.formdraft.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时过期 Agent 表单草稿并清除其中的敏感 payload。 */
@Component
@RequiredArgsConstructor
public class AgentFormDraftExpirationJob {
    private final AgentFormDraftService draftService;

    @Value("${agent.form-draft.expiration-batch-size:100}")
    private int batchSize;

    /** 按配置批次清理已到期草稿，单次最多处理 500 条。 */
    @Scheduled(cron = "${agent.form-draft.expiration-cron:0 0/30 * * * ?}")
    public void expire() {
        draftService.expireBatch(batchSize);
    }
}
