ALTER TABLE `agent_business_query_audit`
  ADD COLUMN `analysis_source` varchar(16) DEFAULT NULL COMMENT '分析来源' AFTER `failure_type`,
  ADD COLUMN `analysis_confidence` decimal(5,4) DEFAULT NULL COMMENT '分析置信度' AFTER `analysis_source`,
  ADD COLUMN `clarification_required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否要求澄清' AFTER `analysis_confidence`,
  ADD COLUMN `metric_codes` varchar(512) DEFAULT NULL COMMENT '指标代码JSON' AFTER `clarification_required`,
  ADD COLUMN `dimension_codes` varchar(512) DEFAULT NULL COMMENT '维度代码JSON' AFTER `metric_codes`,
  ADD COLUMN `unsupported_reason` varchar(64) DEFAULT NULL COMMENT '未支持原因' AFTER `dimension_codes`,
  ADD COLUMN `answer_validation_result` varchar(16) NOT NULL DEFAULT 'VALID' COMMENT '回答校验结果' AFTER `unsupported_reason`;
