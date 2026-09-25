-- V2__Add_stock_level_control.sql

BEGIN;

-- ============================================================
-- ЖУРНАЛ ДОКУМЕНТОВ: КОНТРОЛЬ УРОВНЯ ОСТАТКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_stock_level_control (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code INTEGER,
    doc_date DATE NOT NULL DEFAULT CURRENT_DATE,
    station_uid VARCHAR(50) REFERENCES stations(uid) ON DELETE SET NULL,
    is_posted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_stock_level_control_code ON doc_stock_level_control(code);
CREATE INDEX IF NOT EXISTS idx_doc_stock_level_control_date ON doc_stock_level_control(doc_date DESC);
CREATE INDEX IF NOT EXISTS idx_doc_stock_level_control_station ON doc_stock_level_control(station_uid);
CREATE INDEX IF NOT EXISTS idx_doc_stock_level_control_posted ON doc_stock_level_control(is_posted);

-- ============================================================
-- ПРИВЯЗКИ (ТАБЛИЧНАЯ ЧАСТЬ ДОКУМЕНТА)
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_stock_level_control_bindings (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_uid UUID NOT NULL REFERENCES doc_stock_level_control(uid) ON DELETE CASCADE,
    material_uid UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    binding_date DATE,
    min_stock INTEGER,
    critical_stock INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_bindings_doc ON reg_stock_level_control_bindings(doc_uid);
CREATE INDEX IF NOT EXISTS idx_stock_bindings_material ON reg_stock_level_control_bindings(material_uid);

-- ============================================================
-- РЕГИСТР (ТЕКУЩЕЕ СОСТОЯНИЕ ПО ПАРЕ СТАНЦИЯ+НОМЕНКЛАТУРА)
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_stock_level_control (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_uid VARCHAR(50) NOT NULL REFERENCES stations(uid) ON DELETE CASCADE,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    min_stock INTEGER,
    critical_stock INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(station_uid, material_uid)
);

CREATE INDEX IF NOT EXISTS idx_stock_reg_station ON reg_stock_level_control(station_uid);
CREATE INDEX IF NOT EXISTS idx_stock_reg_material ON reg_stock_level_control(material_uid);

-- ============================================================
-- ЖУРНАЛ СОБЫТИЙ ДОКУМЕНТА
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_level_control_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_uid UUID REFERENCES doc_stock_level_control(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_events_doc ON stock_level_control_event_log(doc_uid);
CREATE INDEX IF NOT EXISTS idx_stock_events_created ON stock_level_control_event_log(created_at DESC);

-- ============================================================
-- НАСТРОЙКИ КОЛОНОК, ФИЛЬТРОВ, СОРТИРОВКИ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_stock_level_control_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_stock_level_control_settings_user
    ON user_stock_level_control_column_settings(user_id);

COMMIT;