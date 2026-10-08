-- V12__Stock_movement_and_transfer_docs.sql
-- 1. Снимаем UNIQUE с reg_stock_balance: теперь это журнал, где каждое движение — новая строка,
--    а актуальное состояние = последняя строка по паре (material_uid, stock_uid).
-- 2. Новый документ перемещения doc_transfer.
-- 3. Новый регистр движений по документам stock_movement.

BEGIN;

-- ============================================================
-- 1. reg_stock_balance: убираем UNIQUE, добавляем индекс на "последнюю"
-- ============================================================

ALTER TABLE reg_stock_balance
    DROP CONSTRAINT IF EXISTS reg_stock_balance_material_uid_stock_uid_key;

CREATE INDEX IF NOT EXISTS idx_stock_balance_material_stock_created
    ON reg_stock_balance (material_uid, stock_uid, created_at DESC, uid DESC);

-- ============================================================
-- 2. ДОКУМЕНТ ПЕРЕМЕЩЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_transfer (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            INTEGER,
    doc_datetime    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Документ-основание (например, документ пополнения)
    doc_base_uid    UUID,
    doc_base_type   VARCHAR(50),          -- 'REFILL' / 'TRANSFER'

    -- Склад-источник и склад-приёмник
    stock_from_uid  VARCHAR(50),          -- 'MAIN' или uid станции
    stock_to_uid    VARCHAR(50),          -- 'MAIN' или uid станции

    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT / POSTED

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_transfer_code         ON doc_transfer(code);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_datetime     ON doc_transfer(doc_datetime DESC);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_base         ON doc_transfer(doc_base_uid);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_status       ON doc_transfer(status);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_from         ON doc_transfer(stock_from_uid);
CREATE INDEX IF NOT EXISTS idx_doc_transfer_to           ON doc_transfer(stock_to_uid);

-- ============================================================
-- 3. РЕГИСТР ДВИЖЕНИЙ ПО ДОКУМЕНТАМ
-- ============================================================

CREATE TABLE IF NOT EXISTS stock_movement (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Сам документ, к которому относится движение
    doc_uid         UUID,
    doc_type        VARCHAR(50) NOT NULL,        -- 'REFILL' / 'TRANSFER'
    doc_code        INTEGER,

    -- Документ-основание
    doc_base_uid    UUID,
    doc_base_type   VARCHAR(50),                 -- 'REFILL' / 'TRANSFER' / NULL

    -- Склад и номенклатура
    stock_uid       VARCHAR(50) NOT NULL,        -- 'MAIN' или uid станции
    material_uid    UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,

    -- Количество со знаком: + приход, − уход
    quantity        INTEGER NOT NULL,

    -- Момент движения (записи/проведения)
    period          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Признаки (пока null — заполним позже)
    is_reusable     BOOLEAN,
    is_resharpen    BOOLEAN,
    is_scrap        BOOLEAN,

    -- Состояние записи
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT / POSTED

    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_movement_doc         ON stock_movement(doc_uid);
CREATE INDEX IF NOT EXISTS idx_stock_movement_doc_type    ON stock_movement(doc_type);
CREATE INDEX IF NOT EXISTS idx_stock_movement_base        ON stock_movement(doc_base_uid);
CREATE INDEX IF NOT EXISTS idx_stock_movement_stock       ON stock_movement(stock_uid);
CREATE INDEX IF NOT EXISTS idx_stock_movement_material    ON stock_movement(material_uid);
CREATE INDEX IF NOT EXISTS idx_stock_movement_period      ON stock_movement(period DESC);
CREATE INDEX IF NOT EXISTS idx_stock_movement_status      ON stock_movement(status);
CREATE INDEX IF NOT EXISTS idx_stock_movement_stock_mat   ON stock_movement(stock_uid, material_uid, period DESC);

COMMIT;