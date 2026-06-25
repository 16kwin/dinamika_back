-- V21__Drop_status_doc.sql

BEGIN;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS status_doc;

COMMIT;