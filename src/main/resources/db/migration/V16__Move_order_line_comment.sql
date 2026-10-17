-- V16__Move_order_line_comment.sql
BEGIN;
ALTER TABLE reg_move_order_lines
    ADD COLUMN IF NOT EXISTS comment TEXT;
COMMIT;