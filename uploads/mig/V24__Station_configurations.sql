-- V24__Station_configurations.sql

-- Таблица конфигураций станций
CREATE TABLE IF NOT EXISTS station_configurations (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    model_id UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    cells_structure TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_station_configurations_model ON station_configurations(model_id);