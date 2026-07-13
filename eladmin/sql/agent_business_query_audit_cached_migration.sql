ALTER TABLE `agent_business_query_audit`
  ADD COLUMN `cached` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否命中同轮缓存' AFTER `result_count`;
