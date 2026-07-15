ALTER TABLE `agent_chat_session`
  ADD COLUMN `pending_business_query_json` longtext DEFAULT NULL COMMENT '待补充条件的受控业务查询上下文' AFTER `last_summary`,
  ADD COLUMN `last_business_query_context_json` longtext DEFAULT NULL COMMENT '最近一次已执行业务查询的脱敏摘要' AFTER `pending_business_query_json`;
