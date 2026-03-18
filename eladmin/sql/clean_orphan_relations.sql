-- 清理dish_ingredient_relation表中的孤立数据
-- 生成时间: 2026-03-17

-- ============================================
-- 步骤1: 删除配料不存在的关联记录（418条）
-- ============================================
DELETE FROM dish_ingredient_relation
WHERE ingredient_id NOT IN (
    SELECT id FROM dish_ingredient
);

-- ============================================
-- 步骤2: 删除菜品不存在的关联记录（4条）
-- ============================================
DELETE FROM dish_ingredient_relation
WHERE dish_id NOT IN (
    SELECT id FROM dish
);

-- ============================================
-- 步骤3: 验证清理结果
-- ============================================
-- 检查是否还有孤立数据
SELECT
    (SELECT COUNT(*) FROM dish_ingredient_relation WHERE ingredient_id NOT IN (SELECT id FROM dish_ingredient)) as orphan_ingredient_count,
    (SELECT COUNT(*) FROM dish_ingredient_relation WHERE dish_id NOT IN (SELECT id FROM dish)) as orphan_dish_count,
    COUNT(*) as total_count
FROM dish_ingredient_relation;

-- ============================================
-- 步骤4: 添加外键约束（可选，防止以后再出现孤立数据）
-- ============================================
-- 确保dish_id存在于dish表
ALTER TABLE dish_ingredient_relation
ADD CONSTRAINT fk_dir_dish
FOREIGN KEY (dish_id) REFERENCES dish(id) ON DELETE CASCADE;

-- 确保ingredient_id存在于dish_ingredient表
ALTER TABLE dish_ingredient_relation
ADD CONSTRAINT fk_dir_ingredient
FOREIGN KEY (ingredient_id) REFERENCES dish_ingredient(id) ON DELETE CASCADE;
