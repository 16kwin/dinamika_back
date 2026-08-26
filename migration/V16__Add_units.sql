-- V16__Add_units.sql

BEGIN;

-- ============================================================
-- 1. ЕДИНИЦЫ ИЗМЕРЕНИЯ (НОМЕНКЛАТУРА)
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_unit (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. НАСТРОЙКИ КОЛОНОК ЕДИНИЦ ИЗМЕРЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_unit_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_unit_settings_user ON user_unit_column_settings(user_id);

-- ============================================================
-- 3. СОБЫТИЯ ЕДИНИЦ ИЗМЕРЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS unit_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unit_event_log_unit_fkey 
        FOREIGN KEY (unit_uid) REFERENCES spr_unit(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_unit_event_log_unit ON unit_event_log(unit_uid);
CREATE INDEX IF NOT EXISTS idx_unit_event_log_created ON unit_event_log(created_at DESC);

-- ============================================================
-- 4. СИДЫ: ЕДИНИЦЫ ИЗМЕРЕНИЯ
-- ============================================================

INSERT INTO spr_unit (uid, name, description) VALUES
    (gen_random_uuid(), 'шт', 'Штука'),
    (gen_random_uuid(), 'кг', 'Килограмм'),
    (gen_random_uuid(), 'упаковка', 'Упаковка')
ON CONFLICT (name) DO NOTHING;

COMMIT;