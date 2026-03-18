-- 检查dish_ingredient_relation表的重复情况
-- 生成时间: 2026-03-17

-- ============================================
-- 检查1: 查看表的总体情况
-- ============================================
SELECT
    COUNT(*) as total_records,
    COUNT(DISTINCT id) as unique_ids,
    COUNT(DISTINCT dish_id) as unique_dishes,
    COUNT(DISTINCT ingredient_id) as unique_ingredients,
    COUNT(DISTINCT CONCAT(dish_id, '-', ingredient_id)) as unique_combinations
FROM dish_ingredient_relation;

-- ============================================
-- 检查2: 查看是否有完全相同的记录（所有字段都相同）
-- ============================================
SELECT dish_id, ingredient_id, quantity, remark, COUNT(*) as count
FROM dish_ingredient_relation
GROUP BY dish_id, ingredient_id, quantity, remark
HAVING COUNT(*) > 1;

-- ============================================
-- 检查3: 查看是否有相同的dish_id和ingredient_id组合
-- ============================================
SELECT dish_id, ingredient_id, COUNT(*) as count
FROM dish_ingredient_relation
GROUP BY dish_id, ingredient_id
HAVING COUNT(*) > 1;

-- ============================================
-- 检查4: 查看具体的重复记录详情
-- ============================================
SELECT dir.*, d.name as dish_name, di.name as ingredient_name
FROM dish_ingredient_relation dir
LEFT JOIN dish d ON dir.dish_id = d.id
LEFT JOIN dish_ingredient di ON dir.ingredient_id = di.id
WHERE (dish_id, ingredient_id) IN (
    SELECT dish_id, ingredient_id
    FROM dish_ingredient_relation
    GROUP BY dish_id, ingredient_id
    HAVING COUNT(*) > 1
)
ORDER BY dish_id, ingredient_id, dir.id;

-- ============================================
-- 检查5: 查看是否有孤立数据（关联的菜品或配料不存在）
-- ============================================
-- 配料不存在
SELECT COUNT(*) as orphan_ingredient_count
FROM dish_ingredient_relation dir
LEFT JOIN dish_ingredient di ON dir.ingredient_id = di.id
WHERE di.id IS NULL;

-- 菜品不存在
SELECT COUNT(*) as orphan_dish_count
FROM dish_ingredient_relation dir
LEFT JOIN dish d ON dir.dish_id = d.id
WHERE d.id IS NULL;

-- ============================================
-- 检查6: 查看孤立数据的详细信息
-- ============================================
SELECT dir.*, '配料不存在' as issue_type
FROM dish_ingredient_relation dir
LEFT JOIN dish_ingredient di ON dir.ingredient_id = di.id
WHERE di.id IS NULL

UNION ALL

SELECT dir.*, '菜品不存在' as issue_type
FROM dish_ingredient_relation dir
LEFT JOIN dish d ON dir.dish_id = d.id
WHERE d.id IS NULL;
