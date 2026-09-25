ALTER TABLE customer_profile
ADD COLUMN delivery_phone_info TEXT NULL COMMENT '配送信息中的电话原文，可包含多个号码' AFTER phone;
