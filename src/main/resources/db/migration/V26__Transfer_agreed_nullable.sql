-- V26__Transfer_agreed_nullable.sql
-- agreed имеет смысл только для документов, созданных из заказа на перемещение (base_type = 'MOVE_ORDER').
-- Для MANUAL/REFILL поле должно быть NULL, поэтому снимаем NOT NULL.

BEGIN;

ALTER TABLE doc_transfer
    ALTER COLUMN agreed DROP NOT NULL;

COMMENT ON COLUMN doc_transfer.agreed IS
    'Согласован (только для base_type = MOVE_ORDER; для MANUAL/REFILL — NULL)';

COMMIT;