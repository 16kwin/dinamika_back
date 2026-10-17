-- V17__Move_order_status_and_snapshot.sql
-- 1) Снимки имени/артикула/кода номенклатуры и доступного остатка в строках заказа.
-- 2) Снимки фиксируются в момент перехода POSTED → ACCEPTED (акцепт).
-- 3) До ACCEPTED значения в DTO отдаются «живыми» из spr_material + reg_stock_balance.

BEGIN;

ALTER TABLE reg_move_order_lines
    ADD COLUMN IF NOT EXISTS material_name_snapshot    TEXT,
    ADD COLUMN IF NOT EXISTS material_article_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS material_code_snapshot    INTEGER,
    ADD COLUMN IF NOT EXISTS available_snapshot        INTEGER;

COMMENT ON COLUMN reg_move_order_lines.material_name_snapshot IS
    'Снимок наименования номенклатуры на момент акцепта заказа';
COMMENT ON COLUMN reg_move_order_lines.material_article_snapshot IS
    'Снимок артикула номенклатуры на момент акцепта заказа';
COMMENT ON COLUMN reg_move_order_lines.material_code_snapshot IS
    'Снимок кода номенклатуры на момент акцепта заказа';
COMMENT ON COLUMN reg_move_order_lines.available_snapshot IS
    'Снимок доступного остатка на складе-отправителе на момент акцепта заказа';

COMMIT;