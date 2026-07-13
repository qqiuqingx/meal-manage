ALTER TABLE `agent_chat_session`
  ADD COLUMN `meal_plan_record_id` bigint DEFAULT NULL COMMENT '当前会话排餐客户记录ID' AFTER `order_code`;
