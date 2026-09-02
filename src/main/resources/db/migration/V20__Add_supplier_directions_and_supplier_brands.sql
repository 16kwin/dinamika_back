-- V20__Add_supplier_directions_and_supplier_brands.sql

BEGIN;

-- ============================================================
-- 1. БРЕНДЫ ПОСТАВЩИКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_supplier_brand (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    supplier_uid UUID REFERENCES spr_suppliers(uid) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, supplier_uid)
);

CREATE INDEX IF NOT EXISTS idx_spr_supplier_brand_supplier ON spr_supplier_brand(supplier_uid);

-- ============================================================
-- 2. УДАЛЕНИЕ BRAND_UID ИЗ ПОСТАВЩИКОВ
-- ============================================================

ALTER TABLE spr_suppliers
    DROP COLUMN IF EXISTS brand_uid;

-- ============================================================
-- 3. НАСТРОЙКИ КОЛОНОК НАПРАВЛЕНИЙ ПОСТАВЩИКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_supplier_direction_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_supplier_direction_settings_user ON user_supplier_direction_column_settings(user_id);

-- ============================================================
-- 4. НАСТРОЙКИ КОЛОНОК БРЕНДОВ ПОСТАВЩИКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_supplier_brand_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_supplier_brand_settings_user ON user_supplier_brand_column_settings(user_id);

-- ============================================================
-- 5. СОБЫТИЯ НАПРАВЛЕНИЙ ПОСТАВЩИКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS supplier_direction_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_direction_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT supplier_direction_event_log_direction_fkey 
        FOREIGN KEY (supplier_direction_uid) REFERENCES spr_supplier_description_types(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_supplier_direction_event_log_direction ON supplier_direction_event_log(supplier_direction_uid);
CREATE INDEX IF NOT EXISTS idx_supplier_direction_event_log_created ON supplier_direction_event_log(created_at DESC);

-- ============================================================
-- 6. СОБЫТИЯ БРЕНДОВ ПОСТАВЩИКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS supplier_brand_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_brand_uid UUID,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT supplier_brand_event_log_brand_fkey 
        FOREIGN KEY (supplier_brand_uid) REFERENCES spr_supplier_brand(uid) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_supplier_brand_event_log_brand ON supplier_brand_event_log(supplier_brand_uid);
CREATE INDEX IF NOT EXISTS idx_supplier_brand_event_log_created ON supplier_brand_event_log(created_at DESC);

-- ============================================================
-- 7. СИДЫ: БРЕНДЫ ПОСТАВЩИКОВ (пусто, заполняется пользователем)
-- ============================================================

COMMIT;