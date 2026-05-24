CREATE TABLE customer_meal_schedule_addition (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  customer_id BIGINT NOT NULL COMMENT '客户ID',
  order_id BIGINT NOT NULL COMMENT '订单ID，用于确定套餐、餐品配置和餐数池',
  record_date DATE NOT NULL COMMENT '人工新增排餐日期',
  meal_type VARCHAR(20) NOT NULL COMMENT '餐次：BREAKFAST/LUNCH/DINNER',
  remark VARCHAR(255) DEFAULT NULL COMMENT '人工新增原因或备注',
  deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0否，1是',
  create_by VARCHAR(255) DEFAULT NULL COMMENT '创建人',
  update_by VARCHAR(255) DEFAULT NULL COMMENT '更新人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_cmsa_customer_date (customer_id, record_date),
  KEY idx_cmsa_order_date_meal (order_id, record_date, meal_type),
  UNIQUE KEY uk_cmsa_active_order_date_meal (order_id, record_date, meal_type, deleted)
) COMMENT='客户排餐日历人工新增记录';
