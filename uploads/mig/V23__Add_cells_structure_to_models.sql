-- V23__Add_cells_structure_to_models.sql

-- Добавляем поле cells_structure в station_models
ALTER TABLE IF EXISTS station_models
    ADD COLUMN IF NOT EXISTS cells_structure TEXT;

COMMENT ON COLUMN station_models.cells_structure IS 'JSON структура ячеек модели станции';