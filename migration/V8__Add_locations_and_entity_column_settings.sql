-- V8__Add_locations_and_entity_column_settings.sql

BEGIN;

-- ============================================================
-- 1. НОВАЯ ТАБЛИЦА LOCATIONS (РАСПОЛОЖЕНИЯ)
-- ============================================================

CREATE TABLE IF NOT EXISTS locations (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. ДОБАВЛЕНИЕ КОЛОНОК В СУЩЕСТВУЮЩИЕ ТАБЛИЦЫ
-- ============================================================

-- Холдинг: расположение
ALTER TABLE holdings
    ADD COLUMN IF NOT EXISTS location_uuid UUID REFERENCES locations(uid) ON DELETE SET NULL;

-- Предприятие: расположение
ALTER TABLE enterprises
    ADD COLUMN IF NOT EXISTS location_uuid UUID REFERENCES locations(uid) ON DELETE SET NULL;

-- Цех: расположение
ALTER TABLE workshops
    ADD COLUMN IF NOT EXISTS location_uuid UUID REFERENCES locations(uid) ON DELETE SET NULL;

-- Производитель: страна
ALTER TABLE station_manufacturers
    ADD COLUMN IF NOT EXISTS country_uuid UUID REFERENCES spr_country(uid) ON DELETE SET NULL;

-- ============================================================
-- 3. ТАБЛИЦЫ СОБЫТИЙ (EVENT LOG)
-- ============================================================

-- События: Расположения
CREATE TABLE IF NOT EXISTS location_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_uid UUID NOT NULL REFERENCES locations(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Страны
CREATE TABLE IF NOT EXISTS country_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_uid UUID NOT NULL REFERENCES spr_country(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Типы станций
CREATE TABLE IF NOT EXISTS station_type_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_type_uid UUID NOT NULL REFERENCES station_types(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Производители станций
CREATE TABLE IF NOT EXISTS station_manufacturer_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_manufacturer_uid UUID NOT NULL REFERENCES station_manufacturers(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Холдинги
CREATE TABLE IF NOT EXISTS holding_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id BIGINT NOT NULL REFERENCES holdings(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Предприятия
CREATE TABLE IF NOT EXISTS enterprise_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id BIGINT NOT NULL REFERENCES enterprises(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Цеха
CREATE TABLE IF NOT EXISTS workshop_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workshop_id BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Участки
CREATE TABLE IF NOT EXISTS section_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id BIGINT NOT NULL REFERENCES sections(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Модели станций
CREATE TABLE IF NOT EXISTS station_model_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_model_uid UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- События: Конфигурации станций
CREATE TABLE IF NOT EXISTS station_configuration_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_configuration_uid UUID NOT NULL REFERENCES station_configurations(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 4. ТАБЛИЦЫ НАСТРОЕК КОЛОНОК (USER COLUMN SETTINGS)
-- ============================================================

-- Настройки колонок: Расположения
CREATE TABLE IF NOT EXISTS user_location_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Страны
CREATE TABLE IF NOT EXISTS user_country_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Типы станций
CREATE TABLE IF NOT EXISTS user_station_type_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Производители станций
CREATE TABLE IF NOT EXISTS user_station_manufacturer_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Холдинги
CREATE TABLE IF NOT EXISTS user_holding_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Предприятия
CREATE TABLE IF NOT EXISTS user_enterprise_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Цеха
CREATE TABLE IF NOT EXISTS user_workshop_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Участки
CREATE TABLE IF NOT EXISTS user_section_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Модели станций
CREATE TABLE IF NOT EXISTS user_station_model_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Настройки колонок: Конфигурации станций
CREATE TABLE IF NOT EXISTS user_station_configuration_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- ============================================================
-- 5. ИНДЕКСЫ
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_locations_name ON locations(name);

CREATE INDEX IF NOT EXISTS idx_holdings_location ON holdings(location_uuid);
CREATE INDEX IF NOT EXISTS idx_enterprises_location ON enterprises(location_uuid);
CREATE INDEX IF NOT EXISTS idx_workshops_location ON workshops(location_uuid);
CREATE INDEX IF NOT EXISTS idx_station_manufacturers_country ON station_manufacturers(country_uuid);

CREATE INDEX IF NOT EXISTS idx_location_event_log_location ON location_event_log(location_uid);
CREATE INDEX IF NOT EXISTS idx_location_event_log_created ON location_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_country_event_log_country ON country_event_log(country_uid);
CREATE INDEX IF NOT EXISTS idx_country_event_log_created ON country_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_station_type_event_log_type ON station_type_event_log(station_type_uid);
CREATE INDEX IF NOT EXISTS idx_station_type_event_log_created ON station_type_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_station_manufacturer_event_log_manufacturer ON station_manufacturer_event_log(station_manufacturer_uid);
CREATE INDEX IF NOT EXISTS idx_station_manufacturer_event_log_created ON station_manufacturer_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_holding_event_log_holding ON holding_event_log(holding_id);
CREATE INDEX IF NOT EXISTS idx_holding_event_log_created ON holding_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_enterprise_event_log_enterprise ON enterprise_event_log(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_enterprise_event_log_created ON enterprise_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_workshop_event_log_workshop ON workshop_event_log(workshop_id);
CREATE INDEX IF NOT EXISTS idx_workshop_event_log_created ON workshop_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_section_event_log_section ON section_event_log(section_id);
CREATE INDEX IF NOT EXISTS idx_section_event_log_created ON section_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_station_model_event_log_model ON station_model_event_log(station_model_uid);
CREATE INDEX IF NOT EXISTS idx_station_model_event_log_created ON station_model_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_station_configuration_event_log_config ON station_configuration_event_log(station_configuration_uid);
CREATE INDEX IF NOT EXISTS idx_station_configuration_event_log_created ON station_configuration_event_log(created_at DESC);

-- ============================================================
-- 6. СИДЫ: РАСПОЛОЖЕНИЯ
-- ============================================================

INSERT INTO locations (uid, name) VALUES
    (gen_random_uuid(), 'Москва'),
    (gen_random_uuid(), 'Санкт-Петербург'),
    (gen_random_uuid(), 'Казань'),
    (gen_random_uuid(), 'Новосибирск')
ON CONFLICT (name) DO NOTHING;

-- Привязка холдингов к расположениям
UPDATE holdings SET location_uuid = (SELECT uid FROM locations WHERE name = 'Москва') WHERE name = 'Холдинг Север';
UPDATE holdings SET location_uuid = (SELECT uid FROM locations WHERE name = 'Казань') WHERE name = 'Холдинг Юг';

-- Привязка предприятий к расположениям
UPDATE enterprises SET location_uuid = (SELECT uid FROM locations WHERE name = 'Москва') WHERE name = 'Предприятие №1';
UPDATE enterprises SET location_uuid = (SELECT uid FROM locations WHERE name = 'Санкт-Петербург') WHERE name = 'Предприятие №2';
UPDATE enterprises SET location_uuid = (SELECT uid FROM locations WHERE name = 'Казань') WHERE name = 'Предприятие №3';
UPDATE enterprises SET location_uuid = (SELECT uid FROM locations WHERE name = 'Новосибирск') WHERE name = 'Предприятие №4';

-- Привязка цехов к расположениям
UPDATE workshops SET location_uuid = (SELECT uid FROM locations WHERE name = 'Москва') WHERE name = 'Цех №1' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №1');
UPDATE workshops SET location_uuid = (SELECT uid FROM locations WHERE name = 'Москва') WHERE name = 'Цех №2' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №1');
UPDATE workshops SET location_uuid = (SELECT uid FROM locations WHERE name = 'Санкт-Петербург') WHERE name = 'Цех №1' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №2');
UPDATE workshops SET location_uuid = (SELECT uid FROM locations WHERE name = 'Санкт-Петербург') WHERE name = 'Цех №2' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №2');
UPDATE workshops SET location_uuid = (SELECT uid FROM locations WHERE name = 'Казань') WHERE name = 'Цех №3' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №3');
UPDATE workshops SET location_uuid = (SELECT uid FROM locations WHERE name = 'Новосибирск') WHERE name = 'Цех №4' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №4');

-- Привязка производителей к странам
UPDATE station_manufacturers SET country_uuid = (SELECT uid FROM spr_country WHERE name = 'Россия') WHERE name = 'СтанкоПром';
UPDATE station_manufacturers SET country_uuid = (SELECT uid FROM spr_country WHERE name = 'Германия') WHERE name = 'TechMachines';
UPDATE station_manufacturers SET country_uuid = (SELECT uid FROM spr_country WHERE name = 'Россия') WHERE name = 'ИнструментСервис';

COMMIT;