-- 订单换菜规则表
CREATE TABLE IF NOT EXISTS `customer_order_replace_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `source_dish_id` BIGINT NOT NULL COMMENT '原菜品ID',
  `source_dish_name` VARCHAR(100) NOT NULL COMMENT '原菜品名称(快照)',
  `source_dish_type` VARCHAR(32) DEFAULT NULL COMMENT '原菜品类型(快照)',
  `target_dish_id` BIGINT NOT NULL COMMENT '目标菜品ID',
  `target_dish_name` VARCHAR(100) NOT NULL COMMENT '目标菜品名称(快照)',
  `target_dish_type` VARCHAR(32) DEFAULT NULL COMMENT '目标菜品类型(快照)',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  `create_by` VARCHAR(100) DEFAULT NULL,
  `update_by` VARCHAR(100) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_source_active` (`order_id`, `source_dish_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单换菜规则表';

-- 排餐明细表新增原菜品类型字段
ALTER TABLE `meal_plan_customer_item` ADD COLUMN `original_dish_type` VARCHAR(32) DEFAULT NULL COMMENT '原菜品类型(被替换前的类型)' AFTER `original_dish_name`;
