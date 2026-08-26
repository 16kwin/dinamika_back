-- V17__Add_production_directions_and_manufacturer_fields.sql

BEGIN;

-- ============================================================
-- 1. НАПРАВЛЕНИЯ ПРОИЗВОДСТВА
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_production_direction (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. ДОБАВЛЕНИЕ ПОЛЕЙ В ПРОИЗВОДИТЕЛЯ
-- ============================================================

ALTER TABLE spr_manufacturer
    ADD COLUMN IF NOT EXISTS code INTEGER UNIQUE,
    ADD COLUMN IF NOT EXISTS country_uid UUID REFERENCES spr_country(uid) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS direction_uid UUID REFERENCES spr_production_direction(uid) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS website VARCHAR(255),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_spr_manufacturer_code ON spr_manufacturer(code);
CREATE INDEX IF NOT EXISTS idx_spr_manufacturer_country ON spr_manufacturer(country_uid);
CREATE INDEX IF NOT EXISTS idx_spr_manufacturer_direction ON spr_manufacturer(direction_uid);

-- ============================================================
-- 3. НАСТРОЙКИ КОЛОНОК НАПРАВЛЕНИЙ ПРОИЗВОДСТВА
-- ============================================================

CREATE TABLE IF NOT EXISTS user_production_direction_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_production_direction_settings_user ON user_production_direction_column_settings(user_id);

-- ============================================================
-- 4. НАСТРОЙКИ КОЛОНОК ПРОИЗВОДИТЕЛЕЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_manufacturer_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_manufacturer_settings_user ON user_manufacturer_column_settings(user_id);

-- ============================================================
-- 5. СОБЫТИЯ НАПРАВЛЕНИЙ ПРОИЗВОДСТВА
-- ============================================================

CREATE TABLE IF NOT EXISTS production_direction_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    production_direction_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT production_direction_event_log_direction_fkey 
        FOREIGN KEY (production_direction_uid) REFERENCES spr_production_direction(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_production_direction_event_log_direction ON production_direction_event_log(production_direction_uid);
CREATE INDEX IF NOT EXISTS idx_production_direction_event_log_created ON production_direction_event_log(created_at DESC);

-- ============================================================
-- 6. СОБЫТИЯ ПРОИЗВОДИТЕЛЕЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS manufacturer_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT manufacturer_event_log_manufacturer_fkey 
        FOREIGN KEY (manufacturer_uid) REFERENCES spr_manufacturer(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_manufacturer_event_log_manufacturer ON manufacturer_event_log(manufacturer_uid);
CREATE INDEX IF NOT EXISTS idx_manufacturer_event_log_created ON manufacturer_event_log(created_at DESC);

-- ============================================================
-- 7. СИДЫ: НАПРАВЛЕНИЯ ПРОИЗВОДСТВА
-- ============================================================

INSERT INTO spr_production_direction (uid, name) VALUES
    (gen_random_uuid(), 'Металлообработка'),
    (gen_random_uuid(), 'Производство оснастки'),
    (gen_random_uuid(), 'Электрооборудование'),
    (gen_random_uuid(), 'Смазочные материалы'),
    (gen_random_uuid(), 'Метизы')
ON CONFLICT (name) DO NOTHING;

COMMIT;