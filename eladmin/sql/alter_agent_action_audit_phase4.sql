ALTER TABLE `agent_action_audit`
  ADD COLUMN `draft_digest` varchar(64) DEFAULT NULL COMMENT '动作草稿摘要' AFTER `idempotency_key`,
  ADD COLUMN `stale_check_result` varchar(32) DEFAULT NULL COMMENT '草稿过期校验结果' AFTER `failure_reason`,
  ADD COLUMN `stale_check_detail` varchar(500) DEFAULT NULL COMMENT '草稿过期校验详情' AFTER `stale_check_result`;
