-- V15__Add_attribute_groups_and_links.sql

BEGIN;

-- ============================================================
-- 1. ГРУППЫ ХАРАКТЕРИСТИК
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_attribute_group (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. ДОБАВЛЕНИЕ GROUP_UID В ВИДЫ ХАРАКТЕРИСТИК
-- ============================================================

ALTER TABLE spr_type_attributes
    ADD COLUMN IF NOT EXISTS group_uid UUID REFERENCES spr_attribute_group(uid) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_spr_type_attributes_group ON spr_type_attributes(group_uid);

-- ============================================================
-- 3. ДОБАВЛЕНИЕ GROUP_UID В ЕДИНИЦЫ ИЗМЕРЕНИЯ
-- ============================================================

ALTER TABLE spr_measure
    ADD COLUMN IF NOT EXISTS group_uid UUID REFERENCES spr_attribute_group(uid) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_spr_measure_group ON spr_measure(group_uid);

-- ============================================================
-- 4. НАСТРОЙКИ КОЛОНОК ГРУПП ХАРАКТЕРИСТИК
-- ============================================================

CREATE TABLE IF NOT EXISTS user_attribute_group_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_attribute_group_settings_user ON user_attribute_group_column_settings(user_id);

-- ============================================================
-- 5. НАСТРОЙКИ КОЛОНОК ВИДОВ ХАРАКТЕРИСТИК
-- ============================================================

CREATE TABLE IF NOT EXISTS user_type_attribute_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_type_attribute_settings_user ON user_type_attribute_column_settings(user_id);

-- ============================================================
-- 6. НАСТРОЙКИ КОЛОНОК ЕДИНИЦ ИЗМЕРЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_measure_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_measure_settings_user ON user_measure_column_settings(user_id);

-- ============================================================
-- 7. СОБЫТИЯ ГРУПП ХАРАКТЕРИСТИК
-- ============================================================

CREATE TABLE IF NOT EXISTS attribute_group_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attribute_group_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT attribute_group_event_log_group_fkey 
        FOREIGN KEY (attribute_group_uid) REFERENCES spr_attribute_group(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_attribute_group_event_log_group ON attribute_group_event_log(attribute_group_uid);
CREATE INDEX IF NOT EXISTS idx_attribute_group_event_log_created ON attribute_group_event_log(created_at DESC);

-- ============================================================
-- 8. СОБЫТИЯ ВИДОВ ХАРАКТЕРИСТИК
-- ============================================================

CREATE TABLE IF NOT EXISTS type_attribute_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_attribute_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT type_attribute_event_log_attribute_fkey 
        FOREIGN KEY (type_attribute_uid) REFERENCES spr_type_attributes(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_type_attribute_event_log_attribute ON type_attribute_event_log(type_attribute_uid);
CREATE INDEX IF NOT EXISTS idx_type_attribute_event_log_created ON type_attribute_event_log(created_at DESC);

-- ============================================================
-- 9. СОБЫТИЯ ЕДИНИЦ ИЗМЕРЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS measure_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    measure_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT measure_event_log_measure_fkey 
        FOREIGN KEY (measure_uid) REFERENCES spr_measure(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_measure_event_log_measure ON measure_event_log(measure_uid);
CREATE INDEX IF NOT EXISTS idx_measure_event_log_created ON measure_event_log(created_at DESC);

-- ============================================================
-- 10. СИДЫ: ГРУППЫ ХАРАКТЕРИСТИК
-- ============================================================

INSERT INTO spr_attribute_group (uid, name) VALUES
    (gen_random_uuid(), 'Геометрические'),
    (gen_random_uuid(), 'Физические'),
    (gen_random_uuid(), 'Эксплуатационные')
ON CONFLICT (name) DO NOTHING;

COMMIT;