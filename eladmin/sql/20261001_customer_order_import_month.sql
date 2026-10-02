-- 在月度续导代码部署前执行；不自动改写或回填现有订单。
-- import_date 沿用 20261001_customer_order_import_date.sql 的独立迁移。
ALTER TABLE customer_order
ADD COLUMN import_month DATE NULL COMMENT '当前餐数与余额采用的来源月份，以月首日保存；普通订单为空' AFTER import_date;
