-- V25__Transfer_document.sql
-- Документ «Перемещение»: шапка, строки, журнал, настройки.
-- Статусы: DRAFT / IN_PROGRESS / SHIPPING / SHIPPED / RECEIVED / LOADING / POSTED
-- Плюс отдельные колонки agreed и cancelled.

BEGIN;

-- ============================================================
-- 1. ШАПКА doc_transfer — новые поля
-- ============================================================

ALTER TABLE doc_transfer
    ADD COLUMN IF NOT EXISTS stock_from_uid VARCHAR(50),
    ADD COLUMN IF NOT EXISTS priority       VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS due_date       TIMESTAMP,
    ADD COLUMN IF NOT EXISTS operator_uid   UUID,
    ADD COLUMN IF NOT EXISTS agreed         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cancelled      BOOLEAN NOT NULL DEFAULT FALSE;

-- status уже есть (VARCHAR(20) DEFAULT 'DRAFT') — приводим комментарием
COMMENT ON COLUMN doc_transfer.status IS
    'DRAFT / IN_PROGRESS / SHIPPING / SHIPPED / RECEIVED / LOADING / POSTED';
COMMENT ON COLUMN doc_transfer.doc_base_type IS
    'MANUAL / REFILL / MOVE_ORDER';
COMMENT ON COLUMN doc_transfer.agreed IS
    'Согласован (только для base_type = MOVE_ORDER)';
COMMENT ON COLUMN doc_transfer.cancelled IS
    'Отменён (до момента «в работе» для MOVE_ORDER)';
COMMENT ON COLUMN doc_transfer.operator_uid IS
    'Оператор склада-отправителя (заглушка, логика позже)';

CREATE INDEX IF NOT EXISTS idx_doc_transfer_from ON doc_transfer(stock_from_uid);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_agreed ON doc_transfer(agreed);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_cancelled ON doc_transfer(cancelled);

-- ============================================================
-- 2. СТРОКИ reg_transfer_lines — новые поля
-- ============================================================

ALTER TABLE reg_transfer_lines
    ADD COLUMN IF NOT EXISTS cell_uid                  UUID,
    ADD COLUMN IF NOT EXISTS material_name_snapshot    TEXT,
    ADD COLUMN IF NOT EXISTS material_article_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS material_code_snapshot    INTEGER,
    ADD COLUMN IF NOT EXISTS accepted_quantity         INTEGER,
    ADD COLUMN IF NOT EXISTS batch_info                TEXT;

COMMENT ON COLUMN reg_transfer_lines.cell_uid IS
    'Ячейка на складе-отправителе (адрес хранения)';
COMMENT ON COLUMN reg_transfer_lines.batch_info IS
    'Партионность (заглушка, «в разработке»)';
COMMENT ON COLUMN reg_transfer_lines.accepted_quantity IS
    'Количество на момент согласования (для MOVE_ORDER)';

CREATE INDEX IF NOT EXISTS idx_transfer_lines_cell ON reg_transfer_lines(cell_uid);

-- ============================================================
-- 3. ЖУРНАЛ СОБЫТИЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS transfer_event_log (
    uid                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_uid             UUID REFERENCES doc_transfer(uid) ON DELETE SET NULL,
    line_uid            UUID REFERENCES reg_transfer_lines(uid) ON DELETE SET NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_description   TEXT NOT NULL,
    field_name          VARCHAR(255),
    old_value           TEXT,
    new_value           TEXT,
    author              VARCHAR(255),
    source              VARCHAR(100) DEFAULT 'Через карточку',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transfer_events_doc ON transfer_event_log(doc_uid);
CREATE INDEX IF NOT EXISTS idx_transfer_events_line ON transfer_event_log(line_uid);
CREATE INDEX IF NOT EXISTS idx_transfer_events_created ON transfer_event_log(created_at DESC);

-- ============================================================
-- 4. НАСТРОЙКИ КОЛОНОК
-- ============================================================

CREATE TABLE IF NOT EXISTS user_transfer_column_settings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_transfer_settings_user
    ON user_transfer_column_settings(user_id);

COMMIT;