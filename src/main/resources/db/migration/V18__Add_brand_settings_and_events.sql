-- V18__Add_brand_settings_and_events.sql

BEGIN;

-- ============================================================
-- 1. НАСТРОЙКИ КОЛОНОК БРЕНДОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_brand_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_brand_settings_user ON user_brand_column_settings(user_id);

-- ============================================================
-- 2. СОБЫТИЯ БРЕНДОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS brand_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT brand_event_log_brand_fkey 
        FOREIGN KEY (brand_uid) REFERENCES spr_brand(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_brand_event_log_brand ON brand_event_log(brand_uid);
CREATE INDEX IF NOT EXISTS idx_brand_event_log_created ON brand_event_log(created_at DESC);

-- ============================================================
-- 3. СИДЫ: БРЕНДЫ (существующие уже есть из V1)
-- ============================================================

COMMIT;