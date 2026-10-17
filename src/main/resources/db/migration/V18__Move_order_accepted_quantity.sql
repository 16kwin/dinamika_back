-- V18__Move_order_accepted_quantity.sql
-- Храним «акцептированное» количество строки отдельно от текущего quantity.
-- При акцепте accepted_quantity = quantity. В статусе ACCEPTED разрешено
-- уменьшать quantity, но не больше accepted_quantity (проверка на бэке).

BEGIN;

ALTER TABLE reg_move_order_lines
    ADD COLUMN IF NOT EXISTS accepted_quantity INTEGER;

COMMENT ON COLUMN reg_move_order_lines.accepted_quantity IS
    'Количество строки на момент акцепта заказа. Верхняя граница для уменьшения в статусе ACCEPTED';

COMMIT;