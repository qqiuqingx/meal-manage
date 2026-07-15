ALTER TABLE `agent_business_query_audit`
  ADD COLUMN `understanding_schema_version` varchar(32) DEFAULT NULL COMMENT '会话理解协议版本',
  ADD COLUMN `interaction_mode` varchar(32) DEFAULT NULL COMMENT '交互模式',
  ADD COLUMN `semantic_frame_count` int NOT NULL DEFAULT 0 COMMENT '语义帧数量',
  ADD COLUMN `capability_ids` varchar(512) DEFAULT NULL COMMENT '命中能力目录标识',
  ADD COLUMN `reference_resolution` varchar(32) DEFAULT NULL COMMENT '上下文引用解析状态',
  ADD COLUMN `confidence_bucket` varchar(16) DEFAULT NULL COMMENT '校准后置信度档位',
  ADD COLUMN `clarification_code` varchar(64) DEFAULT NULL COMMENT '受控澄清码';
