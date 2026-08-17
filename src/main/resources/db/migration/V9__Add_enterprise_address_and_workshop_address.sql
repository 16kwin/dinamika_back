-- V9__Add_enterprise_address_and_workshop_address.sql

BEGIN;

-- Добавляем колонку description в enterprises
ALTER TABLE enterprises ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Добавляем колонку address в enterprises
ALTER TABLE enterprises ADD COLUMN IF NOT EXISTS address VARCHAR(500);

-- Добавляем колонку description в workshops
ALTER TABLE workshops ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- Добавляем колонку address в workshops
ALTER TABLE workshops ADD COLUMN IF NOT EXISTS address VARCHAR(500);

COMMIT;