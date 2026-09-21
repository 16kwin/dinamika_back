-- V5__Dashboard_quality_operator.sql
-- Панели «Показатели» (топ-менеджмент) и «Оператор склада»: структура и тестовые данные.
--
-- Источник значений — файл «Синтетика.xlsx» (листы «Расход объема», «Уровень брака»,
-- «Показатели качества», «Производство», «Инфопанели», «КО и МО»).
-- Как и в V4, дневные ряды разложены так, что сумма за 01.01.2025–30.11.2025 совпадает с Excel
-- до последнего знака: дневное значение = round(T * w_d / W, 4), остаток округления уходит
-- на 30.11.2025. Декабрь генерируется от 8% суммы, чтобы диапазоны, выходящие за ноябрь, не рвались.
-- Позже эти таблицы заменяются реальными источниками (выпуск с производства, ОТК, остатки станций).

-- ==================== Структура ====================

-- График «Расход объема производственной номенклатуры по предприятию»: план/факт расхода ТМЦ за день
CREATE TABLE IF NOT EXISTS dash_production_daily (
    stat_date DATE PRIMARY KEY,
    plan_qty  NUMERIC(16,4) NOT NULL,
    fact_qty  NUMERIC(16,4) NOT NULL
);

-- Субъект графика «Уровень брака»: деталь (view_kind='part') или подразделение (view_kind='workshop')
CREATE TABLE IF NOT EXISTS dash_defect_subject (
    view_kind   VARCHAR(16)  NOT NULL,
    subject_key VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL,
    PRIMARY KEY (view_kind, subject_key)
);

-- Элемент раскрытия субъекта (строка pop-up): для деталей — подразделения, для подразделений — номенклатура
CREATE TABLE IF NOT EXISTS dash_defect_item (
    view_kind  VARCHAR(16)  NOT NULL,
    item_key   VARCHAR(64)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    sort_order INTEGER      NOT NULL,
    PRIMARY KEY (view_kind, item_key)
);

-- Факт выпуска и брака за день в разрезе субъект × элемент раскрытия
CREATE TABLE IF NOT EXISTS dash_defect_daily (
    id           BIGSERIAL PRIMARY KEY,
    stat_date    DATE        NOT NULL,
    view_kind    VARCHAR(16) NOT NULL,
    subject_key  VARCHAR(64) NOT NULL,
    item_key     VARCHAR(64) NOT NULL,
    released_qty NUMERIC(14,4) NOT NULL,
    defect_qty   NUMERIC(14,4) NOT NULL,
    UNIQUE (stat_date, view_kind, subject_key, item_key),
    FOREIGN KEY (view_kind, subject_key) REFERENCES dash_defect_subject(view_kind, subject_key),
    FOREIGN KEY (view_kind, item_key)    REFERENCES dash_defect_item(view_kind, item_key)
);

-- Карточка «Показатели качества»: годный выпуск и брак за день (всего выпуск = сумма обоих)
CREATE TABLE IF NOT EXISTS dash_quality_daily (
    stat_date    DATE PRIMARY KEY,
    released_qty NUMERIC(14,4) NOT NULL,
    defect_qty   NUMERIC(14,4) NOT NULL
);

-- Карточка «Производство»: прохождение контроля качества за день
CREATE TABLE IF NOT EXISTS dash_qc_daily (
    stat_date   DATE PRIMARY KEY,
    passed_qty  NUMERIC(14,4) NOT NULL,
    waiting_qty NUMERIC(14,4) NOT NULL,
    failed_qty  NUMERIC(14,4) NOT NULL
);

-- Лента «Выпуск продукции» справа на панели «Показатели» (последние события, вне диапазона дат)
CREATE TABLE IF NOT EXISTS dash_release_event (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    event_at TIMESTAMP    NOT NULL
);

