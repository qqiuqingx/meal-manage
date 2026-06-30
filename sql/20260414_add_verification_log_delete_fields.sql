-- Quick-001: 核销日志软删除字段迁移
-- Created: 2026-04-14
-- Description: 为 meal_verification_log 表添加软删除相关字段

-- 添加软删除字段
ALTER TABLE meal_verification_log
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除（0未删除，1已删除）' AFTER remark,
    ADD COLUMN delete_time DATETIME DEFAULT NULL COMMENT '删除时间' AFTER deleted,
    ADD COLUMN deleted_by VARCHAR(50) DEFAULT '' COMMENT '删除操作人' AFTER delete_time,
    ADD COLUMN delete_reason VARCHAR(255) DEFAULT '' COMMENT '删除原因' AFTER deleted_by;

-- 添加索引（可选，用于查询优化）
ALTER TABLE meal_verification_log
    ADD INDEX idx_deleted (deleted);

-- 说明：
-- 1. deleted=0 表示正常记录，deleted=1 表示已删除
-- 2. delete_time 记录删除时间
-- 3. deleted_by 记录删除操作人用户名
-- 4. delete_reason 记录删除原因（前端传入）
-- 5. 分页查询自动排除 deleted=1 的记录（见 MealVerificationLogMapper.xml）