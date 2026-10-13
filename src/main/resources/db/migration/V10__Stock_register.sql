-- V10__Stock_register.sql
-- Регистр основного склада: чистый остаток + бронь под документы пополнения.
-- Состоит из двух регистровых таблиц и журнала событий склада.
--
-- 1) reg_stock_balance       — сводный регистр: одна запись на (номенклатура + склад).
--                              Чистый остаток (не забронировано) + общая бронь.
-- 2) reg_stock_reservation   — детальный регистр: одна запись на бронь.
--                              Регистратор (uid документа пополнения) + номенклатура + склад.
--                              Флаг blocked = false (активна) / true (закрыта при проведении).
-- 3) stock_event_log         — журнал изменений регистра склада.

BEGIN;

-- ============================================================
-- 1. СВОДНЫЙ РЕГИСТР ОСТАТКОВ СКЛАДА
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_stock_balance (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Номенклатура
    material_uid    UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,

    -- Склад: пока всегда основной. Здесь либо строка 'MAIN', либо uid станции.
    -- Храним как VARCHAR чтобы не заводить отдельную таблицу складов сейчас.
    stock_uid       VARCHAR(50) NOT NULL DEFAULT 'MAIN',

    -- Чистый остаток (не забронировано)
    clean_quantity  INTEGER NOT NULL DEFAULT 0,

    -- Общая бронь по этой номенклатуре на этом складе
    reserved_quantity INTEGER NOT NULL DEFAULT 0,

    -- Периодический итог (пока пустой, оставлен под будущее)
    period_total    INTEGER,

    -- Служебные
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (material_uid, stock_uid)
);

CREATE INDEX IF NOT EXISTS idx_stock_balance_material ON reg_stock_balance(material_uid);
CREATE INDEX IF NOT EXISTS idx_stock_balance_stock    ON reg_stock_balance(stock_uid);

-- ============================================================
-- 2. ДЕТАЛЬНЫЙ РЕГИСТР БРОНЕЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_stock_reservation (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Регистратор — документ пополнения, под который забронировано
    doc_refill_uid  UUID REFERENCES doc_refill(uid) ON DELETE CASCADE,

    -- Номенклатура
    material_uid    UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,

    -- Склад: 'MAIN' или uid станции
    stock_uid       VARCHAR(50) NOT NULL DEFAULT 'MAIN',

    -- Забронированное количество по этой записи
    reserved_quantity INTEGER NOT NULL DEFAULT 0,

    -- Заблокировано: false — бронь активна (документ не проведён),
    --                true  — бронь закрыта (документ проведён или удалён).
    blocked         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Служебные
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_res_doc      ON reg_stock_reservation(doc_refill_uid);
CREATE INDEX IF NOT EXISTS idx_stock_res_material ON reg_stock_reservation(material_uid);
CREATE INDEX IF NOT EXISTS idx_stock_res_stock    ON reg_stock_reservation(stock_uid);
CREATE INDEX IF NOT EXISTS idx_stock_res_blocked  ON reg_stock_reservation(blocked);

-- ============================================================
-- 3. ЖУРНАЛ СОБЫТИЙ РЕГИСТРА СКЛАДА
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_event_log (
    uid              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- К чему относится событие (может быть null, если запись удалена)
    balance_uid      UUID REFERENCES reg_stock_balance(uid) ON DELETE SET NULL,
    reservation_uid  UUID REFERENCES reg_stock_reservation(uid) ON DELETE SET NULL,
    material_uid     UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    doc_refill_uid   UUID REFERENCES doc_refill(uid) ON DELETE SET NULL,

    event_type       VARCHAR(50)  NOT NULL,
    event_description TEXT        NOT NULL,
    field_name       VARCHAR(255),
    old_value        TEXT,
    new_value        TEXT,
    author           VARCHAR(255),
    source           VARCHAR(100) DEFAULT 'Через карточку',

    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_event_material  ON stock_event_log(material_uid);
CREATE INDEX IF NOT EXISTS idx_stock_event_doc       ON stock_event_log(doc_refill_uid);
CREATE INDEX IF NOT EXISTS idx_stock_event_created   ON stock_event_log(created_at DESC);

COMMIT;