-- Показатели панели оператора склада на дату (ТМЦ в станциях, выдача, детали на СГД)
CREATE TABLE IF NOT EXISTS dash_operator_metric_daily (
    stat_date        DATE PRIMARY KEY,
    tmc_in_stations  INTEGER NOT NULL,
    tmc_capacity     INTEGER NOT NULL,
    issued_tmc       INTEGER NOT NULL,
    issued_over_norm INTEGER NOT NULL,
    sgd_parts        INTEGER NOT NULL,
    sgd_capacity     INTEGER NOT NULL
);

-- Станция (склад) для графика критических и минимальных остатков
CREATE TABLE IF NOT EXISTS dash_station_balance (
    station_key VARCHAR(64)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    sort_order  INTEGER      NOT NULL
);

-- Номенклатура станции с остатком и порогами. Статус строки считается из остатка:
-- quantity <= critical_level → КО (критический остаток), иначе quantity <= min_level → МО (минимальный).
-- На графике станция показывается по номенклатуре с наименьшим остатком, остальные — в pop-up.
CREATE TABLE IF NOT EXISTS dash_station_balance_item (
    id             BIGSERIAL PRIMARY KEY,
    station_key    VARCHAR(64)  NOT NULL REFERENCES dash_station_balance(station_key),
    nom_name       VARCHAR(255) NOT NULL,
    quantity       INTEGER      NOT NULL,
    min_level      INTEGER      NOT NULL,
    critical_level INTEGER      NOT NULL,
    sort_order     INTEGER      NOT NULL
);

