-- 菜品表
CREATE TABLE dish (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name VARCHAR(100) NOT NULL COMMENT '菜品名称',
    cooking_method TEXT COMMENT '做法/流程',
    ingredients TEXT COMMENT '配料',
    image_url VARCHAR(500) COMMENT '图片路径',
    dish_type VARCHAR(20) NOT NULL COMMENT '菜品类型：MAIN主菜、SIDE副菜、SOUP汤、VEGETABLE素菜、RICE米饭',
    meal_types JSON COMMENT '餐次：LUNCH午餐、DINNER晚餐',
    meal_packages JSON COMMENT '所属套餐',
    schedule JSON COMMENT '排期：格式如1-1表示第1周周一',
    sort INT DEFAULT 0 COMMENT '排序',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_dish_type (dish_type),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';
