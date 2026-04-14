-- 客户档案排除菜品功能数据库迁移脚本
-- Created: 2026-04-14
-- Description: 为客户档案表添加排除菜品ID列表字段

-- 检查并添加 excluded_dish_ids 字段到 customer_profile 表
-- 使用 IF NOT EXISTS 避免重复执行错误
ALTER TABLE customer_profile
ADD COLUMN IF NOT EXISTS excluded_dish_ids JSON NULL
COMMENT '排除菜品ID列表(JSON数组)';

-- 为新字段添加索引，提高查询性能
-- 使用函数索引支持 JSON_CONTAINS 查询
CREATE INDEX idx_customer_profile_excluded_dishes ON customer_profile (
    (JSON_CONTAINS(excluded_dish_ids, '1'))  -- 示例索引，实际查询时使用具体的菜品ID
);

-- 添加注释说明字段用途
-- 排除菜品功能允许客户指定不想在餐单中出现的菜品
-- 此字段存储菜品ID的JSON数组，如: [1, 5, 12] 表示排除ID为1,5,12的菜品
-- 在排餐时会过滤掉这些菜品，确保客户不会收到不想要的菜品

-- 验证字段添加成功
SELECT column_name, data_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_name = 'customer_profile'
AND column_name = 'excluded_dish_ids';

-- 重置会话中的表缓存，确保新字段生效
-- 注意：这个语句在某些MySQL版本中可能需要管理员权限
-- RESET QUERY CACHE;