-- =============================================
-- 配料三层分类优化 - 数据库变更脚本
-- 执行顺序：按文件从上到下依次执行
-- =============================================

-- 1. 创建配料分类表
CREATE TABLE IF NOT EXISTS dish_ingredient_category (
  id INT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
  parent_id INT NULL COMMENT '父分类ID，一级分类为空',
  name VARCHAR(64) NOT NULL COMMENT '分类名称',
  level TINYINT NOT NULL COMMENT '层级：1一级分类，2二级分类',
  sort INT DEFAULT 0 COMMENT '排序',
  enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  create_time DATETIME NULL COMMENT '创建时间',
  update_time DATETIME NULL COMMENT '更新时间',
  UNIQUE KEY uk_parent_name (parent_id, name),
  KEY idx_parent_id (parent_id),
  KEY idx_level (level),
  KEY idx_enabled (enabled)
) COMMENT='配料分类表';

-- 2. 给 dish_ingredient 增加 category_id 字段
ALTER TABLE dish_ingredient
  ADD COLUMN category_id INT NULL COMMENT '二级分类ID' AFTER name;

ALTER TABLE dish_ingredient
  ADD INDEX idx_category_id (category_id);

-- 3. 初始化一级分类
INSERT INTO dish_ingredient_category (parent_id, name, level, sort, enabled) VALUES
(NULL, '蔬菜', 1, 10, 1),
(NULL, '肉类', 1, 20, 1),
(NULL, '水产', 1, 30, 1),
(NULL, '豆制品', 1, 40, 1),
(NULL, '调料', 1, 50, 1),
(NULL, '主食', 1, 60, 1),
(NULL, '其他', 1, 90, 1);

-- 4. 初始化二级分类（parent_id 需根据实际一级分类ID调整）
-- 先获取一级分类ID，假设自增从1开始
SET @veg = (SELECT id FROM dish_ingredient_category WHERE name = '蔬菜' AND level = 1 LIMIT 1);
SET @meat = (SELECT id FROM dish_ingredient_category WHERE name = '肉类' AND level = 1 LIMIT 1);
SET @seafood = (SELECT id FROM dish_ingredient_category WHERE name = '水产' AND level = 1 LIMIT 1);
SET @tofu = (SELECT id FROM dish_ingredient_category WHERE name = '豆制品' AND level = 1 LIMIT 1);
SET @spice = (SELECT id FROM dish_ingredient_category WHERE name = '调料' AND level = 1 LIMIT 1);
SET @staple = (SELECT id FROM dish_ingredient_category WHERE name = '主食' AND level = 1 LIMIT 1);
SET @other = (SELECT id FROM dish_ingredient_category WHERE name = '其他' AND level = 1 LIMIT 1);

INSERT INTO dish_ingredient_category (parent_id, name, level, sort, enabled) VALUES
-- 蔬菜二级分类
(@veg, '瓜类', 2, 10, 1),
(@veg, '叶菜类', 2, 20, 1),
(@veg, '根茎类', 2, 30, 1),
(@veg, '菌菇类', 2, 40, 1),
(@veg, '豆荚类', 2, 50, 1),
(@veg, '未分类', 2, 999, 1),
-- 肉类二级分类
(@meat, '禽肉', 2, 10, 1),
(@meat, '畜肉', 2, 20, 1),
(@meat, '内脏', 2, 30, 1),
(@meat, '未分类', 2, 999, 1),
-- 水产二级分类
(@seafood, '鱼类', 2, 10, 1),
(@seafood, '虾蟹类', 2, 20, 1),
(@seafood, '贝类', 2, 30, 1),
(@seafood, '未分类', 2, 999, 1),
-- 豆制品二级分类
(@tofu, '豆腐类', 2, 10, 1),
(@tofu, '豆干类', 2, 20, 1),
(@tofu, '豆浆类', 2, 30, 1),
(@tofu, '未分类', 2, 999, 1),
-- 调料二级分类
(@spice, '咸味调料', 2, 10, 1),
(@spice, '香辛料', 2, 20, 1),
(@spice, '油脂类', 2, 30, 1),
(@spice, '酱料类', 2, 40, 1),
(@spice, '未分类', 2, 999, 1),
-- 主食二级分类
(@staple, '米类', 2, 10, 1),
(@staple, '面类', 2, 20, 1),
(@staple, '杂粮类', 2, 30, 1),
(@staple, '未分类', 2, 999, 1),
-- 其他二级分类
(@other, '未分类', 2, 999, 1);

-- 5. 迁移历史数据：按旧 category 映射到对应一级分类的"未分类"二级分类
UPDATE dish_ingredient di
JOIN dish_ingredient_category c1
  ON c1.name = CASE di.category
    WHEN 'MEAT' THEN '肉类'
    WHEN 'VEGETABLE' THEN '蔬菜'
    WHEN 'SEAFOOD' THEN '水产'
    WHEN 'TOFU' THEN '豆制品'
    WHEN 'SPICE' THEN '调料'
    WHEN 'RICE' THEN '主食'
    WHEN 'NOODLE' THEN '主食'
    ELSE '其他'
  END
 AND c1.level = 1
JOIN dish_ingredient_category c2
  ON c2.parent_id = c1.id
 AND c2.name = '未分类'
 AND c2.level = 2
SET di.category_id = c2.id
WHERE di.category_id IS NULL;
