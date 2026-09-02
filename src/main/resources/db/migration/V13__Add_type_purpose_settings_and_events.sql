-- V13__Add_type_purpose_settings_and_events.sql

BEGIN;

-- ============================================================
-- 1. НАСТРОЙКИ КОЛОНОК ГРУПП НОМЕНКЛАТУРЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_type_purpose_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_type_purpose_settings_user ON user_type_purpose_column_settings(user_id);

-- ============================================================
-- 2. СОБЫТИЯ ГРУПП НОМЕНКЛАТУРЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS type_purpose_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_purpose_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT type_purpose_event_log_purpose_fkey 
        FOREIGN KEY (type_purpose_uid) REFERENCES spr_type_purpose(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_type_purpose_event_log_purpose ON type_purpose_event_log(type_purpose_uid);
CREATE INDEX IF NOT EXISTS idx_type_purpose_event_log_created ON type_purpose_event_log(created_at DESC);

COMMIT;