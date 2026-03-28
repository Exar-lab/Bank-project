-- V2__drop_available_balance_column.sql
-- Remove the available_balance column from accounts table.
-- The field was always 0 because no business method updated it.
-- The available balance is now computed via Account#getAvailableBalance() which returns this.balance.
ALTER TABLE accounts DROP COLUMN available_balance;
