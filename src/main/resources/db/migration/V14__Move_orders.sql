-- V14__Move_orders.sql
-- Заказ на перемещение: шапка + строки + строки перемещений + журнал + настройки.
-- Плюс расширение doc_transfer статусом CLOSED.

BEGIN;

-- ============================================================
-- 1. РАСШИРЕНИЕ doc_transfer — статус CLOSED
-- ============================================================

-- Значения status: 'DRAFT' / 'POSTED' / 'CLOSED'
-- Явного ENUM нет — просто VARCHAR(20). Ничего не меняем структурно.

-- ============================================================
-- 2. ШАПКА ЗАКАЗА НА ПЕРЕМЕЩЕНИЕ
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_move_order (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            INTEGER UNIQUE,
    doc_datetime    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Склад-получатель (uid станции или 'MAIN')
    stock_to_uid    VARCHAR(50),

    -- Крайний срок = MAX(due_date) по строкам (пересчитывается приложением)
    deadline        TIMESTAMP,

    comment         TEXT,

    -- DRAFT / ACCEPTED / POSTED / CANCELLED
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_move_order_code        ON doc_move_order(code);
CREATE INDEX IF NOT EXISTS idx_doc_move_order_datetime    ON doc_move_order(doc_datetime DESC);
CREATE INDEX IF NOT EXISTS idx_doc_move_order_status      ON doc_move_order(status);
CREATE INDEX IF NOT EXISTS idx_doc_move_order_stock_to    ON doc_move_order(stock_to_uid);

-- ============================================================
-- 3. СТРОКИ ЗАКАЗА НА ПЕРЕМЕЩЕНИЕ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_move_order_lines (
    uid                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_uid             UUID NOT NULL REFERENCES doc_move_order(uid) ON DELETE CASCADE,

    -- Номенклатура и количество
    material_uid        UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    quantity            INTEGER NOT NULL DEFAULT 0,

    -- Склад-отправитель (uid станции или 'MAIN')
    stock_from_uid      VARCHAR(50),

    -- Приоритет: HIGH / MEDIUM / LOW
    priority            VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',

    -- Срок исполнения строки
    due_date            TIMESTAMP,

    -- Статус строки: NOT_STARTED / SHIPPING / SHIPPED / CLOSED / CANCELLED
    status              VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',

    -- Даты смены статусов
    shipping_at         TIMESTAMP,
    shipped_at          TIMESTAMP,
    loading_at          TIMESTAMP,
    closed_at           TIMESTAMP,

    -- Отмена строки
    cancelled           BOOLEAN NOT NULL DEFAULT FALSE,

    -- Ссылка на созданный doc_transfer (заполняется при акцепте)
    transfer_uid        UUID REFERENCES doc_transfer(uid) ON DELETE SET NULL,

    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_move_order_lines_doc          ON reg_move_order_lines(doc_uid);
CREATE INDEX IF NOT EXISTS idx_move_order_lines_material     ON reg_move_order_lines(material_uid);
CREATE INDEX IF NOT EXISTS idx_move_order_lines_transfer     ON reg_move_order_lines(transfer_uid);
CREATE INDEX IF NOT EXISTS idx_move_order_lines_status       ON reg_move_order_lines(status);
CREATE INDEX IF NOT EXISTS idx_move_order_lines_stock_from   ON reg_move_order_lines(stock_from_uid);

-- ============================================================
-- 4. ТАБЛИЧНАЯ ЧАСТЬ doc_transfer — НОМЕНКЛАТУРА + КОЛИЧЕСТВО
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_transfer_lines (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_uid    UUID NOT NULL REFERENCES doc_transfer(uid) ON DELETE CASCADE,

    material_uid    UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    quantity        INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_transfer_lines_transfer  ON reg_transfer_lines(transfer_uid);
CREATE INDEX IF NOT EXISTS idx_transfer_lines_material  ON reg_transfer_lines(material_uid);

-- ============================================================
-- 5. ЖУРНАЛ СОБЫТИЙ ЗАКАЗА
-- ============================================================

CREATE TABLE IF NOT EXISTS move_order_event_log (
    uid                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_uid             UUID REFERENCES doc_move_order(uid) ON DELETE SET NULL,
    line_uid            UUID REFERENCES reg_move_order_lines(uid) ON DELETE SET NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_description   TEXT NOT NULL,
    field_name          VARCHAR(255),
    old_value           TEXT,
    new_value           TEXT,
    author              VARCHAR(255),
    source              VARCHAR(100) DEFAULT 'Через карточку',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_move_order_events_doc      ON move_order_event_log(doc_uid);
CREATE INDEX IF NOT EXISTS idx_move_order_events_line     ON move_order_event_log(line_uid);
CREATE INDEX IF NOT EXISTS idx_move_order_events_created  ON move_order_event_log(created_at DESC);

-- ============================================================
-- 6. НАСТРОЙКИ КОЛОНОК / ФИЛЬТРОВ / СОРТИРОВКИ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_move_order_column_settings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_move_order_settings_user
    ON user_move_order_column_settings(user_id);

-- ============================================================
-- 7. ИНДЕКС НА doc_transfer.doc_base_uid (для выборки transfers по заказу)
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_doc_transfer_base_type
    ON doc_transfer(doc_base_type, doc_base_uid);

COMMIT;