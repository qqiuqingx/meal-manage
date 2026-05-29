-- 排餐单手工换菜关系表
CREATE TABLE `meal_plan_manual_replace` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `meal_plan_id` BIGINT NOT NULL COMMENT '排餐计划ID',
  `customer_plan_id` BIGINT NOT NULL COMMENT '客户排餐记录ID',
  `customer_id` BIGINT NOT NULL COMMENT '客户ID',
  `customer_code` VARCHAR(64) DEFAULT '' COMMENT '客户编号快照',
  `customer_name` VARCHAR(64) DEFAULT '' COMMENT '客户姓名快照',
  `dish_id` INT NOT NULL COMMENT '换菜菜品ID',
  `dish_name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '换菜菜名快照',
  `dish_type` VARCHAR(32) NOT NULL COMMENT '菜品类目：MAIN/SIDE/VEGETABLE/SOUP/RICE',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  `create_by` VARCHAR(64) DEFAULT '',
  `update_by` VARCHAR(64) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_customer_dish_deleted` (`meal_plan_id`, `customer_plan_id`, `dish_id`, `deleted`),
  KEY `idx_meal_plan_id` (`meal_plan_id`),
  KEY `idx_plan_dish_type` (`meal_plan_id`, `dish_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排餐单手工换菜关系表';
