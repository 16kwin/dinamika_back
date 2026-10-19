-- V17__Gpp.sql
-- ГПП — «График подготовки производства (ГПП по предприятию)»: шапка документа, строки-дерево
-- (изделие → детали → материалы) и зависимости строк для диаграммы Ганта.
--
-- Документ один на предприятие и создаётся этой миграцией. Сохранение — полная замена строк
-- в одной транзакции; version — оптимистическая блокировка: сохранение с устаревшей версией получает 409.
-- Даты считает фронтенд (режимы GENERAL / SEQUENTIAL, автоматический расчёт), база их только хранит.
-- Длительность — календарные дни включительно: finish = start + duration_days - 1,
-- преемник по зависимости начинается на следующий день после окончания предшественника.

CREATE TABLE IF NOT EXISTS doc_gpp (
    uid         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    -- GENERAL — «Общий по предприятию», SEQUENTIAL — «Последовательный»
    gpp_type    VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    auto_calc   BOOLEAN      NOT NULL DEFAULT TRUE,
    -- «Период выборки» — диапазон шкалы диаграммы
    period_from DATE         NOT NULL,
    period_to   DATE         NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    -- ФИО (или логин) последнего сохранившего; NULL — документ ещё не сохраняли
    updated_by  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT doc_gpp_type_chk   CHECK (gpp_type IN ('GENERAL', 'SEQUENTIAL')),
    CONSTRAINT doc_gpp_period_chk CHECK (period_to >= period_from)
);

