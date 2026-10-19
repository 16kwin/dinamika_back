-- V15__Doc_transfer_closed_at.sql
BEGIN;

ALTER TABLE doc_transfer
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;

COMMIT;