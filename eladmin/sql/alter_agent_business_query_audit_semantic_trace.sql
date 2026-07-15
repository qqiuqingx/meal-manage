ALTER TABLE `agent_business_query_audit`
  ADD COLUMN `semantic_fallback_reason` varchar(32) DEFAULT NULL COMMENT '语义分析降级原因' AFTER `analysis_confidence`,
  ADD COLUMN `semantic_catalog_version` varchar(32) DEFAULT NULL COMMENT '受控语义目录版本' AFTER `semantic_fallback_reason`,
  ADD COLUMN `temporal_expression` varchar(32) DEFAULT NULL COMMENT '受控相对时间表达' AFTER `semantic_catalog_version`,
  ADD COLUMN `resolved_record_date` varchar(10) DEFAULT NULL COMMENT '解析后的单日业务日期' AFTER `temporal_expression`,
  ADD COLUMN `resolved_start_date` varchar(10) DEFAULT NULL COMMENT '解析后的范围开始日期' AFTER `resolved_record_date`,
  ADD COLUMN `resolved_end_date` varchar(10) DEFAULT NULL COMMENT '解析后的范围结束日期' AFTER `resolved_start_date`,
  ADD COLUMN `pending_context_reused` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否复用待执行查询上下文' AFTER `resolved_end_date`;