-- Ленты панели оператора: kind='order' — «Заказы на поставку», kind='event' — «Экран событий»
CREATE TABLE IF NOT EXISTS dash_operator_event (
    id       BIGSERIAL PRIMARY KEY,
    kind     VARCHAR(16)  NOT NULL,
    title    VARCHAR(255) NOT NULL,
    event_at TIMESTAMP    NOT NULL,
    done     BOOLEAN      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dash_defect_daily_date ON dash_defect_daily(stat_date);
CREATE INDEX IF NOT EXISTS idx_dash_defect_daily_subject ON dash_defect_daily(view_kind, subject_key);
CREATE INDEX IF NOT EXISTS idx_dash_station_balance_item_station ON dash_station_balance_item(station_key);

-- ==================== Сиды ====================

-- Расход объема номенклатуры: опорные значения на 1-е число месяца (лист «Расход объема»),
-- между ними косинусная интерполяция v = a_m + (a_{m+1} - a_m) * (1 - cos(pi * t)) / 2,
-- t = (день - 1) / дней_в_месяце. Для декабря a_13 = a_12 — линия выходит на полку.
-- Это и есть «промежуточные значения» из примечания к листу: по точке на каждый день.
WITH anchors AS (
    SELECT m, plan, fact FROM (VALUES
        (1, 4600, 2600), (2, 3800, 2900), (3, 3100, 6100), (4, 4500, 3100),
        (5, 2100, 3300), (6, 6300, 5600), (7, 7800, 5400), (8, 1200, 2700),
        (9, 1500, 2900), (10, 1400, 3800), (11, 5800, 1900), (12, 2100, 500)
    ) AS a(m, plan, fact)
),
days AS (
    SELECT d::date AS stat_date,
           extract(month FROM d)::int AS m,
           extract(day FROM d)::int AS dom,
           extract(day FROM date_trunc('month', d) + interval '1 month' - interval '1 day')::int AS dim
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
calc AS (
    SELECT d.stat_date,
           (1 - cos(pi() * (d.dom - 1)::double precision / d.dim)) / 2 AS s,
           a0.plan AS p0, COALESCE(a1.plan, a0.plan) AS p1,
           a0.fact AS f0, COALESCE(a1.fact, a0.fact) AS f1
    FROM days d
    JOIN anchors a0 ON a0.m = d.m
    LEFT JOIN anchors a1 ON a1.m = d.m + 1
)
INSERT INTO dash_production_daily (stat_date, plan_qty, fact_qty)
SELECT stat_date,
       round((p0 + (p1 - p0) * s)::numeric, 4),
       round((f0 + (f1 - f0) * s)::numeric, 4)
FROM calc
ORDER BY stat_date
ON CONFLICT (stat_date) DO NOTHING;

-- Субъекты графика «Уровень брака»
INSERT INTO dash_defect_subject (view_kind, subject_key, name, sort_order) VALUES
    ('part', 'part_1', 'Деталь 1', 1),
    ('part', 'part_2', 'Деталь 2', 2),
    ('part', 'part_3', 'Деталь 3', 3),
    ('part', 'part_4', 'Деталь 4', 4),
    ('part', 'part_5', 'Деталь 5', 5),
    ('part', 'part_6', 'Деталь 6', 6),
    ('part', 'part_7', 'Деталь 7', 7),
    ('part', 'part_8', 'Деталь 8', 8),
    ('workshop', 'ws_1', 'Цех № 1', 1),
    ('workshop', 'ws_2', 'Цех № 2', 2),
    ('workshop', 'ws_3', 'Цех № 3', 3),
    ('workshop', 'ws_4', 'Цех № 4', 4),
    ('workshop', 'ws_5', 'Цех № 5', 5),
    ('workshop', 'ws_6', 'Цех № 6', 6),
    ('workshop', 'ws_7', 'Цех № 7', 7),
    ('workshop', 'ws_8', 'Цех № 8', 8)
ON CONFLICT (view_kind, subject_key) DO NOTHING;

-- Элементы раскрытия: для вида «По деталям» — подразделения, для «По подразделениям» — номенклатура
INSERT INTO dash_defect_item (view_kind, item_key, name, sort_order) VALUES
    ('part', 'ws_1', 'Цех металлообработки № 1', 1),
    ('part', 'ws_2', 'Цех металлообработки № 2', 2),
    ('part', 'ws_3', 'Цех металлоконструкций', 3),
    ('part', 'ws_4', 'Цех металлорежущий № 4', 4),
    ('part', 'ws_5', 'Цех металлорежущий № 5', 5),
    ('workshop', 'nom_1',  'Шкив Y0001N5', 1),
    ('workshop', 'nom_2',  'Крыльчатка 200/КВ 01', 2),
    ('workshop', 'nom_3',  'Привод 8100-А', 3),
    ('workshop', 'nom_4',  'Турбина ТС 2003', 4),
    ('workshop', 'nom_5',  'Генератор Р105', 5),
    ('workshop', 'nom_6',  'Патрубок АОК 10-70', 6),
    ('workshop', 'nom_7',  'Датчик кислородный N200', 7),
    ('workshop', 'nom_8',  'Рама конструкционная И10500', 8),
    ('workshop', 'nom_9',  'Сигнальный блок AL-765', 9),
    ('workshop', 'nom_10', 'Узел усилителя 444BHK', 10)
ON CONFLICT (view_kind, item_key) DO NOTHING;

-- Выпуск и брак по субъектам и элементам (итоги за 01.01–30.11.2025 — ровно из Excel).
-- Брак взят из листа «Уровень брака» как есть. Выпуск субъекта разложен по элементам
-- пропорционально браку: для деталей — строго пропорционально (в pop-up показывается только брак),
-- для подразделений — с детерминированным разбросом ±10%, чтобы доли брака по номенклатуре различались.
WITH totals(view_kind, subject_key, item_key, released, defect) AS (VALUES
    ('part','part_1','ws_1',473,180),
    ('part','part_1','ws_2',61,23),
    ('part','part_2','ws_3',109,47),
    ('part','part_3','ws_3',50,34),
    ('part','part_3','ws_4',36,25),
    ('part','part_4','ws_2',246,54),
    ('part','part_4','ws_4',637,140),
    ('part','part_5','ws_2',134,118),
    ('part','part_5','ws_3',41,36),
    ('part','part_5','ws_5',127,112),
    ('part','part_6','ws_2',59,30),
    ('part','part_6','ws_3',59,30),
    ('part','part_6','ws_5',170,87),
    ('part','part_7','ws_1',205,123),
    ('part','part_7','ws_3',340,204),
    ('part','part_7','ws_4',896,538),
    ('part','part_8','ws_1',60,46),
    ('part','part_8','ws_2',19,15),
    ('part','part_8','ws_4',44,34),
    ('part','part_8','ws_5',185,142),
    ('workshop','ws_1','nom_1',348,45),
    ('workshop','ws_2','nom_2',461,192),
    ('workshop','ws_2','nom_3',202,103),
    ('workshop','ws_3','nom_2',75,13),
    ('workshop','ws_3','nom_4',52,9),
    ('workshop','ws_4','nom_5',495,136),
    ('workshop','ws_4','nom_7',88,24),
    ('workshop','ws_4','nom_9',124,34),
    ('workshop','ws_5','nom_1',203,17),
    ('workshop','ws_5','nom_6',51,4),
    ('workshop','ws_6','nom_1',4,2),
    ('workshop','ws_6','nom_2',17,8),
    ('workshop','ws_6','nom_3',32,18),
    ('workshop','ws_6','nom_4',26,12),
    ('workshop','ws_6','nom_8',104,48),
    ('workshop','ws_7','nom_3',83,18),
    ('workshop','ws_7','nom_4',139,34),
    ('workshop','ws_7','nom_5',92,20),
    ('workshop','ws_7','nom_7',36,8),
    ('workshop','ws_7','nom_9',149,33),
    ('workshop','ws_7','nom_10',248,59),
    ('workshop','ws_8','nom_5',28,9),
    ('workshop','ws_8','nom_7',117,37),
    ('workshop','ws_8','nom_9',77,24),
    ('workshop','ws_8','nom_10',62,23)
),
numbered AS (
    SELECT t.*, row_number() OVER (ORDER BY view_kind, subject_key, item_key) AS ord
    FROM totals t
),
days AS (
    SELECT d::date AS stat_date,
           extract(doy FROM d)::int AS doy,
           d::date <= DATE '2025-11-30' AS in_main
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT n.view_kind, n.subject_key, n.item_key, d.stat_date, d.in_main,
           CASE WHEN d.in_main THEN n.released ELSE n.released * 0.08 END AS r_total,
           CASE WHEN d.in_main THEN n.defect   ELSE n.defect   * 0.08 END AS d_total,
           1 + 0.3 * sin(2 * pi() * d.doy / 365 + 0.7 * n.ord) AS w
    FROM numbered n
    CROSS JOIN days d
),
rounded AS (
    SELECT view_kind, subject_key, item_key, stat_date, in_main, r_total, d_total,
           round((r_total * w / sum(w) OVER (PARTITION BY view_kind, subject_key, item_key, in_main))::numeric, 4) AS released,
           round((d_total * w / sum(w) OVER (PARTITION BY view_kind, subject_key, item_key, in_main))::numeric, 4) AS defect
    FROM weighted
),
adjusted AS (
    SELECT view_kind, subject_key, item_key, stat_date,
           released + CASE WHEN stat_date = DATE '2025-11-30'
                           THEN r_total - sum(released) OVER (PARTITION BY view_kind, subject_key, item_key, in_main)
                           ELSE 0 END AS released,
           defect + CASE WHEN stat_date = DATE '2025-11-30'
                         THEN d_total - sum(defect) OVER (PARTITION BY view_kind, subject_key, item_key, in_main)
                         ELSE 0 END AS defect
    FROM rounded
)
INSERT INTO dash_defect_daily (stat_date, view_kind, subject_key, item_key, released_qty, defect_qty)
SELECT stat_date, view_kind, subject_key, item_key, released, defect
FROM adjusted
ORDER BY view_kind, subject_key, item_key, stat_date
ON CONFLICT (stat_date, view_kind, subject_key, item_key) DO NOTHING;

-- Показатели качества (лист «Показатели качества»): выпуск 504 248, брак 109 302,
-- всего выпуск с производства 613 550, средний уровень брака 17,8146850297449 %
WITH days AS (
    SELECT d::date AS stat_date,
           extract(doy FROM d)::int AS doy,
           d::date <= DATE '2025-11-30' AS in_main
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT stat_date, in_main,
           CASE WHEN in_main THEN 504248.0 ELSE 504248.0 * 0.08 END AS r_total,
           CASE WHEN in_main THEN 109302.0 ELSE 109302.0 * 0.08 END AS d_total,
           1 + 0.32 * sin(2 * pi() * doy / 365 + 0.5) AS wr,
           1 + 0.32 * sin(2 * pi() * doy / 365 + 1.9) AS wd
    FROM days
),
rounded AS (
    SELECT stat_date, in_main, r_total, d_total,
           round((r_total * wr / sum(wr) OVER (PARTITION BY in_main))::numeric, 4) AS released,
           round((d_total * wd / sum(wd) OVER (PARTITION BY in_main))::numeric, 4) AS defect
    FROM weighted
),
adjusted AS (
    SELECT stat_date,
           released + CASE WHEN stat_date = DATE '2025-11-30'
                           THEN r_total - sum(released) OVER (PARTITION BY in_main) ELSE 0 END AS released,
           defect + CASE WHEN stat_date = DATE '2025-11-30'
                         THEN d_total - sum(defect) OVER (PARTITION BY in_main) ELSE 0 END AS defect
    FROM rounded
)
INSERT INTO dash_quality_daily (stat_date, released_qty, defect_qty)
SELECT stat_date, released, defect FROM adjusted
ORDER BY stat_date
ON CONFLICT (stat_date) DO NOTHING;

-- Производство (лист «Производство»): прошли КК 504 248 (75,284 %), ожидают КК 56 244 (8,397 %),
-- не прошли КК 109 302 (16,319 %); всего выпуск с производства 669 794
WITH days AS (
    SELECT d::date AS stat_date,
           extract(doy FROM d)::int AS doy,
           d::date <= DATE '2025-11-30' AS in_main
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT stat_date, in_main,
           CASE WHEN in_main THEN 504248.0 ELSE 504248.0 * 0.08 END AS p_total,
           CASE WHEN in_main THEN  56244.0 ELSE  56244.0 * 0.08 END AS w_total,
           CASE WHEN in_main THEN 109302.0 ELSE 109302.0 * 0.08 END AS f_total,
           1 + 0.32 * sin(2 * pi() * doy / 365 + 0.5) AS wp,
           1 + 0.32 * sin(2 * pi() * doy / 365 + 1.1) AS ww,
           1 + 0.32 * sin(2 * pi() * doy / 365 + 1.9) AS wf
    FROM days
),
rounded AS (
    SELECT stat_date, in_main, p_total, w_total, f_total,
           round((p_total * wp / sum(wp) OVER (PARTITION BY in_main))::numeric, 4) AS passed,
           round((w_total * ww / sum(ww) OVER (PARTITION BY in_main))::numeric, 4) AS waiting,
           round((f_total * wf / sum(wf) OVER (PARTITION BY in_main))::numeric, 4) AS failed
    FROM weighted
),
adjusted AS (
    SELECT stat_date,
           passed + CASE WHEN stat_date = DATE '2025-11-30'
                         THEN p_total - sum(passed) OVER (PARTITION BY in_main) ELSE 0 END AS passed,
           waiting + CASE WHEN stat_date = DATE '2025-11-30'
                          THEN w_total - sum(waiting) OVER (PARTITION BY in_main) ELSE 0 END AS waiting,
           failed + CASE WHEN stat_date = DATE '2025-11-30'
                         THEN f_total - sum(failed) OVER (PARTITION BY in_main) ELSE 0 END AS failed
    FROM rounded
)
INSERT INTO dash_qc_daily (stat_date, passed_qty, waiting_qty, failed_qty)
SELECT stat_date, passed, waiting, failed FROM adjusted
ORDER BY stat_date
ON CONFLICT (stat_date) DO NOTHING;

-- Лента «Выпуск продукции»: последние события выпуска (номенклатура из листа «Уровень брака»)
INSERT INTO dash_release_event (name, event_at)
SELECT v.name, v.event_at::timestamp
FROM (VALUES
    ('Шкив Y0001N5',                '2026-05-12 17:40:00'),
    ('Крыльчатка 200/КВ 01',        '2026-05-12 15:15:00'),
    ('Привод 8100-А',               '2026-05-12 15:10:00'),
    ('Турбина ТС 2003',             '2026-05-12 13:56:00'),
    ('Генератор Р105',              '2026-05-12 13:30:00'),
    ('Патрубок АОК 10-70',          '2026-05-12 11:56:00'),
    ('Датчик кислородный N200',     '2026-05-12 10:13:00'),
    ('Рама конструкционная И10500', '2026-05-12 10:02:00'),
    ('Сигнальный блок AL-765',      '2026-05-11 17:24:00'),
    ('Узел усилителя 444BHK',       '2026-05-11 16:15:00'),
    ('Шкив Y0001N5',                '2026-05-11 14:48:00'),
    ('Крыльчатка 200/КВ 01',        '2026-05-11 12:07:00'),
    ('Привод 8100-А',               '2026-05-11 10:38:00'),
    ('Турбина ТС 2003',             '2026-05-10 16:52:00'),
    ('Генератор Р105',              '2026-05-10 13:53:00'),
    ('Патрубок АОК 10-70',          '2026-05-10 11:31:00'),
    ('Датчик кислородный N200',     '2026-05-09 16:47:00'),
    ('Рама конструкционная И10500', '2026-05-09 14:20:00'),
    ('Сигнальный блок AL-765',      '2026-05-09 11:00:00'),
    ('Узел усилителя 444BHK',       '2026-05-08 09:42:00')
) AS v(name, event_at)
WHERE NOT EXISTS (SELECT 1 FROM dash_release_event);

-- Показатели панели оператора (лист «Инфопанели»): ТМЦ в станциях 1820 из 2040 (89,22 %),
-- выдано ТМЦ 141 из 1820 (7,75 %), выдано сверхнормы 6 из 1820 (0,33 %), детали на СГД 47 из 200 (23,5 %).
-- Ёмкость 2040 = 4 барабана по 504 ячейки + постамат на 24 ячейки.
INSERT INTO dash_operator_metric_daily
    (stat_date, tmc_in_stations, tmc_capacity, issued_tmc, issued_over_norm, sgd_parts, sgd_capacity)
VALUES (DATE '2025-11-30', 1820, 2040, 141, 6, 47, 200)
ON CONFLICT (stat_date) DO NOTHING;

-- Станции для графика критических и минимальных остатков (лист «КО и МО»)
INSERT INTO dash_station_balance (station_key, name, sort_order) VALUES
    ('st_1', 'Станция 1', 1),
    ('st_2', 'Станция 2', 2),
    ('st_3', 'Станция 3', 3),
    ('st_4', 'Станция 4', 4),
    ('st_5', 'Станция 5', 5),
    ('st_6', 'Станция 6', 6),
    ('st_7', 'Станция 7', 7)
ON CONFLICT (station_key) DO NOTHING;

-- Номенклатура станций. Наименьший остаток каждой станции совпадает со значением из Excel
-- (117, 167, 59, 431, 388, 158, 476) — именно он выводится на график. Пороги подобраны так,
-- чтобы статус станции по минимальной номенклатуре совпадал с прототипом:
-- КО у станций 1, 2, 3, 6 и МО у станций 4, 5, 7.
INSERT INTO dash_station_balance_item (station_key, nom_name, quantity, min_level, critical_level, sort_order)
SELECT v.station_key, v.nom_name, v.quantity, v.min_level, v.critical_level, v.sort_order
FROM (VALUES
    ('st_1', 'Фреза концевая Ø12',            117, 260, 120, 1),
    ('st_1', 'Пластина твердосплавная CNMG',  184, 260, 120, 2),
    ('st_1', 'Сверло Ø8,5',                   231, 260, 120, 3),
    ('st_2', 'Резец проходной PCLNR',         167, 380, 180, 1),
    ('st_2', 'Метчик М10',                    242, 380, 180, 2),
    ('st_2', 'Патрон цанговый ER32',          311, 380, 180, 3),
    ('st_2', 'Круг отрезной 125×1,2',         358, 380, 180, 4),
    ('st_3', 'Фреза дисковая Ø63',             59, 150,  70, 1),
    ('st_3', 'Сверло центровочное А4',         96, 150,  70, 2),
    ('st_3', 'Штангенциркуль ШЦ-I',           134, 150,  70, 3),
    ('st_4', 'Пластина токарная WNMG',        431, 500, 300, 1),
    ('st_4', 'Оправка фрезерная BT40',        462, 500, 300, 2),
    ('st_4', 'СОЖ эмульсол 20 л',             487, 500, 300, 3),
    ('st_5', 'Фреза торцевая Ø80',            388, 450, 250, 1),
    ('st_5', 'Втулка переходная КМ3',         409, 450, 250, 2),
    ('st_5', 'Ключ динамометрический',        436, 450, 250, 3),
    ('st_6', 'Сверло твердосплавное Ø6,8',    158, 350, 170, 1),
    ('st_6', 'Зенкер Ø16',                    203, 350, 170, 2),
    ('st_6', 'Микрометр МК-25',               284, 350, 170, 3),
    ('st_6', 'Щётка проволочная',             327, 350, 170, 4),
    ('st_7', 'Резец расточной S16',           476, 560, 320, 1),
    ('st_7', 'Фреза угловая Ø50',             503, 560, 320, 2),
    ('st_7', 'Набор щупов 0,05–1,0',          541, 560, 320, 3)
) AS v(station_key, nom_name, quantity, min_level, critical_level, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dash_station_balance_item);

-- Ленты панели оператора: заказы на поставку и экран событий
INSERT INTO dash_operator_event (kind, title, event_at, done)
SELECT v.kind, v.title, v.event_at::timestamp, v.done
FROM (VALUES
    ('order', 'Заказ №106',      '2026-05-12 18:36:00', false),
    ('order', 'Заказ №105',      '2026-05-12 17:03:00', false),
    ('order', 'Заказ №104',      '2026-05-11 16:15:00', false),
    ('order', 'Заказ №103',      '2026-05-11 10:38:00', false),
    ('order', 'Заказ №102',      '2026-05-10 13:53:00', false),
    ('order', 'Заказ №101',      '2026-05-09 16:47:00', false),
    ('order', 'Заказ №100',      '2026-05-09 11:00:00', false),
    ('order', 'Заказ №099',      '2026-05-08 15:24:00', true),
    ('order', 'Заказ №098',      '2026-05-08 12:10:00', true),
    ('order', 'Заказ №097',      '2026-05-07 09:35:00', true),
    ('event', 'Пополнение №1004', '2026-05-12 17:40:00', false),
    ('event', 'Пополнение №1003', '2026-05-12 15:15:00', false),
    ('event', 'Пополнение №1002', '2026-05-12 15:10:00', false),
    ('event', 'Пополнение №1001', '2026-05-12 13:56:00', false),
    ('event', 'Пополнение №1000', '2026-05-12 13:30:00', false),
    ('event', 'Списание №0012',   '2026-05-12 11:56:00', false),
    ('event', 'Пополнение №0999', '2026-05-12 10:13:00', false),
    ('event', 'Пополнение №0998', '2026-05-12 10:02:00', false),
    ('event', 'Списание №0011',   '2026-05-11 17:24:00', true),
    ('event', 'Пополнение №0997', '2026-05-11 16:15:00', true),
    ('event', 'Пополнение №0996', '2026-05-11 14:48:00', true),
    ('event', 'Списание №0010',   '2026-05-11 12:07:00', true)
) AS v(kind, title, event_at, done)
WHERE NOT EXISTS (SELECT 1 FROM dash_operator_event);
