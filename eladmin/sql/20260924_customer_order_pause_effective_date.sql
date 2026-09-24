ALTER TABLE customer_order
ADD COLUMN pause_effective_date DATE NULL COMMENT '订单首次从进行中转为暂停的生效日期' AFTER end_date;
