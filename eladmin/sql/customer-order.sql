-- Customer Order Module DDL + Sys Menu
-- Created: 2026-03-26

-- ============================================
-- Table: customer_order
-- Purpose: Customer order management
-- ============================================
DROP TABLE IF EXISTS customer_order;
CREATE TABLE customer_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID(关联customer_profile)',
    customer_code VARCHAR(16) NOT NULL COMMENT '客户编号(关联customer_profile)',
    parent_package_id BIGINT NULL COMMENT '父套餐ID(关联customer_package_category)',
    child_package_id BIGINT NULL COMMENT '子套餐ID(关联customer_package_category)',
    order_code VARCHAR(32) NOT NULL COMMENT '订单编号(唯一,如ORD20260326001)',
    deposit_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '定金金额',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总金额',
    final_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '成交金额',
    breakfast_count INT NOT NULL DEFAULT 0 COMMENT '早餐合计餐数',
    lunch_dinner_count INT NOT NULL DEFAULT 0 COMMENT '午餐+晚餐合计餐数',
    total_count INT NOT NULL DEFAULT 0 COMMENT '总餐数',
    breakfast_price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '早餐单价',
    lunch_dinner_price DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '午餐晚餐单价',
    verified_count INT NOT NULL DEFAULT 0 COMMENT '核销餐数(合计)',
    verified_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '核销金额',
    meal_balance DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '餐费余额(成交金额-核销金额)',
    remaining_count INT NOT NULL DEFAULT 0 COMMENT '剩余餐数(合计餐数-核销餐数)',
    deal_time DATETIME NULL COMMENT '成交时间',
    first_delivery_time DATETIME NULL COMMENT '第一次送餐时间',
    start_date DATE NULL COMMENT '订单开始日期',
    end_date DATE NULL COMMENT '订单结束日期',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '订单状态(0=已取消,1=进行中,2=已完成)',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by VARCHAR(100) NULL COMMENT '创建人',
    update_by VARCHAR(100) NULL COMMENT '更新人',
    create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_code (order_code),
    KEY idx_customer_id (customer_id),
    KEY idx_customer_code (customer_code),
    KEY idx_status (status),
    KEY idx_start_date (start_date),
    KEY idx_end_date (end_date),
    KEY idx_deal_time (deal_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户订单表';

-- ============================================
-- Sys Menu (for permission management)
-- Menu structure under "客户管理" (pid=122):
--   订单管理 (directory, id=133)
--     ├── 订单管理 (menu, id=134)
--         ├── 订单新增 (button, id=135)
--         ├── 订单编辑 (button, id=136)
--         ├── 订单删除 (button, id=137)
-- ============================================

-- 订单管理 (directory)
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
VALUES (133, 122, 1, 0, '订单管理', NULL, NULL, 3, 'documentation', 'order', b'0', b'0', b'0', NULL, NULL, NULL, NOW(), NULL);

-- 订单管理 (menu)
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time)
VALUES (134, 133, 3, 1, '订单管理', 'CustomerOrder', 'customer/order/index', 1, 'documentation', 'order', b'0', b'0', b'0', 'customerOrder:list', NULL, NULL, NOW(), NULL);

-- 订单按钮权限 (pid = 134)
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (135, 134, 0, 2, '订单新增', NULL, '', 1, '', '', b'0', b'0', b'0', 'customerOrder:add', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (136, 134, 0, 2, '订单编辑', NULL, '', 2, '', '', b'0', b'0', b'0', 'customerOrder:edit', NULL, NULL, NOW(), NULL);
INSERT INTO sys_menu (menu_id, pid, sub_count, type, title, name, component, menu_sort, icon, path, i_frame, cache, hidden, permission, create_by, update_by, create_time, update_time) VALUES (137, 134, 0, 2, '订单删除', NULL, '', 3, '', '', b'0', b'0', b'0', 'customerOrder:del', NULL, NULL, NOW(), NULL);
