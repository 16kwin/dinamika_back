-- V11__Stock_balance_min_critical.sql
-- Добавляем минимальный и критический остаток в сводный регистр склада.
-- И проставляем сиды: для всех номенклатур — остаток 50, минимальный 20, критический 10.

BEGIN;

-- ============================================================
-- 1. НОВЫЕ КОЛОНКИ
-- ============================================================

ALTER TABLE reg_stock_balance
    ADD COLUMN IF NOT EXISTS min_stock      INTEGER,
    ADD COLUMN IF NOT EXISTS critical_stock INTEGER;

COMMENT ON COLUMN reg_stock_balance.min_stock IS
    'Минимальный остаток номенклатуры на складе';
COMMENT ON COLUMN reg_stock_balance.critical_stock IS
    'Критический остаток номенклатуры на складе';

-- ============================================================
-- 2. СИДЫ: для всех существующих номенклатур на складе MAIN
--    остаток 50, минимальный 20, критический 10
-- ============================================================

INSERT INTO reg_stock_balance (material_uid, stock_uid, clean_quantity, reserved_quantity, min_stock, critical_stock)
SELECT
    m.uid,
    'MAIN',
    50,
    0,
    20,
    10
FROM spr_material m
ON CONFLICT (material_uid, stock_uid) DO UPDATE
SET
    clean_quantity   = EXCLUDED.clean_quantity,
    min_stock        = EXCLUDED.min_stock,
    critical_stock   = EXCLUDED.critical_stock,
    updated_at       = CURRENT_TIMESTAMP;

COMMIT;