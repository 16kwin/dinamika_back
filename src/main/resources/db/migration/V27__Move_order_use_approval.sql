-- V26__Move_order_use_approval.sql (или следующая по номеру)
BEGIN;
ALTER TABLE doc_move_order
    ADD COLUMN IF NOT EXISTS use_approval BOOLEAN NOT NULL DEFAULT TRUE;
COMMENT ON COLUMN doc_move_order.use_approval IS
    'Использовать режим согласования (true — есть шаг «На согласовании», false — сразу «В работу»)';
COMMIT;