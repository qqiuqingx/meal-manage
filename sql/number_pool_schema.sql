/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

-- ============================================================================
-- Number Pool Schema Migration
-- Phase 1: 套餐编号池 - 数据库结构变更
--
-- Purpose:
--   - Add pool configuration columns to parent_package table
--   - Add unique index on customer_profile.customer_code
--
-- Execution order:
--   1. Run this SQL before Phase 2 code is deployed
--   2. Phase 5 migration script will populate pool_prefix / pool_start / pool_end
--      for existing packages (existing customer codes remain unchanged)
--
-- Requirements covered: POOL-01, POOL-02, POOL-03, POOL-04
-- ============================================================================

-- Step 1: Add pool configuration columns to parent_package
-- pool_prefix: 编号池前缀（如 A1），字母+数字格式，VARCHAR(10) accommodates up to "Z999"
-- pool_start:  编号池起始号（如 1001），正整数
-- pool_end:    编号池结束号（如 1199），正整数
ALTER TABLE parent_package
  ADD COLUMN pool_prefix VARCHAR(10) DEFAULT NULL COMMENT '编号池前缀（如A1）' AFTER `prefix`,
  ADD COLUMN pool_start  INT UNSIGNED  DEFAULT NULL COMMENT '编号池起始号（如1001）' AFTER pool_prefix,
  ADD COLUMN pool_end    INT UNSIGNED  DEFAULT NULL COMMENT '编号池结束号（如1199）' AFTER pool_start;

-- Step 2: Add unique index on customer_profile.customer_code
-- MySQL unique index permits multiple NULL values (new profiles before allocation),
-- which is the desired behavior. DB-level constraint is the last line of defense
-- against duplicate allocations.
ALTER TABLE customer_profile
  ADD UNIQUE INDEX uk_customer_code (customer_code);
