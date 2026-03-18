-- 清理dish_ingredient_relation表中的重复数据
-- 生成时间: 2026-03-17

-- ============================================
-- 步骤1: 查看重复数据（可选，先检查一下）
-- 查看同一个菜品关联了多个相同名称的配料
-- ============================================
SELECT dir.dish_id, d.name as dish_name, di.name as ingredient_name, COUNT(*) as count
FROM dish_ingredient_relation dir
JOIN dish d ON dir.dish_id = d.id
JOIN dish_ingredient di ON dir.ingredient_id = di.id
GROUP BY dir.dish_id, di.name
HAVING COUNT(*) > 1
ORDER BY count DESC;

-- ============================================
-- 步骤2: 查看有多少关联的配料在dish_ingredient表中不存在
-- ============================================
SELECT COUNT(*) as orphan_count
FROM dish_ingredient_relation dir
LEFT JOIN dish_ingredient di ON dir.ingredient_id = di.id
WHERE di.id IS NULL;

-- ============================================
-- 步骤3: 删除dish_ingredient表中不存在的关联数据
-- ============================================
DELETE FROM dish_ingredient_relation
WHERE ingredient_id NOT IN (
    SELECT id FROM dish_ingredient
);

-- ============================================
-- 步骤4: 删除重复数据
-- 对于同一个菜品(dish_id)关联的相同配料名称，保留ID最小的记录
-- 优先删除ingredient_id在dish_ingredient表中不存在的记录
-- ============================================

-- 方法1: 使用窗口函数（MySQL 8.0+，推荐）
DELETE FROM dish_ingredient_relation
WHERE id NOT IN (
    SELECT id FROM (
        SELECT id
        FROM (
            SELECT dir.id, dir.dish_id, di.name as ingredient_name,
                   ROW_NUMBER() OVER (
                       PARTITION BY dir.dish_id, di.name
                       ORDER BY
                           CASE WHEN di.id IS NULL THEN 1 ELSE 0 END,  -- 优先删除不存在的配料
                           dir.id
                   ) as rn
            FROM dish_ingredient_relation dir
            LEFT JOIN dish_ingredient di ON dir.ingredient_id = di.id
        ) ranked
        WHERE rn = 1
    ) AS temp
);

-- 如果上面的SQL报错，使用下面这个方法（分两步执行）：
-- 第一步：先删除ingredient_id在dish_ingredient表中不存在的记录
-- DELETE FROM dish_ingredient_relation
-- WHERE ingredient_id NOT IN (
--     SELECT id FROM dish_ingredient
-- );

-- 第二步：删除同一菜品的相同配料名称的重复记录（保留ID最小的）
-- DELETE dir1 FROM dish_ingredient_relation dir1
-- INNER JOIN dish_ingredient_relation dir2
--     ON dir1.dish_id = dir2.dish_id
--     AND dir1.id > dir2.id
-- INNER JOIN dish_ingredient di1 ON dir1.ingredient_id = di1.id
-- INNER JOIN dish_ingredient di2 ON dir2.ingredient_id = di2.id
-- WHERE di1.name = di2.name;

-- ============================================
-- 步骤5: 验证清理结果
-- ============================================
SELECT COUNT(*) as total_count
FROM dish_ingredient_relation;

-- 查看是否还有重复（同一菜品的相同配料名称）
SELECT dir.dish_id, d.name as dish_name, di.name as ingredient_name, COUNT(*) as count
FROM dish_ingredient_relation dir
JOIN dish d ON dir.dish_id = d.id
JOIN dish_ingredient di ON dir.ingredient_id = di.id
GROUP BY dir.dish_id, di.name
HAVING COUNT(*) > 1;

-- ============================================
-- 步骤6: 添加复合唯一索引，防止以后再次出现重复
-- 注意：这个索引需要基于dish_id和配料名称，但MySQL不支持函数索引
-- 所以我们只能在应用层面控制，或者使用触发器
-- ============================================
-- 如果要防止同一菜品关联相同配料名称，需要在应用层面控制
-- 或者考虑在dish_ingredient_relation表中添加ingredient_name冗余字段并建立唯一索引

-- ============================================
-- 步骤7: 添加外键约束（可选，确保数据完整性）
-- ============================================
-- 确保dish_id存在于dish表
-- ALTER TABLE dish_ingredient_relation
-- ADD CONSTRAINT fk_dir_dish
-- FOREIGN KEY (dish_id) REFERENCES dish(id) ON DELETE CASCADE;

-- 确保ingredient_id存在于dish_ingredient表
-- ALTER TABLE dish_ingredient_relation
-- ADD CONSTRAINT fk_dir_ingredient
-- FOREIGN KEY (ingredient_id) REFERENCES dish_ingredient(id) ON DELETE CASCADE;
