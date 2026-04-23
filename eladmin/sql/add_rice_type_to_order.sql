-- 新增米饭类型字段，默认白米饭
ALTER TABLE customer_order ADD COLUMN rice_type VARCHAR(20) DEFAULT '白米饭' COMMENT '米饭类型（普通杂粮米饭、杂粮1:1米饭、三色糙米、白米饭）';

-- 已有数据统一设置为白米饭
UPDATE customer_order SET rice_type = '白米饭' WHERE rice_type IS NULL;
