ALTER TABLE `agent_chat_session`
  ADD COLUMN `active_task_stack_json` longtext DEFAULT NULL COMMENT '受控会话任务栈，不保存原始工具响应' AFTER `last_business_query_context_json`;
