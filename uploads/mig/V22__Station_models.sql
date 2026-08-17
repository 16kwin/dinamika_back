-- V22__Station_models.sql

-- 1. Таблица типов станций
CREATE TABLE IF NOT EXISTS station_types (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Таблица производителей станций
CREATE TABLE IF NOT EXISTS station_manufacturers (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Таблица моделей станций
CREATE TABLE IF NOT EXISTS station_models (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code INTEGER NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    article VARCHAR(255),
    revision VARCHAR(255),
    type_id UUID REFERENCES station_types(uid) ON DELETE SET NULL,
    manufacturer_id UUID REFERENCES station_manufacturers(uid) ON DELETE SET NULL,
    purpose TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_station_models_code ON station_models(code);
CREATE INDEX IF NOT EXISTS idx_station_models_type ON station_models(type_id);
CREATE INDEX IF NOT EXISTS idx_station_models_manufacturer ON station_models(manufacturer_id);

-- 4. Таблица изображений моделей станций
CREATE TABLE IF NOT EXISTS station_model_images (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_uid UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_station_model_images_model ON station_model_images(model_uid);

-- 5. Добавляем model_id в stations (ссылка на station_models.uid)
ALTER TABLE IF EXISTS stations
    ADD COLUMN IF NOT EXISTS model_id UUID REFERENCES station_models(uid) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_stations_model ON stations(model_id);

-- 6. Тестовые типы станций
INSERT INTO station_types (uid, name, description) VALUES
    (gen_random_uuid(), 'DRUM_TYPE', 'Барабанного типа'),
    (gen_random_uuid(), 'POSTAMAT_TYPE', 'Постамат'),
    (gen_random_uuid(), 'ADDITIONAL_MODULE', 'Дополнительный модуль')
ON CONFLICT (name) DO NOTHING;

-- 7. Тестовые производители
INSERT INTO station_manufacturers (uid, name, description) VALUES
    (gen_random_uuid(), 'СтанкоПром', 'Отечественный производитель станков'),
    (gen_random_uuid(), 'TechMachines', 'Зарубежный производитель оборудования'),
    (gen_random_uuid(), 'ИнструментСервис', 'Производитель инструментальных станций')
ON CONFLICT (name) DO NOTHING;