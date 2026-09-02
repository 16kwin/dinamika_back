-- V9__Add_nomenclature_column_settings.sql

BEGIN;

-- ============================================================
-- НАСТРОЙКИ КОЛОНОК НОМЕНКЛАТУРЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_nomenclature_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    current_path_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_nomenclature_settings_user ON user_nomenclature_column_settings(user_id);

COMMIT;