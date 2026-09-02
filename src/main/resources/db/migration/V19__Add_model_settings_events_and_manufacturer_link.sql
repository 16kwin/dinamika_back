-- V19__Add_model_settings_events_and_manufacturer_link.sql

BEGIN;

-- ============================================================
-- 1. ДОБАВЛЕНИЕ MANUFACTURER_UID В МОДЕЛИ БРЕНДОВ
-- ============================================================

ALTER TABLE spr_model_of_brand
    ADD COLUMN IF NOT EXISTS manufacturer_uid UUID REFERENCES spr_manufacturer(uid) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_spr_model_of_brand_manufacturer ON spr_model_of_brand(manufacturer_uid);

-- ============================================================
-- 2. НАСТРОЙКИ КОЛОНОК МОДЕЛЕЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_model_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_model_settings_user ON user_model_column_settings(user_id);

-- ============================================================
-- 3. СОБЫТИЯ МОДЕЛЕЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS model_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT model_event_log_model_fkey 
        FOREIGN KEY (model_uid) REFERENCES spr_model_of_brand(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_model_event_log_model ON model_event_log(model_uid);
CREATE INDEX IF NOT EXISTS idx_model_event_log_created ON model_event_log(created_at DESC);

COMMIT;