-- Строка графика. uid генерирует клиент (crypto.randomUUID) — по нему же ссылаются зависимости.
-- Порядок вывода — обход дерева: родитель, затем его дети по position.
CREATE TABLE IF NOT EXISTS reg_gpp_rows (
    uid           UUID          PRIMARY KEY,
    doc_gpp_uid   UUID          NOT NULL REFERENCES doc_gpp(uid) ON DELETE CASCADE,
    parent_uid    UUID          REFERENCES reg_gpp_rows(uid) ON DELETE CASCADE,
    -- Порядок среди братьев, с 0
    position      INTEGER       NOT NULL DEFAULT 0,
    -- «000» — собственная операция изделия, «001»… — детали
    op_number     VARCHAR(10),
    -- Децимальный номер / артикул; на экране выводится перед наименованием через пробел
    code          VARCHAR(100),
    name          VARCHAR(500)  NOT NULL,
    -- PRODUCT — изделие (только корень), PART — деталь, MATERIAL — материал/инструмент (без дат и детей)
    kind          VARCHAR(10)   NOT NULL,
    quantity      NUMERIC(15,3) NOT NULL DEFAULT 0,
    unit          VARCHAR(20)   NOT NULL DEFAULT 'шт',
    -- У изделия start — корень плана; у материала дат нет
    start_date    DATE,
    finish_date   DATE,
    duration_days INTEGER,
    -- Цвет полосы диаграммы '#RRGGBB'
    color         VARCHAR(7),
    -- Узел свёрнут при открытии документа
    collapsed     BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT reg_gpp_rows_kind_chk     CHECK (kind IN ('PRODUCT', 'PART', 'MATERIAL')),
    CONSTRAINT reg_gpp_rows_position_chk CHECK (position >= 0),
    CONSTRAINT reg_gpp_rows_quantity_chk CHECK (quantity >= 0),
    CONSTRAINT reg_gpp_rows_duration_chk CHECK (duration_days BETWEEN 1 AND 3650),
    CONSTRAINT reg_gpp_rows_color_chk    CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT reg_gpp_rows_dates_chk    CHECK (start_date IS NULL OR finish_date IS NULL OR finish_date >= start_date),
    CONSTRAINT reg_gpp_rows_parent_chk   CHECK (parent_uid IS NULL OR parent_uid <> uid),
    CONSTRAINT reg_gpp_rows_product_chk  CHECK (kind <> 'PRODUCT' OR parent_uid IS NULL),
    CONSTRAINT reg_gpp_rows_material_chk CHECK (
        kind <> 'MATERIAL' OR (start_date IS NULL AND finish_date IS NULL AND duration_days IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_reg_gpp_rows_doc    ON reg_gpp_rows (doc_gpp_uid, parent_uid, position);
CREATE INDEX IF NOT EXISTS idx_reg_gpp_rows_parent ON reg_gpp_rows (parent_uid);

-- Зависимость «окончание → начало»: строка row_uid начинается после окончания depends_on_uid.
-- position — порядок предшественников строки, как их прислал клиент.
CREATE TABLE IF NOT EXISTS reg_gpp_row_dependencies (
    row_uid        UUID    NOT NULL REFERENCES reg_gpp_rows(uid) ON DELETE CASCADE,
    depends_on_uid UUID    NOT NULL REFERENCES reg_gpp_rows(uid) ON DELETE CASCADE,
    position       INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (row_uid, depends_on_uid),
    CONSTRAINT reg_gpp_row_dependencies_self_chk CHECK (row_uid <> depends_on_uid)
);

CREATE INDEX IF NOT EXISTS idx_reg_gpp_row_dependencies_dep ON reg_gpp_row_dependencies (depends_on_uid);

-- ============================================================
-- Документ предприятия с примером плана на январь–июль 2027.
-- Даты согласованы с режимом GENERAL: детали стартуют от даты изделия и ждут предшественника
-- по цепочке, полоса изделия — от самого раннего начала деталей до самого позднего окончания,
-- duration_days изделия — его собственная операция «000» (нужна последовательному режиму).
-- ============================================================

DO $$
DECLARE
    v_doc   UUID := gen_random_uuid();
    -- Изделие 1 — как на референсе
    v_p1    UUID := gen_random_uuid();
    v_p1_d1 UUID := gen_random_uuid();
    v_p1_d2 UUID := gen_random_uuid();
    v_p1_d3 UUID := gen_random_uuid();
    -- Изделие 2
    v_p2    UUID := gen_random_uuid();
    v_p2_d1 UUID := gen_random_uuid();
    v_p2_d2 UUID := gen_random_uuid();
    v_p2_d3 UUID := gen_random_uuid();
    -- Изделие 3
    v_p3    UUID := gen_random_uuid();
    v_p3_d1 UUID := gen_random_uuid();
    v_p3_d2 UUID := gen_random_uuid();
    v_p3_d3 UUID := gen_random_uuid();
BEGIN
    INSERT INTO doc_gpp (uid, title, gpp_type, auto_calc, period_from, period_to, version)
    VALUES (v_doc, 'График подготовки производства (ГПП по предприятию)', 'GENERAL', TRUE,
            DATE '2027-01-01', DATE '2027-07-31', 1);

    INSERT INTO reg_gpp_rows (uid, doc_gpp_uid, parent_uid, position, op_number, code, name, kind,
                              quantity, unit, start_date, finish_date, duration_days, color, collapsed)
    VALUES
        -- Изделие 1: 001 (30 дн.) → 002 (35 дн.) → 003 (40 дн.), собственная операция 000 — 20 дн.
        (v_p1, v_doc, NULL, 0, '000', 'Р03.200.000.12', 'Вал приводной в сборе', 'PRODUCT',
         100, 'шт', DATE '2027-01-04', DATE '2027-04-18', 20, '#BFE3FF', FALSE),
        (v_p1_d1, v_doc, v_p1, 0, '001', 'Р03.200.000.12.01', 'Вал привода', 'PART',
         100, 'шт', DATE '2027-01-04', DATE '2027-02-02', 30, '#FFB3B3', FALSE),
        (gen_random_uuid(), v_doc, v_p1_d1, 0, NULL, 'CTJ66', 'Пластина резьбовая сменная твердосплавная', 'MATERIAL',
         140, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p1_d1, 1, NULL, 'AOKT060208PEER-VM-HR5120', 'Пластина фрезерная', 'MATERIAL',
         80, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (v_p1_d2, v_doc, v_p1, 1, '002', 'Р03.200.000.12.02', 'Корпус наружного шарнира', 'PART',
         200, 'шт', DATE '2027-02-03', DATE '2027-03-09', 35, '#FFE08A', TRUE),
        (gen_random_uuid(), v_doc, v_p1_d2, 0, NULL, 'DCMT11T304-PM', 'Пластина токарная', 'MATERIAL',
         120, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p1_d2, 1, NULL, 'R390-11T308M-PM', 'Пластина фрезерная', 'MATERIAL',
         60, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (v_p1_d3, v_doc, v_p1, 2, '003', 'Р03.200.000.12.03', 'Сепаратор', 'PART',
         200, 'шт', DATE '2027-03-10', DATE '2027-04-18', 40, '#A8EFC0', FALSE),
        (gen_random_uuid(), v_doc, v_p1_d3, 0, NULL, 'CKJNL2525M16', 'Державка проходная для наружного точения', 'MATERIAL',
         64, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p1_d3, 1, NULL, 'BD03CA0320-HF35', 'Сверло твердосплавное по металлу 3,2 мм с каналами для подвода СОЖ', 'MATERIAL',
         42, 'шт', NULL, NULL, NULL, NULL, FALSE),

        -- Изделие 2: 001 (25 дн.) → 002 (30 дн.) → 003 (20 дн.), собственная операция — 15 дн.
        (v_p2, v_doc, NULL, 1, '000', 'Р03.300.000.05', 'Редуктор червячный одноступенчатый', 'PRODUCT',
         50, 'шт', DATE '2027-02-01', DATE '2027-04-16', 15, '#BFE3FF', FALSE),
        (v_p2_d1, v_doc, v_p2, 0, '001', 'Р03.300.000.05.01', 'Корпус редуктора', 'PART',
         50, 'шт', DATE '2027-02-01', DATE '2027-02-25', 25, '#D9C8FF', FALSE),
        (gen_random_uuid(), v_doc, v_p2_d1, 0, NULL, 'R245-12T3M-PM', 'Пластина фрезерная', 'MATERIAL',
         90, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p2_d1, 1, NULL, 'SD203A-8.5-50-12R1', 'Сверло спиральное с внутренним подводом СОЖ 8,5 мм', 'MATERIAL',
         24, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p2_d1, 2, NULL, 'EX-SFT-M10X1.5', 'Метчик машинный М10×1,5 для глухих отверстий', 'MATERIAL',
         30, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (v_p2_d2, v_doc, v_p2, 1, '002', 'Р03.300.000.05.02', 'Вал червячный', 'PART',
         50, 'шт', DATE '2027-02-26', DATE '2027-03-27', 30, '#FFB3B3', FALSE),
        (gen_random_uuid(), v_doc, v_p2_d2, 0, NULL, 'CNMG120408-PM', 'Пластина токарная', 'MATERIAL',
         110, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p2_d2, 1, NULL, '16ER-AG60', 'Пластина резьбонарезная', 'MATERIAL',
         40, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (v_p2_d3, v_doc, v_p2, 2, '003', 'Р03.300.000.05.03', 'Колесо червячное', 'PART',
         50, 'шт', DATE '2027-03-28', DATE '2027-04-16', 20, '#FFE08A', FALSE),
        (gen_random_uuid(), v_doc, v_p2_d3, 0, NULL, 'ФЧ-m3-A', 'Фреза червячная модульная m3, класс точности A', 'MATERIAL',
         6, 'шт', NULL, NULL, NULL, NULL, FALSE),

        -- Изделие 3: 001 (28 дн.) → 002 (21 дн.) → 003 (18 дн.), собственная операция — 10 дн.
        (v_p3, v_doc, NULL, 2, '000', 'Р03.400.000.08', 'Насос шестерённый', 'PRODUCT',
         80, 'шт', DATE '2027-04-05', DATE '2027-06-10', 10, '#BFE3FF', FALSE),
        (v_p3_d1, v_doc, v_p3, 0, '001', 'Р03.400.000.08.01', 'Корпус насоса', 'PART',
         80, 'шт', DATE '2027-04-05', DATE '2027-05-02', 28, '#A8EFC0', FALSE),
        (gen_random_uuid(), v_doc, v_p3_d1, 0, NULL, 'APMT1135PDER-M2', 'Пластина фрезерная', 'MATERIAL',
         100, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p3_d1, 1, NULL, 'TNMG160408-MF', 'Пластина токарная', 'MATERIAL',
         70, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (v_p3_d2, v_doc, v_p3, 1, '002', 'Р03.400.000.08.02', 'Шестерня ведущая', 'PART',
         80, 'шт', DATE '2027-05-03', DATE '2027-05-23', 21, '#D9C8FF', FALSE),
        (gen_random_uuid(), v_doc, v_p3_d2, 0, NULL, 'DNMG150608-PM', 'Пластина токарная', 'MATERIAL',
         75, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (gen_random_uuid(), v_doc, v_p3_d2, 1, NULL, 'ФЧ-m2-AA', 'Фреза червячная модульная m2, класс точности AA', 'MATERIAL',
         4, 'шт', NULL, NULL, NULL, NULL, FALSE),
        (v_p3_d3, v_doc, v_p3, 2, '003', 'Р03.400.000.08.03', 'Шестерня ведомая', 'PART',
         80, 'шт', DATE '2027-05-24', DATE '2027-06-10', 18, '#FFB3B3', FALSE),
        (gen_random_uuid(), v_doc, v_p3_d3, 0, NULL, 'VNMG160408-MF', 'Пластина токарная', 'MATERIAL',
         60, 'шт', NULL, NULL, NULL, NULL, FALSE);

    -- Детали каждого изделия идут по цепочке «окончание → начало»
    INSERT INTO reg_gpp_row_dependencies (row_uid, depends_on_uid, position)
    VALUES
        (v_p1_d2, v_p1_d1, 0),
        (v_p1_d3, v_p1_d2, 0),
        (v_p2_d2, v_p2_d1, 0),
        (v_p2_d3, v_p2_d2, 0),
        (v_p3_d2, v_p3_d1, 0),
        (v_p3_d3, v_p3_d2, 0);
END $$;
