-- 清理dish_ingredient表中的重复数据
-- 生成时间: 2026-03-17

-- ============================================
-- 步骤1: 查看重复数据（可选，先检查一下）
-- ============================================
SELECT name, COUNT(*) as count
FROM dish_ingredient
GROUP BY name
HAVING COUNT(*) > 1
ORDER BY count DESC;

-- ============================================
-- 步骤2: 删除重复数据
-- 优先保留有category值的记录，如果都有值则保留ID最小的
-- ============================================

-- 方法1: 使用窗口函数（MySQL 8.0+）
DELETE FROM dish_ingredient
WHERE id NOT IN (
    SELECT id FROM (
        SELECT id
        FROM (
            SELECT id, name, category,
                   ROW_NUMBER() OVER (
                       PARTITION BY name
                       ORDER BY
                           CASE WHEN category IS NOT NULL AND category != '' THEN 0 ELSE 1 END,
                           id
                   ) as rn
            FROM dish_ingredient
        ) ranked
        WHERE rn = 1
    ) AS temp
);

-- 如果上面的SQL报错，使用下面这个方法（MySQL 5.7兼容）：
-- DELETE di1 FROM dish_ingredient di1
-- INNER JOIN dish_ingredient di2 ON di1.name = di2.name
-- WHERE di1.id > di2.id
--    OR (di1.id != di2.id
--        AND (di1.category IS NULL OR di1.category = '')
--        AND (di2.category IS NOT NULL AND di2.category != ''));

-- ============================================
-- 步骤3: 验证清理结果
-- ============================================
SELECT COUNT(*) as total_count,
       COUNT(DISTINCT name) as unique_count
FROM dish_ingredient;

-- ============================================
-- 步骤4: 添加唯一索引，防止以后再次出现重复
-- ============================================
ALTER TABLE dish_ingredient
ADD UNIQUE INDEX uk_name (name);
