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
-- Number Pool Migration - Phase 5: 老数据迁移
--
-- Purpose:
--   - Migrate existing packages to new pool format
--   - Existing customer_code values remain unchanged
--   - pool_prefix: prefix → prefix + '1' (e.g., A → A1)
--   - pool_start: auto-calculated as max used code number + 1
--   - pool_end: default 1199, can be adjusted in Phase 4 admin UI
--
-- Note:
--   customer_profile 和 parent_package 通过 customer_code LIKE prefix 关联（非 parent_package_id）
--   COLLATE utf8mb4_unicode_ci 用于解决 utf8mb4_general_ci 和 utf8mb4_unicode_ci 混用问题
--
-- Execution:
--   1. Run BEFORE Phase 2-4 code is deployed
--   2. Run AFTER number_pool_schema.sql (Phase 1)
--   3. Run as MySQL DBA or via python pymysql
--
-- Requirements covered: POOL-15, POOL-16, POOL-17, POOL-18
-- ============================================================================

-- Step 0: Acquire migration lock (prevent concurrent execution)
SELECT GET_LOCK('number_pool_migration', 10) AS lock_status;

-- Step 1: Preview - Show current state before migration
SELECT
  'BEFORE MIGRATION' AS phase,
  pp.id,
  pp.package_name,
  pp.prefix AS old_prefix,
  pp.pool_prefix AS current_pool_prefix,
  (
    SELECT COUNT(*)
    FROM customer_profile cp
    WHERE cp.customer_code LIKE CONCAT(pp.prefix, '%') COLLATE utf8mb4_unicode_ci
      AND cp.customer_code IS NOT NULL
  ) AS existing_customers,
  (
    SELECT COALESCE(MAX(CAST(SUBSTRING(cp.customer_code, LENGTH(pp.prefix)+1) AS UNSIGNED)), 0)
    FROM customer_profile cp
    WHERE cp.customer_code LIKE CONCAT(pp.prefix, '%') COLLATE utf8mb4_unicode_ci
      AND cp.customer_code IS NOT NULL
  ) AS max_used_number
FROM parent_package pp
ORDER BY pp.id;

-- Step 2: Update pool_prefix, pool_start, pool_end for all existing packages
-- For each package:
--   pool_prefix = prefix + '1'  (e.g., A -> A1)
--   pool_start   = max used number for this prefix + 1  (e.g., max is A1050 -> pool_start = 1051)
--   pool_end     = 1199 (default, can be adjusted in Phase 4 admin UI)
UPDATE parent_package pp
SET
  pool_prefix = CONCAT(pp.prefix, '1'),
  pool_start  = (
    SELECT COALESCE(MAX(CAST(SUBSTRING(cp.customer_code, LENGTH(pp.prefix)+1) AS UNSIGNED)), 1000)
    FROM customer_profile cp
    WHERE cp.customer_code LIKE CONCAT(pp.prefix, '%') COLLATE utf8mb4_unicode_ci
      AND cp.customer_code IS NOT NULL
  ) + 1,
  pool_end    = 1199
WHERE pp.pool_prefix IS NULL OR pp.pool_prefix = pp.prefix;

-- Step 3: Preview - Show state after migration
SELECT
  'AFTER MIGRATION' AS phase,
  pp.id,
  pp.package_name,
  pp.prefix,
  pp.pool_prefix,
  pp.pool_start,
  pp.pool_end,
  (
    SELECT COUNT(*)
    FROM customer_profile cp
    WHERE cp.customer_code LIKE CONCAT(pp.pool_prefix, '%') COLLATE utf8mb4_unicode_ci
      AND cp.customer_code IS NOT NULL
  ) AS existing_customers
FROM parent_package pp
ORDER BY pp.id;

-- Step 4: Verify - Check for duplicate customer_codes
-- Expected result: 0 rows (no duplicates)
SELECT
  'VERIFICATION' AS phase,
  customer_code,
  COUNT(*) AS cnt
FROM customer_profile
WHERE customer_code IS NOT NULL
GROUP BY customer_code
HAVING COUNT(*) > 1;

-- Step 5: Release migration lock
SELECT RELEASE_LOCK('number_pool_migration') AS unlock_status;
