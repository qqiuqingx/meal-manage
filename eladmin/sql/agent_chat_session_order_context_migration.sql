ALTER TABLE `agent_chat_session`
  ADD COLUMN `order_id` bigint DEFAULT NULL COMMENT '当前会话订单ID' AFTER `customer_code`,
  ADD COLUMN `order_code` varchar(64) DEFAULT NULL COMMENT '当前会话订单编号' AFTER `order_id`;
