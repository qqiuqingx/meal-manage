-- Customer Profile Module DDL + Seed Data
-- Created: 2026-03-25
-- Description: Customer profile management, package category tree, addresses

-- ============================================
-- Table: customer_package_category
-- Purpose: Two-level package category (parent/child)
-- ============================================
DROP TABLE IF EXISTS customer_package_category;
CREATE TABLE customer_package_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    category_name VARCHAR(100) NOT NULL COMMENT '分类名称',
    category_code VARCHAR(50) NOT NULL COMMENT '分类编码(全局唯一)',
    parent_id BIGINT NULL COMMENT '父级ID(顶级为NULL)',
    level INT NOT NULL DEFAULT 1 COMMENT '层级(1=父级,2=子级)',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(0=否,1=是)',
    code_prefix CHAR(1) NULL COMMENT '编号前缀(仅父级使用,单个大写字母)',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_category_code (category_code),
    UNIQUE KEY uk_code_prefix (code_prefix),
    KEY idx_parent_id (parent_id),
    KEY idx_level (level),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户套餐分类表';

-- ============================================
-- Table: customer_profile
-- Purpose: Customer profile master record
-- ============================================
DROP TABLE IF EXISTS customer_profile;
CREATE TABLE customer_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    customer_code VARCHAR(16) NOT NULL COMMENT '客户编号(唯一,如A001)',
    customer_name VARCHAR(50) NOT NULL COMMENT '客户姓名',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    gestational_week INT NULL COMMENT '孕周(正整数)',
    allergy_tags JSON NULL COMMENT '过敏食物标签(JSON数组)',
    medical_requirements VARCHAR(500) NULL COMMENT '医嘱要求',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态(0=停用,1=启用)',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by VARCHAR(100) NULL COMMENT '创建人',
    update_by VARCHAR(100) NULL COMMENT '更新人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_customer_code (customer_code),
    KEY idx_phone (phone),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户档案表';

-- ============================================
-- Table: customer_profile_address
-- Purpose: Customer address slots (DEFAULT/WORKDAY/WEEKEND)
-- ============================================
DROP TABLE IF EXISTS customer_profile_address;
CREATE TABLE customer_profile_address (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    address_type VARCHAR(20) NOT NULL COMMENT '地址类型(DEFAULT/WORKDAY/WEEKEND)',
    address_detail VARCHAR(200) NOT NULL COMMENT '详细地址',
    contact_name VARCHAR(50) NULL COMMENT '联系人姓名',
    contact_phone VARCHAR(20) NULL COMMENT '联系人电话',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_customer_address_type (customer_id, address_type),
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户地址表';

-- ============================================
-- Table: customer_profile_package
-- Purpose: Customer current package contract
-- ============================================
DROP TABLE IF EXISTS customer_profile_package;
CREATE TABLE customer_profile_package (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    parent_package_id BIGINT NOT NULL COMMENT '父套餐ID',
    child_package_id BIGINT NOT NULL COMMENT '子套餐ID',
    breakfast_count INT NULL COMMENT '早餐数',
    lunch_dinner_count INT NULL COMMENT '午餐+晚餐数',
    total_count INT NOT NULL DEFAULT 0 COMMENT '总份数(后端计算)',
    start_date DATE NOT NULL COMMENT '签约开始日期',
    end_date DATE NOT NULL COMMENT '签约结束日期',
    active_flag TINYINT NOT NULL DEFAULT 1 COMMENT '生效标志(0=失效,1=生效)',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_customer_id (customer_id),
    KEY idx_active_flag (active_flag),
    KEY idx_start_date (start_date),
    KEY idx_end_date (end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户签约表';

-- ============================================
-- Seed Data: Package Categories
-- ============================================
-- 父级套餐: 月子餐 (code_prefix = A)
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('月子餐', 'PACKAGE_A', NULL, 1, 1, 1, 'A');

-- 父级套餐: 营养餐 (code_prefix = B)
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('营养餐', 'PACKAGE_B', NULL, 1, 2, 1, 'B');

-- 子级套餐: 月子餐 -> 两荤一素
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('两荤一素', 'PACKAGE_A_1', 1, 2, 1, 1, NULL);

-- 子级套餐: 月子餐 -> 一荤一素
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('一荤一素', 'PACKAGE_A_2', 1, 2, 2, 1, NULL);

-- 子级套餐: 月子餐 -> 两荤两素
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('两荤两素', 'PACKAGE_A_3', 1, 2, 3, 1, NULL);

-- 子级套餐: 营养餐 -> 标准餐
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('标准餐', 'PACKAGE_B_1', 2, 2, 1, 1, NULL);

-- 子级套餐: 营养餐 -> 轻食餐
INSERT INTO customer_package_category (category_name, category_code, parent_id, level, sort, enabled, code_prefix) VALUES
('轻食餐', 'PACKAGE_B_2', 2, 2, 2, 1, NULL);

-- ============================================
-- Seed Data: Sys Menu (for permission management)
-- ============================================
-- Menu structure:
--   客户管理 (directory, id=118)
--     ├── 客户档案管理 (menu, id=119)
--     │   ├── 客户档案新增 (button)
--     │   ├── 客户档案编辑 (button)
--     │   ├── 客户档案状态 (button)
--     │   └── 客户档案删除 (button)
--     └── 套餐分类管理 (menu, id=120)
--         ├── 套餐分类新增 (button)
--         ├── 套餐分类编辑 (button)
--         ├── 套餐分类删除 (button)
--         └── 套餐分类状态 (button)

-- 客户管理 (directory)
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
VALUES (118, NULL, 2, 0, '客户管理', NULL, NULL, 15, 'peoples', 'customer', b'0', b'0', b'0', NULL, NULL, NULL, NOW(), NULL);

-- 客户档案管理 (menu)
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
VALUES (119, 118, 4, 1, '客户档案管理', 'CustomerProfile', 'customer/profile/index', 1, 'peoples', 'profile', b'0', b'0', b'0', 'customerProfile:list', NULL, NULL, NOW(), NULL);

-- 套餐分类管理 (menu)
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
VALUES (120, 118, 4, 1, '套餐分类管理', 'CustomerPackageCategory', 'customer/packageCategory/index', 2, 'tree-table', 'packageCategory', b'0', b'0', b'0', 'customerPackageCategory:list', NULL, NULL, NOW(), NULL);

-- 客户档案按钮权限
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (121, 119, 0, 2, '客户档案新增', NULL, '', 1, '', '', b'0', b'0', b'0', 'customerProfile:add', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (122, 119, 0, 2, '客户档案编辑', NULL, '', 2, '', '', b'0', b'0', b'0', 'customerProfile:edit', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (123, 119, 0, 2, '客户档案状态', NULL, '', 3, '', '', b'0', b'0', b'0', 'customerProfile:status', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (124, 119, 0, 2, '客户档案删除', NULL, '', 4, '', '', b'0', b'0', b'0', 'customerProfile:del', NULL, NULL, NOW(), NULL);

-- 套餐分类按钮权限
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (125, 120, 0, 2, '套餐分类新增', NULL, '', 1, '', '', b'0', b'0', b'0', 'customerPackageCategory:add', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (126, 120, 0, 2, '套餐分类编辑', NULL, '', 2, '', '', b'0', b'0', b'0', 'customerPackageCategory:edit', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (127, 120, 0, 2, '套餐分类删除', NULL, '', 3, '', '', b'0', b'0', b'0', 'customerPackageCategory:del', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (128, 120, 0, 2, '套餐分类状态', NULL, '', 4, '', '', b'0', b'0', b'0', 'customerPackageCategory:status', NULL, NULL, NOW(), NULL);