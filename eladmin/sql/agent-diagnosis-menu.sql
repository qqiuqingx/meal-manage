-- 智能排查助手菜单与权限
-- 依赖客户管理目录（title = '客户管理'），页面组件：eladmin-web/src/views/agent/diagnosis/index.vue

INSERT INTO sys_menu (
    pid, sub_count, type, title, name, component, menu_sort, icon, path,
    i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time
)
SELECT
    parent.menu_id, 1, 1, '智能排查助手', 'AgentDiagnosis', 'agent/diagnosis/index', 9, 'search', 'agent/diagnosis',
    b'0', b'0', b'0', 'agentDiagnosis:list', NULL, NULL, NOW(), NULL
FROM sys_menu parent
WHERE parent.title = '客户管理'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE title = '智能排查助手');

UPDATE sys_menu menu
JOIN sys_menu parent ON parent.title = '客户管理'
SET menu.pid = parent.menu_id,
    menu.sub_count = 1,
    menu.type = 1,
    menu.name = 'AgentDiagnosis',
    menu.component = 'agent/diagnosis/index',
    menu.menu_sort = 9,
    menu.icon = 'search',
    menu.path = 'agent/diagnosis',
    menu.i_frame = b'0',
    menu.cache = b'0',
    menu.hidden = b'0',
    menu.permission = 'agentDiagnosis:list',
    menu.update_time = NOW()
WHERE menu.title = '智能排查助手';

INSERT INTO sys_roles_menus (menu_id, role_id)
SELECT menu.menu_id, 1
FROM sys_menu menu
WHERE menu.title = '智能排查助手'
  AND NOT EXISTS (
      SELECT 1 FROM sys_roles_menus role_menu
      WHERE role_menu.menu_id = menu.menu_id AND role_menu.role_id = 1
  );

UPDATE sys_menu parent
JOIN (
    SELECT pid, COUNT(1) AS child_count
    FROM sys_menu
    GROUP BY pid
) child_stat ON child_stat.pid = parent.menu_id
SET parent.sub_count = child_stat.child_count
WHERE parent.title = '客户管理';
