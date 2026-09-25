ALTER TABLE customer_order
ADD COLUMN imported_verified_count INT NOT NULL DEFAULT 0 COMMENT '批量导入前已核销餐数基数，不对应系统内逐餐核销日志' AFTER verified_count;
