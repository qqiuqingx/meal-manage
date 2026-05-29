-- 将 24d4b966 新增的客户用餐统计页面调整为“排餐计划”，并挂到客户管理目录下。
-- 执行前请确认 sys_menu 中“客户管理/餐客户管理”目录存在；本项目默认 menu_id=122。

SET @customer_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE title IN ('客户管理', '餐客户管理')
      AND type = 0
    ORDER BY menu_id
    LIMIT 1
);

SET @meal_plan_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE name = 'CustomerMealStats'
       OR component = 'customer/mealStats/index'
       OR path = 'customer-meal-stats'
       OR path = 'mealStats'
    ORDER BY menu_id
    LIMIT 1
);

INSERT INTO sys_menu (
    pid, sub_count, type, title, name, component, menu_sort, icon, path,
    i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time
)
SELECT
    @customer_menu_id, 0, 1, '排餐计划', 'CustomerMealStats', 'customer/mealStats/index', 6, 'table', 'mealStats',
    b'0', b'0', b'0', 'customerProfile:list', 'admin', NULL, NOW(), NULL
WHERE @customer_menu_id IS NOT NULL
  AND @meal_plan_menu_id IS NULL;

SET @meal_plan_menu_id := COALESCE(@meal_plan_menu_id, LAST_INSERT_ID());

UPDATE sys_menu
SET pid = @customer_menu_id,
    type = 1,
    title = '排餐计划',
    name = 'CustomerMealStats',
    component = 'customer/mealStats/index',
    menu_sort = 6,
    icon = 'table',
    path = 'mealStats',
    i_frame = b'0',
    cache = b'0',
    hidden = b'0',
    permission = 'customerProfile:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = @meal_plan_menu_id
  AND @customer_menu_id IS NOT NULL;

UPDATE sys_menu parent
SET parent.sub_count = (
    SELECT COUNT(1)
    FROM sys_menu child
    WHERE child.pid = parent.menu_id
)
WHERE parent.menu_id = @customer_menu_id
  AND @customer_menu_id IS NOT NULL;
