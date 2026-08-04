-- Agent 统一只读工具权限补充（2026-08-04）
--
-- 统一查询不再把 mealPlan:list/customerOrder:list 兼容为核销或退餐权限。
-- 执行后请在角色管理中按岗位显式分配 mealVerification:list、mealRefund:list。
-- 本脚本只新增隐藏按钮级权限，不自动扩大普通角色的数据访问范围。

-- 核销记录页面下增加 Agent 专用只读权限点。
INSERT INTO sys_menu (
    pid, sub_count, type, title, name, permission, component, path, menu_sort,
    i_frame, cache, hidden, create_by, create_time
)
SELECT parent.menu_id, 0, 2, 'Agent核销记录查询', NULL, 'mealVerification:list', '', '', 20,
       b'0', b'0', b'1', 'admin', NOW()
FROM sys_menu parent
WHERE parent.title = '核销记录'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu item WHERE item.permission = 'mealVerification:list'
  );

-- 退餐日志通常挂在订单管理下；若部署环境有独立退餐菜单，也可手动将该权限点移动到对应父菜单。
INSERT INTO sys_menu (
    pid, sub_count, type, title, name, permission, component, path, menu_sort,
    i_frame, cache, hidden, create_by, create_time
)
SELECT parent.menu_id, 0, 2, 'Agent退餐记录查询', NULL, 'mealRefund:list', '', '', 21,
       b'0', b'0', b'1', 'admin', NOW()
FROM sys_menu parent
WHERE parent.title = '订单管理'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu item WHERE item.permission = 'mealRefund:list'
  );

UPDATE sys_menu parent
SET parent.sub_count = (
    SELECT COUNT(1) FROM sys_menu child WHERE child.pid = parent.menu_id
)
WHERE parent.title IN ('核销记录', '订单管理');
