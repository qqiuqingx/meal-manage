ALTER TABLE customer_meal_schedule_addition
    ADD COLUMN quantity INT NOT NULL DEFAULT 1 COMMENT '目标配送份数' AFTER meal_type,
    ADD COLUMN soup_quantity INT DEFAULT NULL COMMENT '目标份数中含汤的份数，NULL表示沿用订单汤品配置' AFTER quantity;
