-- V25__Extend_stations.sql

-- Добавляем недостающие поля в таблицу stations
ALTER TABLE IF EXISTS stations
    ADD COLUMN IF NOT EXISTS code INTEGER UNIQUE,
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS production_date DATE,
    ADD COLUMN IF NOT EXISTS serial_number VARCHAR(255),
    ADD COLUMN IF NOT EXISTS configuration_uid UUID REFERENCES station_configurations(uid) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS ip_address VARCHAR(50),
    ADD COLUMN IF NOT EXISTS network_port INTEGER,
    ADD COLUMN IF NOT EXISTS is_additional_module BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS has_additional_module BOOLEAN NOT NULL DEFAULT FALSE;

-- Индексы для новых полей
CREATE INDEX IF NOT EXISTS idx_stations_code ON stations(code);
CREATE INDEX IF NOT EXISTS idx_stations_configuration ON stations(configuration_uid);

-- Генерируем коды для существующих станций (если есть)
DO $$
DECLARE
    r RECORD;
    v_counter INTEGER := 1;
BEGIN
    FOR r IN SELECT uid FROM stations WHERE code IS NULL ORDER BY created_at, uid
    LOOP
        UPDATE stations SET code = v_counter WHERE uid = r.uid;
        v_counter := v_counter + 1;
    END LOOP;
END $$;