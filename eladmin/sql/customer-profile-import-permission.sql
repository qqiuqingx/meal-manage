-- 客户与首单批量导入权限点；执行前确认客户档案菜单 menu_id=123 存在。
START TRANSACTION;

INSERT INTO sys_menu (pid, sub_count, type, title, name, component, menu_sort, icon, path,
                      i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
SELECT 123, 0, 2, '客户数据导入', NULL, '', 5, '', '', b'0', b'0', b'0',
       'customerProfile:import', 'admin', NULL, NOW(), NULL
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 123)
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE pid = 123 AND permission = 'customerProfile:import');

SET @customer_import_menu_inserted = ROW_COUNT();
UPDATE sys_menu
SET sub_count = sub_count + @customer_import_menu_inserted,
    update_by = 'admin',
    update_time = NOW()
WHERE menu_id = 123;

COMMIT;
