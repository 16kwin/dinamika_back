-- V12__Add_type_material_settings_and_events.sql

BEGIN;

-- ============================================================
-- 1. НАСТРОЙКИ КОЛОНОК ГРУПП УЧЕТА
-- ============================================================

CREATE TABLE IF NOT EXISTS user_type_material_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_type_material_settings_user ON user_type_material_column_settings(user_id);

-- ============================================================
-- 2. СОБЫТИЯ ГРУПП УЧЕТА
-- ============================================================

CREATE TABLE IF NOT EXISTS type_material_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_material_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT type_material_event_log_material_fkey 
        FOREIGN KEY (type_material_uid) REFERENCES spr_type_material(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_type_material_event_log_material ON type_material_event_log(type_material_uid);
CREATE INDEX IF NOT EXISTS idx_type_material_event_log_created ON type_material_event_log(created_at DESC);

COMMIT;