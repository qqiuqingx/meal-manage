-- 智能客服 Agent 会话范围上下文迁移：支持跨重启恢复核销/退餐日期范围查询。
ALTER TABLE `agent_chat_session`
  ADD COLUMN `query_start_date` varchar(20) DEFAULT NULL COMMENT '当前会话受控查询起始日期' AFTER `record_date`,
  ADD COLUMN `query_end_date` varchar(20) DEFAULT NULL COMMENT '当前会话受控查询结束日期' AFTER `query_start_date`;
