/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * 客户档案排除日期功能数据库迁移脚本
 * 为客户档案表添加排除日期+餐次组合字段
 *
 * Phase 10-02: 数据存储基础 - SQL migration
 * DATE-01 requirement: Database schema for excluded_dates JSON field
 *
 * 示例值: [{"date":"2026-04-15","mealTypes":["BREAKFAST"]},
 *          {"date":"2026-04-16","mealTypes":["LUNCH","DINNER"]}]
 */

-- 检查并添加 excluded_dates 字段到 customer_profile 表
-- 注意: MySQL 不支持 ADD COLUMN IF NOT EXISTS 语法
-- 执行前请确认字段不存在，或使用以下验证语句检查
-- SELECT column_name FROM information_schema.columns
--   WHERE table_name = 'customer_profile' AND column_name = 'excluded_dates';

ALTER TABLE customer_profile
ADD COLUMN excluded_dates JSON NULL
COMMENT '排除日期+餐次组合(JSON数组，存储日期+餐次对象列表)';

-- 注意: 不创建 JSON 函数索引(MySQL 不支持 JSON_CONTAINS 函数索引语法)
-- excluded_dates 字段为 per-customer 数据, 列表长度通常较小
-- 性能可接受; 后续如需优化可使用 generated column 方式建索引

-- 字段用途说明:
-- 排除日期功能允许客户指定不想在排餐中出现的日期+餐次组合
-- 此字段存储 JSON 对象数组，每个对象包含:
--   - date: 日期 (格式: yyyy-MM-dd)
--   - mealTypes: 餐次列表 (BREAKFAST/LUNCH/DINNER)
-- 示例: [{"date":"2026-04-15","mealTypes":["BREAKFAST"]}]
-- 在排餐时会跳过这些日期+餐次组合，不生成排餐记录

-- 验证字段添加成功
SELECT column_name, data_type, is_nullable, column_comment
FROM information_schema.columns
WHERE table_name = 'customer_profile'
AND column_name = 'excluded_dates';
