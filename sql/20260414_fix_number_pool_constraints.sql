-- ============================================================================
-- Number Pool Hotfix
-- Date: 2026-04-14
--
-- Purpose:
--   1. Remove the unique index that blocks customer_code reuse
--   2. Safe to run after sql/number_pool_schema.sql has already been executed
--
-- Notes:
--   - The number-pool design allows reusing customer_code after all orders for the
--     previous owner are cancelled or completed.
--   - Keeping uk_customer_code will cause inserts for reused codes to fail.
-- ============================================================================

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'customer_profile'
      AND index_name = 'uk_customer_code'
);

SET @drop_index_sql := IF(
    @index_exists > 0,
    'ALTER TABLE customer_profile DROP INDEX uk_customer_code',
    'SELECT ''uk_customer_code not found, skip'''
);

PREPARE stmt FROM @drop_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
