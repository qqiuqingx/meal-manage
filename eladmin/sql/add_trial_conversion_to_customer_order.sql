-- 增加试餐成单标记与关联试餐订单字段
ALTER TABLE customer_order
ADD COLUMN trial_converted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否试餐成单，0=否，1=是'
AFTER customer_source,
ADD COLUMN trial_order_id BIGINT NULL COMMENT '关联试餐订单ID'
AFTER trial_converted;

CREATE INDEX idx_trial_order_id ON customer_order(trial_order_id);
