-- V2__drop_available_balance_column.sql
-- Remove the available_balance column from accounts table (if it exists).
-- The field was always 0 because no business method updated it.
-- The available balance is now computed via Account#getAvailableBalance() which returns this.balance.
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'accounts'
               AND COLUMN_NAME = 'available_balance');
SET @sql = IF(@exist > 0,
    'ALTER TABLE accounts DROP COLUMN available_balance',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
