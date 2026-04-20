/*
 * 退餐功能数据库脚本
 * 执行前请备份数据库！
 */

/*
-- ========================================
-- 1. 核销日志表新增退餐标记字段
-- ========================================
-- 注意：以下 ALTER TABLE 语句需要根据实际情况执行

ALTER TABLE meal_verification_log
ADD COLUMN IF NOT EXISTS is_refunded TINYINT(1) DEFAULT 0 COMMENT '是否已退餐：0=否，1=是',
ADD COLUMN IF NOT EXISTS refund_time DATETIME DEFAULT NULL COMMENT '退餐时间';
*/

/*
-- ========================================
-- 2. 新建退餐日志表
-- ========================================

CREATE TABLE IF NOT EXISTS meal_refund_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    customer_id BIGINT NOT NULL COMMENT '客户ID',
    refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '退款金额',
    refund_breakfast_count INT DEFAULT 0 COMMENT '退早餐数',
    refund_lunch_dinner_count INT DEFAULT 0 COMMENT '退午晚餐数',
    verified_breakfast_count INT DEFAULT 0 COMMENT '已核销早餐数（不退）',
    verified_lunch_dinner_count INT DEFAULT 0 COMMENT '已核销午晚餐数（不退）',
    refund_reason VARCHAR(500) DEFAULT NULL COMMENT '退餐原因',
    operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    operate_time DATETIME DEFAULT NULL COMMENT '操作时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id (order_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退餐日志表';
*/
