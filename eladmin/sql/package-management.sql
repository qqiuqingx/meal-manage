-- ============================================================
-- 套餐管理模块数据库脚本
-- 包含: DDL + 种子数据 + sys_menu 路由配置
-- ============================================================

-- --------------------------------------------------------
-- 1. 父套餐表 parent_package
-- --------------------------------------------------------
DROP TABLE IF EXISTS `parent_package`;
CREATE TABLE `parent_package` (
    `id`            BIGINT        NOT NULL  AUTO_INCREMENT  COMMENT '主键ID',
    `package_code`  VARCHAR(32)   NOT NULL                    COMMENT '套餐编码',
    `prefix`        VARCHAR(1)    NOT NULL                    COMMENT '单字母前缀',
    `package_name`  VARCHAR(64)   NOT NULL                    COMMENT '套餐名称',
    `status`        TINYINT       NOT NULL  DEFAULT 1         COMMENT '状态: 0=禁用, 1=启用',
    `remark`        VARCHAR(255)  NULL                        COMMENT '备注',
    `created_at`   DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`    DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_package_code`  (`package_code`),
    UNIQUE KEY `uk_prefix`        (`prefix`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4  COLLATE=utf8mb4_unicode_ci  COMMENT='父套餐表';

-- --------------------------------------------------------
-- 2. 子套餐表 sub_package（含规格字段）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `sub_package`;
CREATE TABLE `sub_package` (
    `id`               BIGINT        NOT NULL  AUTO_INCREMENT  COMMENT '主键ID',
    `sub_package_code` VARCHAR(32)   NOT NULL                    COMMENT '子套餐编码',
    `sub_package_name` VARCHAR(64)   NOT NULL                    COMMENT '子套餐名称',
    `meat_count`       INT           NOT NULL  DEFAULT 0         COMMENT '荤菜数量',
    `veg_count`        INT           NOT NULL  DEFAULT 0         COMMENT '素菜数量',
    `include_soup`     TINYINT       NOT NULL  DEFAULT 0         COMMENT '是否含汤: 0=无, 1=含',
    `include_rice`     TINYINT       NOT NULL  DEFAULT 1         COMMENT '是否含米饭: 0=无, 1=含',
    `status`           TINYINT       NOT NULL  DEFAULT 1         COMMENT '状态: 0=禁用, 1=启用',
    `remark`           VARCHAR(255)  NULL                        COMMENT '备注',
    `created_at`       DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`       DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_package_code` (`sub_package_code`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4  COLLATE=utf8mb4_unicode_ci  COMMENT='子套餐表';

-- --------------------------------------------------------
-- 3. 父子关联表 parent_package_sub
-- --------------------------------------------------------
DROP TABLE IF EXISTS `parent_package_sub`;
CREATE TABLE `parent_package_sub` (
    `id`                 BIGINT        NOT NULL  AUTO_INCREMENT  COMMENT '主键ID',
    `parent_package_id`  BIGINT        NOT NULL                    COMMENT '父套餐ID',
    `sub_package_id`     BIGINT        NOT NULL                    COMMENT '子套餐ID',
    `status`             TINYINT       NOT NULL  DEFAULT 1         COMMENT '状态: 0=禁用, 1=启用',
    `created_at`         DATETIME      NOT NULL  DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_sub`   (`parent_package_id`, `sub_package_id`),
    INDEX `idx_parent_package_id` (`parent_package_id`),
    INDEX `idx_sub_package_id`    (`sub_package_id`)
) ENGINE=InnoDB  DEFAULT CHARSET=utf8mb4  COLLATE=utf8mb4_unicode_ci  COMMENT='父子套餐关联表';

-- --------------------------------------------------------
-- 4. 种子数据
-- --------------------------------------------------------

-- 4.1 父套餐
INSERT INTO `parent_package` (`id`, `package_code`, `prefix`, `package_name`, `status`, `remark`, `created_at`, `updated_at`) VALUES
(1, 'JKC001', 'J', '健康餐', 1, '健康餐父套餐', NOW(), NOW()),
(2, 'YYC001', 'Y', '营养餐', 1, '营养餐父套餐', NOW(), NOW());

-- 4.2 子套餐（健康餐）
INSERT INTO `sub_package` (`id`, `sub_package_code`, `sub_package_name`, `meat_count`, `veg_count`, `include_soup`, `include_rice`, `status`, `remark`, `created_at`, `updated_at`) VALUES
(1, 'JKC001-01', '一荤一素', 1, 1, 1, 1, 1, '健康餐-一荤一素，含汤含米饭', NOW(), NOW()),
(2, 'JKC001-02', '两荤一素', 2, 1, 1, 1, 1, '健康餐-两荤一素，含汤含米饭', NOW(), NOW()),
(3, 'JKC001-03', '减脂餐',   1, 2, 0, 1, 1, '健康餐-减脂餐，无汤含米饭', NOW(), NOW());

-- 4.3 子套餐（营养餐）
INSERT INTO `sub_package` (`id`, `sub_package_code`, `sub_package_name`, `meat_count`, `veg_count`, `include_soup`, `include_rice`, `status`, `remark`, `created_at`, `updated_at`) VALUES
(4, 'YYC001-01', '标准餐', 1, 1, 1, 1, 1, '营养餐-标准餐，含汤含米饭', NOW(), NOW()),
(5, 'YYC001-02', '轻食餐', 0, 2, 0, 1, 1, '营养餐-轻食餐，无汤含米饭', NOW(), NOW());

-- 4.4 关联数据
-- 健康餐(JKC001) 关联子套餐 1, 2, 3
INSERT INTO `parent_package_sub` (`parent_package_id`, `sub_package_id`, `status`, `created_at`) VALUES
(1, 1, 1, NOW()),
(1, 2, 1, NOW()),
(1, 3, 1, NOW());

-- 营养餐(YYC001) 关联子套餐 4, 5
INSERT INTO `parent_package_sub` (`parent_package_id`, `sub_package_id`, `status`, `created_at`) VALUES
(2, 4, 1, NOW()),
(2, 5, 1, NOW());

-- --------------------------------------------------------
-- 5. sys_menu 路由配置
-- --------------------------------------------------------

-- 5.1 父菜单: 套餐管理（menu_id=138，pid=122 客户管理目录）
INSERT INTO `sys_menu` (`menu_id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES
(138, 122, 0, 1, '套餐管理', 'customer/package/index', 'customer/package/index', 5, 'bug', '', 0, 0, 0, '', 'admin', '', NOW(), NOW());

-- 5.2 按钮: 查看
INSERT INTO `sys_menu` (`menu_id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES
(139, 138, 0, 2, '套餐管理查看', NULL, NULL, 0, '', '', 0, 0, 0, 'package:list', 'admin', '', NOW(), NOW());

-- 5.3 按钮: 新增
INSERT INTO `sys_menu` (`menu_id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES
(140, 138, 0, 2, '套餐管理新增', NULL, NULL, 0, '', '', 0, 0, 0, 'package:add', 'admin', '', NOW(), NOW());

-- 5.4 按钮: 编辑
INSERT INTO `sys_menu` (`menu_id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES
(141, 138, 0, 2, '套餐管理编辑', NULL, NULL, 0, '', '', 0, 0, 0, 'package:edit', 'admin', '', NOW(), NOW());

-- 5.5 按钮: 删除
INSERT INTO `sys_menu` (`menu_id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES
(142, 138, 0, 2, '套餐管理删除', NULL, NULL, 0, '', '', 0, 0, 0, 'package:del', 'admin', '', NOW(), NOW());

-- 5.6 按钮: 修改状态
INSERT INTO `sys_menu` (`menu_id`, `pid`, `sub_count`, `type`, `title`, `name`, `component`, `menu_sort`, `icon`, `path`, `i_frame`, `cache`, `hidden`, `permission`, `create_by`, `update_by`, `create_time`, `update_time`) VALUES
(143, 138, 0, 2, '套餐管理修改状态', NULL, NULL, 0, '', '', 0, 0, 0, 'package:status', 'admin', '', NOW(), NOW());

-- --------------------------------------------------------
-- 6. 删除旧套餐分类管理菜单（menu_id=124 及其按钮 129-131）
-- --------------------------------------------------------
DELETE FROM `sys_menu` WHERE `menu_id` IN (129, 130, 131, 124);

-- --------------------------------------------------------
-- 7. 删除旧表（确认新模块正常后可执行）
-- --------------------------------------------------------
-- DROP TABLE IF EXISTS `customer_package_category`;
