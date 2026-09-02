-- V3__Add_column_drum_to_reg_cells.sql
ALTER TABLE reg_cells 
ADD COLUMN IF NOT EXISTS column_number INTEGER,
ADD COLUMN IF NOT EXISTS drum_number INTEGER;