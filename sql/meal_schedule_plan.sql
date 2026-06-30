CREATE TABLE IF NOT EXISTS meal_schedule_plan (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    week_num      TINYINT NOT NULL COMMENT '周序号(1-4)',
    day_of_week   TINYINT NOT NULL COMMENT '星期几(1-7)',
    meal_time     VARCHAR(20) NOT NULL COMMENT 'BREAKFAST早餐/LUNCH午餐/DINNER晚餐',
    dish_category VARCHAR(20) NOT NULL COMMENT '菜品坑位分类：MAIN主菜/SIDE副菜/SOUP汤品/VEGETABLE蔬菜/RICE米饭',
    dish_id       INT NOT NULL COMMENT '关联dish表主键',
    enabled       TINYINT DEFAULT 1 COMMENT '是否启用：0禁用/1启用',
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_slot (week_num, day_of_week, meal_time, dish_category),
    KEY idx_dish_id (dish_id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='餐品菜单计划映射表';

INSERT INTO meal_schedule_plan (week_num, day_of_week, meal_time, dish_category, dish_id, enabled, create_time, update_time)
SELECT
  CAST(SUBSTRING_INDEX(s.split_value, '-', 1) AS UNSIGNED) AS week_num,
  CAST(SUBSTRING_INDEX(s.split_value, '-', -1) AS UNSIGNED) AS day_of_week,
  m.meal_type AS meal_time,
  d.dish_type AS dish_category,
  d.id AS dish_id,
  COALESCE(d.enabled, 1) AS enabled,
  NOW() AS create_time,
  NOW() AS update_time
FROM dish d
CROSS JOIN JSON_TABLE(
  d.schedule,
  '$[*]' COLUMNS (split_value VARCHAR(10) PATH '$')
) AS s
CROSS JOIN JSON_TABLE(
  d.meal_types,
  '$[*]' COLUMNS (meal_type VARCHAR(20) PATH '$')
) AS m
WHERE d.schedule IS NOT NULL
  AND JSON_LENGTH(d.schedule) > 0
  AND d.meal_types IS NOT NULL
  AND JSON_LENGTH(d.meal_types) > 0;

-- 确认业务代码已全部切到 meal_schedule_plan 后再执行
-- ALTER TABLE dish
--   DROP COLUMN schedule,
--   DROP COLUMN meal_types,
--   DROP COLUMN dish_type;
