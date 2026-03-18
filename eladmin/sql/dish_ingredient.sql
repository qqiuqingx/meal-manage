-- 配料表
CREATE TABLE `dish_ingredient` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '配料名称',
  `category` varchar(50) DEFAULT NULL COMMENT '分类：MEAT肉类、VEGETABLE蔬菜、SEAFOOD海鲜、TOFU豆制品、SPICE调料、OTHER其他',
  `unit` varchar(20) DEFAULT NULL COMMENT '单位：克g、毫升ml、个',
  `calories` int DEFAULT NULL COMMENT '每单位热量（卡路里）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `enabled` bit(1) DEFAULT b'1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配料表';

-- 菜品配料关联表
CREATE TABLE `dish_ingredient_relation` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dish_id` int NOT NULL COMMENT '菜品ID',
  `ingredient_id` int NOT NULL COMMENT '配料ID',
  `quantity` decimal(10,2) DEFAULT NULL COMMENT '用量',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_dish_id` (`dish_id`),
  KEY `idx_ingredient_id` (`ingredient_id`),
  KEY `idx_dish_ingredient` (`dish_id`, `ingredient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品配料关联表';

-- 可选：将原 dish 表的 ingredients 字段数据迁移到新表
-- 建议：先保留原字段，待新功能测试完成后，再考虑是否删除
