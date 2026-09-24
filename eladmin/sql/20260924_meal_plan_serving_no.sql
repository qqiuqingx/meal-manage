ALTER TABLE meal_plan_customer
    ADD COLUMN serving_no INT NOT NULL DEFAULT 1 COMMENT '同一订单日期餐次下的配送份序号' AFTER order_id;
