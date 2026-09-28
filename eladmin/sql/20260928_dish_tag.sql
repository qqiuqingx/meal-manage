CREATE TABLE IF NOT EXISTS `dish_tag` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '菜品标签ID',
    `name` VARCHAR(64) NOT NULL COMMENT '菜品标签名称',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NULL DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dish_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品标签字典';

CREATE TABLE IF NOT EXISTS `dish_tag_relation` (
    `dish_id` INT NOT NULL COMMENT '菜品ID',
    `tag_id` INT NOT NULL COMMENT '菜品标签ID',
    PRIMARY KEY (`dish_id`, `tag_id`),
    KEY `idx_dish_tag_relation_tag_dish` (`tag_id`, `dish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜品标签关联';

-- 新增标签字典操作权限到现有菜品管理菜单；不创建新页面，也不授予任何角色。
SET @dish_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE type = 1 AND permission = 'dish:list'
    ORDER BY menu_id
    LIMIT 1
);

SELECT CASE
           WHEN @dish_menu_id IS NULL THEN 'ERROR: 未找到 type=1 且 permission=dish:list 的菜品管理菜单，未新增标签权限。'
           ELSE CONCAT('OK: 菜品管理菜单 menu_id=', @dish_menu_id)
       END AS prerequisite_check;

START TRANSACTION;

INSERT INTO sys_menu (pid, sub_count, type, title, name, component, menu_sort, icon, path,
                      i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
SELECT @dish_menu_id, 0, 2, permission_rows.title, NULL, '', permission_rows.sort_order, '', '',
       b'0', b'0', b'0', permission_rows.permission_code, 'admin', NULL, NOW(), NULL
FROM (
    SELECT '菜品标签新增' AS title, 20 AS sort_order, 'dishTag:add' AS permission_code
    UNION ALL SELECT '菜品标签编辑', 21, 'dishTag:edit'
    UNION ALL SELECT '菜品标签删除', 22, 'dishTag:del'
) permission_rows
WHERE @dish_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.permission = permission_rows.permission_code
  );

UPDATE sys_menu parent
LEFT JOIN (
    SELECT pid, COUNT(1) AS child_count
    FROM sys_menu
    GROUP BY pid
) child_count ON child_count.pid = parent.menu_id
SET parent.sub_count = COALESCE(child_count.child_count, 0),
    parent.update_by = 'admin',
    parent.update_time = NOW()
WHERE parent.menu_id = @dish_menu_id
  AND @dish_menu_id IS NOT NULL;

COMMIT;
