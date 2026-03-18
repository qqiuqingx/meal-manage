-- 每日排餐记录表
CREATE TABLE dish_schedule_record (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    record_date DATE NOT NULL COMMENT '排餐日期',
    meal_type VARCHAR(20) NOT NULL COMMENT '餐次：LUNCH午餐、DINNER晚餐',
    week_num INT NOT NULL COMMENT '周数',
    day_of_week INT NOT NULL COMMENT '星期（1-7）',
    customer_count INT DEFAULT 0 COMMENT '客户数量',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_date_meal (record_date, meal_type),
    INDEX idx_record_date (record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日排餐记录表';

-- 客户菜单记录表
CREATE TABLE customer_menu_record (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    record_id INT NOT NULL COMMENT '关联排餐记录ID',
    customer_id INT NOT NULL COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    dish_type VARCHAR(20) NOT NULL COMMENT '菜品类型：MAIN主菜、SIDE副菜、SOUP汤、VEGETABLE素菜、RICE米饭',
    dish_id INT COMMENT '菜品ID',
    dish_name VARCHAR(100) COMMENT '菜品名称',
    dish_ingredients VARCHAR(500) COMMENT '配料',
    is_replaced TINYINT DEFAULT 0 COMMENT '是否被替换（0否/1是）',
    original_dish_id INT COMMENT '原菜品ID',
    replacement_reason VARCHAR(200) COMMENT '替换原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_record_id (record_id),
    INDEX idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户菜单记录表';
