-- V9__Refill_snapshot_and_station_cells.sql
-- 1. Регистр фактических ячеек станции (reg_station_cells).
-- 2. Снимок факта в ячейках документа пополнения (fact_quantity).
-- 3. Снимок порогов контроля остатков в ячейках документа.
-- 4. uid-ссылки на конфигурацию и модель в шапке документа.
--
-- В документе пополнения храним только:
--   • uid-ссылки на станцию / шаблон / конфигурацию / модель / автора;
--   • uid-ссылки на назначение ячейки и номенклатуру;
--   • числовые значения ячейки: template_quantity, fact_quantity, refill_quantity;
--   • числовые снимки порогов: min_stock_snapshot, critical_stock_snapshot, stock_control_exists.
-- Всё остальное подтягивается по uid связанных объектов.

BEGIN;

-- ============================================================
-- 1. РЕГИСТР ФАКТИЧЕСКИХ ЯЧЕЕК СТАНЦИИ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_station_cells (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_uid VARCHAR(50) NOT NULL REFERENCES stations(uid) ON DELETE CASCADE,

    -- Адрес ячейки
    number_cell INTEGER,
    column_number INTEGER,
    drum_number INTEGER,

    -- Назначение ячейки
    cell_assignment_uid UUID REFERENCES spr_cell_assignment(uid) ON DELETE SET NULL,

    -- Номенклатура
    material_uid UUID REFERENCES spr_material(uid) ON DELETE SET NULL,

    -- ФАКТ по ячейке
    quantity INTEGER NOT NULL DEFAULT 0,

    -- Флаги
    return_to_this_cell BOOLEAN NOT NULL DEFAULT FALSE,
    is_individual BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (station_uid, drum_number, column_number, number_cell)
);

CREATE INDEX IF NOT EXISTS idx_station_cells_station
    ON reg_station_cells(station_uid);
CREATE INDEX IF NOT EXISTS idx_station_cells_address
    ON reg_station_cells(station_uid, drum_number, column_number, number_cell);
CREATE INDEX IF NOT EXISTS idx_station_cells_material
    ON reg_station_cells(material_uid);
CREATE INDEX IF NOT EXISTS idx_station_cells_assignment
    ON reg_station_cells(cell_assignment_uid);

-- ============================================================
-- 2. ЯЧЕЙКИ ДОКУМЕНТА: ФАКТ + СНИМОК ПОРОГОВ
-- ============================================================

ALTER TABLE reg_refill_cells
    ADD COLUMN IF NOT EXISTS fact_quantity            INTEGER,
    ADD COLUMN IF NOT EXISTS min_stock_snapshot       INTEGER,
    ADD COLUMN IF NOT EXISTS critical_stock_snapshot  INTEGER,
    ADD COLUMN IF NOT EXISTS stock_control_exists     BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN reg_refill_cells.fact_quantity IS
    'Фактическое количество в ячейке станции на момент создания документа (из reg_station_cells)';
COMMENT ON COLUMN reg_refill_cells.min_stock_snapshot IS
    'Минимальный остаток на момент создания документа (из reg_stock_level_control)';
COMMENT ON COLUMN reg_refill_cells.critical_stock_snapshot IS
    'Критический остаток на момент создания документа (из reg_stock_level_control)';
COMMENT ON COLUMN reg_refill_cells.stock_control_exists IS
    'Признак наличия порогов контроля остатков на момент создания документа';

-- ============================================================
-- 3. ШАПКА ДОКУМЕНТА: uid-ССЫЛКИ НА КОНФИГУРАЦИЮ И МОДЕЛЬ
-- ============================================================

ALTER TABLE doc_refill
    ADD COLUMN IF NOT EXISTS configuration_uid UUID,
    ADD COLUMN IF NOT EXISTS model_uid         UUID;

CREATE INDEX IF NOT EXISTS idx_doc_refill_configuration_uid
    ON doc_refill(configuration_uid);
CREATE INDEX IF NOT EXISTS idx_doc_refill_model_uid
    ON doc_refill(model_uid);

COMMIT;