-- 订单行内编辑追加式审计表。发布前先备份目标数据库并确认 DDL 窗口。
CREATE TABLE IF NOT EXISTS customer_order_inline_audit (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计记录主键',
    order_id BIGINT NOT NULL COMMENT '被修改的订单主键',
    customer_id BIGINT NOT NULL COMMENT '被修改订单所属的客户主键',
    field_key VARCHAR(64) NOT NULL COMMENT '本次请求修改的字段键',
    operator VARCHAR(100) NOT NULL COMMENT '执行修改的操作人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计记录创建时间',
    before_state LONGTEXT NOT NULL COMMENT '按实体主键组织的修改前字段状态 JSON',
    after_state LONGTEXT NOT NULL COMMENT '按实体主键组织的修改后字段状态 JSON',
    PRIMARY KEY (id),
    KEY idx_order_inline_audit_order_time (order_id, created_at),
    KEY idx_order_inline_audit_customer_time (customer_id, created_at),
    KEY idx_order_inline_audit_operator_time (operator, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单行内修改追加式审计记录';
