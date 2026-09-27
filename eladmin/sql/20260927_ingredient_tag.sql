-- 配料多标签字典和关联表。脚本可重复执行，不插入业务标签，也不授予角色权限。
CREATE TABLE IF NOT EXISTS dish_ingredient_tag
(
    id          INT          NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    name        VARCHAR(64)  NOT NULL COMMENT '标签名称',
    create_time DATETIME     NOT NULL COMMENT '创建时间',
    update_time DATETIME              DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dish_ingredient_tag_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dish_ingredient_tag_relation
(
    ingredient_id INT NOT NULL COMMENT '配料ID',
    tag_id        INT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (ingredient_id, tag_id),
    KEY idx_ingredient_tag_tag_ingredient (tag_id, ingredient_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 标签菜单挂在现有配料管理目录下。若权限菜单缺失，以下检查会显示原因且不会创建根菜单。
SET @ingredient_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE type = 1 AND permission = 'dishIngredient:list'
    ORDER BY menu_id
    LIMIT 1
);

SELECT CASE
           WHEN @ingredient_menu_id IS NULL THEN 'ERROR: 未找到 dishIngredient:list 菜单，请先核对配料管理菜单。'
           ELSE CONCAT('OK: 配料管理菜单 menu_id=', @ingredient_menu_id)
       END AS prerequisite_check;

START TRANSACTION;

INSERT INTO sys_menu (pid, sub_count, type, title, name, component, menu_sort, icon, path,
                      i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
SELECT @ingredient_menu_id, 0, 1, '配料标签', 'DishIngredientTag', 'meal/dishIngredientTag/index', 8,
       'el-icon-collection-tag', 'dishIngredientTag', b'0', b'0', b'0',
       'dishIngredientTag:list', 'admin', NULL, NOW(), NULL
WHERE @ingredient_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'dishIngredientTag:list');

SET @ingredient_tag_menu_id := (
    SELECT menu_id
    FROM sys_menu
    WHERE permission = 'dishIngredientTag:list'
    ORDER BY menu_id
    LIMIT 1
);

INSERT INTO sys_menu (pid, sub_count, type, title, name, component, menu_sort, icon, path,
                      i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
SELECT @ingredient_tag_menu_id, 0, 2, permission_rows.title, NULL, '', permission_rows.sort_order, '', '',
       b'0', b'0', b'0', permission_rows.permission_code, 'admin', NULL, NOW(), NULL
FROM (
    SELECT '标签新增' AS title, 1 AS sort_order, 'dishIngredientTag:add' AS permission_code
    UNION ALL SELECT '标签编辑', 2, 'dishIngredientTag:edit'
    UNION ALL SELECT '标签删除', 3, 'dishIngredientTag:del'
) permission_rows
WHERE @ingredient_tag_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.pid = @ingredient_tag_menu_id
        AND existing.permission = permission_rows.permission_code
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
WHERE parent.menu_id IN (@ingredient_menu_id, @ingredient_tag_menu_id)
  AND @ingredient_menu_id IS NOT NULL
  AND @ingredient_tag_menu_id IS NOT NULL;

COMMIT;
