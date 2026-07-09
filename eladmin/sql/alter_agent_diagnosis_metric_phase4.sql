ALTER TABLE `agent_diagnosis_metric`
  ADD COLUMN `fallback_source` varchar(32) DEFAULT NULL COMMENT '兜底来源' AFTER `fallback_reason`,
  ADD COLUMN `failure_type` varchar(64) DEFAULT NULL COMMENT '失败类型' AFTER `fallback_source`;
