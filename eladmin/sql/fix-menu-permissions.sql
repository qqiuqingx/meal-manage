-- ============================================================
-- 餐客户管理子菜单权限点补充
-- 梳理日期：2026-06-11
-- 说明：餐客户管理(menu_id=122)下原有菜单中，
--   仅 客户档案管理(menu_id=123) 和 订单管理(menu_id=133) 配置了页面级和按钮级权限点，
--   其余页面以下方面缺失：
--     ① 页面级 permission 字段（type=1）：菜单本身没有 list 权限
--     ② 按钮级菜单条目（type=2）：新增/编辑/删除等操作的权限点
--
-- 执行前请确认 sys_menu 中对应 menu_id 存在。
-- ============================================================

-- ===================== 第一部分：补充页面级权限 (type=1) =====================

-- 1. 菜品管理 (menu_id=119) -> dish:list
--    按钮权限（dish:add/edit/del）已有（menu_id=149/150/151）
UPDATE sys_menu
SET permission = 'dish:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 119;

-- 2. 配菜管理 (menu_id=120) -> dishIngredient:list
--    按钮权限（dishIngredient:add/edit/del）已有（menu_id=146/147/148）
UPDATE sys_menu
SET permission = 'dishIngredient:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 120;

-- 3. 排餐管理 (menu_id=121) -> mealSchedule:list
--    按钮权限（mealPlan:generate）已有（menu_id=145），
--    但后端 MealSchedulePlanController 还用了 mealSchedule:add/edit/del
UPDATE sys_menu
SET permission = 'mealSchedule:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 121;

-- 4. 套餐管理 (menu_id=138) -> package:list
--    原 permission 为空字符串，需补充
--    按钮权限（package:list/add/edit/del/status）已有（menu_id=139~143, 152）
UPDATE sys_menu
SET permission = 'package:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 138;

-- 5. 销售数据看板 (menu_id=144) -> salesDashboard:list
UPDATE sys_menu
SET permission = 'salesDashboard:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 144;

-- 6. 核销记录 (menu_id=153) -> verificationLog:list
UPDATE sys_menu
SET permission = 'verificationLog:list',
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 153;


-- ===================== 第二部分：补充按钮级权限 (type=2) =====================

-- 7. 排餐计划 -> 补充按钮权限（继承自 customerProfile + mealPlan）
--    页面级已有 customerProfile:list，但缺按钮级条目
--    - 后端 CustomerProfileController 的排餐调整接口使用 customerProfile:edit
--    - 后端 MealPlanController 的 depletion-warnings 接口使用 mealPlan:list
INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 155, 0, 2, '排餐计划查看', NULL, 'customerProfile:list', '', '', 1, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 155 AND permission = 'customerProfile:list');

INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 155, 0, 2, '排餐计划编辑', NULL, 'customerProfile:edit', '', '', 2, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 155 AND permission = 'customerProfile:edit');

INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 155, 0, 2, '排餐计划餐数预警', NULL, 'mealPlan:list', '', '', 3, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 155 AND permission = 'mealPlan:list');

-- 8. 核销记录 -> 补充按钮权限
--    后端 MealVerificationController 的删除接口使用 mealPlan:list
INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 153, 0, 2, '核销记录查看', NULL, 'verificationLog:list', '', '', 1, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 153 AND permission = 'verificationLog:list');

INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 153, 0, 2, '核销记录删除', NULL, 'verificationLog:del', '', '', 2, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 153 AND permission = 'verificationLog:del');

-- 9. 销售数据看板 -> 补充按钮权限
INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 144, 0, 2, '销售数据看板查看', NULL, 'salesDashboard:list', '', '', 1, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 144 AND permission = 'salesDashboard:list');

-- 10. 排餐管理 -> 补充缺少的按钮权限
--     目前仅有 mealPlan:generate（menu_id=145），缺 mealSchedule:list/add/edit/del
INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 121, 0, 2, '排餐管理查看', NULL, 'mealSchedule:list', '', '', 1, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 121 AND permission = 'mealSchedule:list');

INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 121, 0, 2, '排餐管理新增', NULL, 'mealSchedule:add', '', '', 2, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 121 AND permission = 'mealSchedule:add');

INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 121, 0, 2, '排餐管理编辑', NULL, 'mealSchedule:edit', '', '', 3, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 121 AND permission = 'mealSchedule:edit');

INSERT INTO sys_menu (pid, sub_count, type, title, name, permission, component, path, menu_sort, i_frame, cache, hidden, create_by, create_time)
SELECT 121, 0, 2, '排餐管理删除', NULL, 'mealSchedule:del', '', '', 4, b'0', b'0', b'0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 121 AND permission = 'mealSchedule:del');


-- ===================== 第三部分：更新菜单 sub_count =====================
UPDATE sys_menu
SET sub_count = (
    SELECT cnt FROM (
        SELECT COUNT(1) AS cnt
        FROM sys_menu child
        WHERE child.pid = sys_menu.menu_id
    ) AS tmp
)
WHERE menu_id IN (121, 144, 153, 155);