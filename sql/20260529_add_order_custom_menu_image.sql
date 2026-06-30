-- 订单主表新增自定义菜单图片字段
ALTER TABLE customer_order
ADD COLUMN custom_menu_image VARCHAR(500) NULL COMMENT '自定义菜单图片地址，用于客户换菜参考'
AFTER soup_count;
