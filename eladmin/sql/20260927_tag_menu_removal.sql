-- 移除配料标签独立菜单：标签维护已收敛到配菜管理页内（页内弹窗 + 卡片快捷绑定）。
-- 脚本可重复执行：按钮权限（add/edit/del）迁移到配料管理菜单下继续生效，原标签菜单精确删除。
-- 说明：不执行无条件删除，删除仅限定 type=1、permission='dishIngredientTag:list' 且无子节点的菜单。

SET @ingredient_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE type = 1 AND permission = 'dishIngredient:list'
    ORDER BY menu_id
    LIMIT 1
);

SET @tag_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE type = 1 AND permission = 'dishIngredientTag:list'
    ORDER BY menu_id
    LIMIT 1
);

SELECT CASE
           WHEN @ingredient_menu_id IS NULL THEN 'ERROR: 未找到 dishIngredient:list 菜单，请先核对配料管理菜单。'
           WHEN @tag_menu_id IS NULL THEN 'SKIP: 标签菜单已不存在，无需迁移。'
           ELSE CONCAT('OK: 配料管理菜单 menu_id=', @ingredient_menu_id, '，标签菜单 menu_id=', @tag_menu_id)
       END AS prerequisite_check;

START TRANSACTION;

-- 1. 将 add/edit/del 三个按钮权限迁移到配料管理菜单下（permission 码保持不变）。
UPDATE sys_menu button
JOIN (
    SELECT 'dishIngredientTag:add' AS p_code
    UNION ALL SELECT 'dishIngredientTag:edit'
    UNION ALL SELECT 'dishIngredientTag:del'
) codes ON button.permission = codes.p_code
SET button.pid = @ingredient_menu_id,
    button.update_by = 'admin',
    button.update_time = NOW()
WHERE button.type = 2
  AND button.pid = @tag_menu_id
  AND @tag_menu_id IS NOT NULL
  AND @ingredient_menu_id IS NOT NULL;

-- 2. 迁移完成后，校验标签菜单下是否仍有子节点（有则跳过删除，避免误删）。
SET @tag_child_count := (
    SELECT COUNT(1)
    FROM sys_menu
    WHERE pid = @tag_menu_id
      AND @tag_menu_id IS NOT NULL
);

-- 3. 精确删除标签菜单入口。
DELETE FROM sys_menu
WHERE menu_id = @tag_menu_id
  AND type = 1
  AND permission = 'dishIngredientTag:list'
  AND @tag_menu_id IS NOT NULL
  AND @tag_child_count = 0;

-- 4. 重算配料管理菜单的 sub_count。
UPDATE sys_menu parent
LEFT JOIN (
    SELECT pid, COUNT(1) AS child_count
    FROM sys_menu
    GROUP BY pid
) child_count ON child_count.pid = parent.menu_id
SET parent.sub_count = COALESCE(child_count.child_count, 0),
    parent.update_by = 'admin',
    parent.update_time = NOW()
WHERE parent.menu_id = @ingredient_menu_id
  AND @ingredient_menu_id IS NOT NULL;

COMMIT;
