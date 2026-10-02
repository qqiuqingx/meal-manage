-- 在部署导入开始日期调整前执行；仅添加字段，不自动回填或改写现有订单。
ALTER TABLE customer_order
ADD COLUMN import_date DATE NULL COMMENT '采用数量快照的导入日界线，未来规划从次日承接；普通订单为空' AFTER start_date;
