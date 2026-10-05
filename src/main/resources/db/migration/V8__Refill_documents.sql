-- V8__Refill_documents.sql
-- Документ пополнения: шапка + ячейки (снимок данных из шаблона) + настройки пользователя

BEGIN;

-- ============================================================
-- 1. ШАПКА ДОКУМЕНТА ПОПОЛНЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_refill (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code INTEGER,
    doc_datetime TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    station_uid VARCHAR(50) REFERENCES stations(uid) ON DELETE SET NULL,
    template_uid UUID REFERENCES doc_pattern(uid) ON DELETE SET NULL,
    created_by_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    total_cells INTEGER NOT NULL DEFAULT 0,
    cells_to_load INTEGER NOT NULL DEFAULT 0,
    total_quantity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_refill_code ON doc_refill(code);
CREATE INDEX IF NOT EXISTS idx_doc_refill_datetime ON doc_refill(doc_datetime DESC);
CREATE INDEX IF NOT EXISTS idx_doc_refill_station ON doc_refill(station_uid);
CREATE INDEX IF NOT EXISTS idx_doc_refill_template ON doc_refill(template_uid);
CREATE INDEX IF NOT EXISTS idx_doc_refill_user ON doc_refill(created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_doc_refill_status ON doc_refill(status);

-- ============================================================
-- 2. ЯЧЕЙКИ ДОКУМЕНТА (СНИМОК ИЗ ШАБЛОНА + ПОПОЛНЕНИЕ)
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_refill_cells (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_refill_uid UUID NOT NULL REFERENCES doc_refill(uid) ON DELETE CASCADE,

    -- Адрес ячейки
    drum_number INTEGER,
    column_number INTEGER,
    number_cell INTEGER,

    -- Снимок из шаблона (момент создания документа)
    cell_assignment_uid UUID REFERENCES spr_cell_assignment(uid) ON DELETE SET NULL,
    cell_assignment_name VARCHAR(255),
    material_uid UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    material_name TEXT,
    material_article TEXT,
    template_quantity INTEGER,
    return_to_this_cell BOOLEAN NOT NULL DEFAULT FALSE,
    is_individual BOOLEAN NOT NULL DEFAULT FALSE,

    -- Данные документа
    refill_quantity INTEGER,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(doc_refill_uid, drum_number, column_number, number_cell)
);

CREATE INDEX IF NOT EXISTS idx_refill_cells_doc ON reg_refill_cells(doc_refill_uid);
CREATE INDEX IF NOT EXISTS idx_refill_cells_address
    ON reg_refill_cells(doc_refill_uid, drum_number, column_number, number_cell);

-- ============================================================
-- 3. ИСТОРИЯ ИЗМЕНЕНИЙ ДОКУМЕНТА
-- ============================================================

CREATE TABLE IF NOT EXISTS refill_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_refill_uid UUID REFERENCES doc_refill(uid) ON DELETE CASCADE,
    cell_uid UUID REFERENCES reg_refill_cells(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refill_events_doc ON refill_event_log(doc_refill_uid);
CREATE INDEX IF NOT EXISTS idx_refill_events_cell ON refill_event_log(cell_uid);
CREATE INDEX IF NOT EXISTS idx_refill_events_created ON refill_event_log(created_at DESC);

-- ============================================================
-- 4. НАСТРОЙКИ КОЛОНОК / ФИЛЬТРОВ / СОРТИРОВКИ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_refill_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_refill_settings_user
    ON user_refill_column_settings(user_id);

COMMIT;