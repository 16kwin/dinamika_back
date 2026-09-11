-- V4__Dashboard_economic.sql
-- Панель «Экономический блок»: справочник видов номенклатуры, дневные ряды затрат, показателей и бюджета
-- (тестовые данные, позже заменятся реальными источниками — doc_entrance / выдача / бюджет)
-- и настройки карточек панели на пользователя.

-- ==================== Структура ====================

-- Виды номенклатуры (sort_order — порядок на макете)
CREATE TABLE IF NOT EXISTS dash_nomenclature_type (
    type_key   VARCHAR(64)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    sort_order INTEGER      NOT NULL
);

-- График «Затраты на приобретение»: значение расходов (план / факт) в точке дня, руб.
CREATE TABLE IF NOT EXISTS dash_cost_daily (
    cost_date   DATE PRIMARY KEY,
    plan_amount NUMERIC(16,2) NOT NULL,
    fact_amount NUMERIC(16,2) NOT NULL
);

-- Затраты по видам номенклатуры за день, руб.
CREATE TABLE IF NOT EXISTS dash_cost_type_daily (
    id        BIGSERIAL PRIMARY KEY,
    cost_date DATE NOT NULL,
    type_key  VARCHAR(64) NOT NULL REFERENCES dash_nomenclature_type(type_key),
    amount    NUMERIC(16,2) NOT NULL,
    UNIQUE (cost_date, type_key)
);

-- Показатели затрат: закупки / выдача за день, руб.
CREATE TABLE IF NOT EXISTS dash_indicator_daily (
    cost_date        DATE PRIMARY KEY,
    purchases_amount NUMERIC(16,2) NOT NULL,
    issue_amount     NUMERIC(16,2) NOT NULL
);

-- Исполнение бюджета: план / факт за день, руб.
CREATE TABLE IF NOT EXISTS dash_budget_daily (
    cost_date   DATE PRIMARY KEY,
    plan_amount NUMERIC(16,2) NOT NULL,
    fact_amount NUMERIC(16,2) NOT NULL
);

-- Выбранные пользователем виды номенклатуры для карточек панели (по образцу user_unit_column_settings)
CREATE TABLE IF NOT EXISTS user_dashboard_settings (
    id               BIGSERIAL PRIMARY KEY,
    user_id          INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bar_types_json   JSONB NOT NULL DEFAULT '[]'::jsonb,
    radar_types_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_dash_cost_type_daily_date ON dash_cost_type_daily(cost_date);

-- ==================== Сиды ====================
-- Все суммы — рубли. За диапазон 01.01.2025–30.11.2025 итоги совпадают с Excel до копейки:
-- дневное значение = round(T * w_d / W, 2), где W — сумма весов диапазона; остаток округления
-- добавляется к 30.11.2025. Декабрь генерируется по той же формуле от 8% суммы и в итоги не входит.

-- Виды номенклатуры
INSERT INTO dash_nomenclature_type (type_key, name, sort_order) VALUES
    ('frezy_monolitnye',        'Фрезы монолитные',             1),
    ('frezy_smennye',           'Фрезы со сменными пластинами', 2),
    ('metizy',                  'Метизы',                       3),
    ('plastiny_tverdosplavnye', 'Пластины твердосплавные',      4),
    ('reztsy_tokarnye',         'Резцы токарные',               5),
    ('instrument_slesarnyi',    'Инструмент слесарный',         6),
    ('sverla_tverdosplavnye',   'Сверла твердосплавные',        7),
    ('osnastka_tokarnaya',      'Оснастка токарная',            8),
    ('osnastka_frezernaya',     'Оснастка фрезерная',           9),
    ('abrazivy',                'Абразивы',                     10),
    ('izmeritelnyi_instrument', 'Измерительный инструмент',     11),
    ('sozh',                    'СОЖ и смазки',                 12)
ON CONFLICT (type_key) DO NOTHING;

-- График «Затраты на приобретение»: опорные значения (млн руб.) на 1-е число месяца,
-- между ними косинусная интерполяция v = a_m + (a_{m+1} - a_m) * (1 - cos(pi * t)) / 2,
-- t = (день - 1) / дней_в_месяце; для декабря a_13 = a_12 (ровная линия). Хранится round(v * 1e6, 2).
WITH anchors AS (
    SELECT m, plan, fact FROM (VALUES
        (1, 4.6, 2.6), (2, 3.8, 2.9), (3, 3.1, 6.1), (4, 4.5, 3.1),  (5, 2.1, 3.3),  (6, 6.3, 5.6),
        (7, 7.8, 5.4), (8, 1.2, 2.7), (9, 1.5, 2.9), (10, 1.4, 3.8), (11, 5.8, 1.9), (12, 2.1, 0.5)
    ) AS a(m, plan, fact)
),
days AS (
    SELECT d::date AS cost_date,
           extract(month FROM d)::int AS m,
           extract(day FROM d)::int AS dom,
           extract(day FROM date_trunc('month', d) + interval '1 month' - interval '1 day')::int AS dim
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
calc AS (
    SELECT d.cost_date,
           (1 - cos(pi() * (d.dom - 1)::double precision / d.dim)) / 2 AS s,
           a0.plan AS p0, COALESCE(a1.plan, a0.plan) AS p1,
           a0.fact AS f0, COALESCE(a1.fact, a0.fact) AS f1
    FROM days d
    JOIN anchors a0 ON a0.m = d.m
    LEFT JOIN anchors a1 ON a1.m = d.m + 1
)
INSERT INTO dash_cost_daily (cost_date, plan_amount, fact_amount)
SELECT cost_date,
       round(((p0 + (p1 - p0) * s) * 1000000)::numeric, 2),
       round(((f0 + (f1 - f0) * s) * 1000000)::numeric, 2)
FROM calc
ORDER BY cost_date
ON CONFLICT (cost_date) DO NOTHING;

-- Затраты по видам номенклатуры: вес дня w_d = 1 + 0.3 * sin(2*pi*doy/365 + 0.7*sort_order)
WITH totals AS (
    SELECT type_key, total FROM (VALUES
        ('frezy_monolitnye',         642500.00),
        ('frezy_smennye',            862400.00),
        ('metizy',                  1838558.00),
        ('plastiny_tverdosplavnye',  275768.00),
        ('reztsy_tokarnye',          385769.00),
        ('instrument_slesarnyi',      26384.00),
        ('sverla_tverdosplavnye',    342567.00),
        ('osnastka_tokarnaya',       384575.00),
        ('osnastka_frezernaya',     2848595.00),
        ('abrazivy',                 158240.00),
        ('izmeritelnyi_instrument',   96730.00),
        ('sozh',                     214905.00)
    ) AS v(type_key, total)
),
days AS (
    SELECT d::date AS cost_date,
           extract(doy FROM d)::int AS doy,
           d::date <= DATE '2025-11-30' AS in_main   -- основной диапазон 01.01–30.11 / декабрь
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT t.type_key, d.cost_date, d.in_main,
           CASE WHEN d.in_main THEN v.total ELSE v.total * 0.08 END AS period_total,
           1 + 0.3 * sin(2 * pi() * d.doy / 365 + 0.7 * t.sort_order) AS w
    FROM dash_nomenclature_type t
    JOIN totals v ON v.type_key = t.type_key
    CROSS JOIN days d
),
rounded AS (
    SELECT type_key, cost_date, in_main, period_total,
           round((period_total * w / sum(w) OVER (PARTITION BY type_key, in_main))::numeric, 2) AS amount
    FROM weighted
),
adjusted AS (
    -- остаток округления — на 30.11.2025, чтобы сумма за 01.01–30.11 была ровно T
    SELECT type_key, cost_date,
           amount + CASE WHEN cost_date = DATE '2025-11-30'
                         THEN period_total - sum(amount) OVER (PARTITION BY type_key, in_main)
                         ELSE 0 END AS amount
    FROM rounded
)
INSERT INTO dash_cost_type_daily (cost_date, type_key, amount)
SELECT cost_date, type_key, amount
FROM adjusted
ORDER BY type_key, cost_date
ON CONFLICT (cost_date, type_key) DO NOTHING;

-- Показатели затрат: закупки T = 2 859 579.00 (вес 1 + 0.35*sin(2*pi*doy/365 + 0.3)),
-- выдача T = 1 703 932.00 (вес 1 + 0.35*sin(2*pi*doy/365 + 1.5))
WITH days AS (
    SELECT d::date AS cost_date,
           extract(doy FROM d)::int AS doy,
           d::date <= DATE '2025-11-30' AS in_main
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT cost_date, in_main,
           CASE WHEN in_main THEN 2859579.00 ELSE 2859579.00 * 0.08 END AS p_total,
           CASE WHEN in_main THEN 1703932.00 ELSE 1703932.00 * 0.08 END AS i_total,
           1 + 0.35 * sin(2 * pi() * doy / 365 + 0.3) AS wp,
           1 + 0.35 * sin(2 * pi() * doy / 365 + 1.5) AS wi
    FROM days
),
rounded AS (
    SELECT cost_date, in_main, p_total, i_total,
           round((p_total * wp / sum(wp) OVER (PARTITION BY in_main))::numeric, 2) AS purchases,
           round((i_total * wi / sum(wi) OVER (PARTITION BY in_main))::numeric, 2) AS issue
    FROM weighted
),
adjusted AS (
    SELECT cost_date,
           purchases + CASE WHEN cost_date = DATE '2025-11-30'
                            THEN p_total - sum(purchases) OVER (PARTITION BY in_main)
                            ELSE 0 END AS purchases,
           issue + CASE WHEN cost_date = DATE '2025-11-30'
                        THEN i_total - sum(issue) OVER (PARTITION BY in_main)
                        ELSE 0 END AS issue
    FROM rounded
)
INSERT INTO dash_indicator_daily (cost_date, purchases_amount, issue_amount)
SELECT cost_date, purchases, issue
FROM adjusted
ORDER BY cost_date
ON CONFLICT (cost_date) DO NOTHING;

-- Исполнение бюджета: план T = 3 576 870.00, факт T = 4 480 579.00 (итог 125.27%).
-- Вес плана wp_d = 1 + 0.15*sin(2*pi*doy/365); вес факта wf_d = wp_d * r_m, где r_m — профиль по месяцам
-- (январь ниже 100%, июль–август свыше 200%).
WITH profile AS (
    SELECT m, r FROM (VALUES
        (1, 0.87), (2, 0.88), (3, 0.92), (4, 1.08),  (5, 1.22),  (6, 1.35),
        (7, 2.30), (8, 2.25), (9, 1.02), (10, 1.18), (11, 1.16), (12, 0.85)
    ) AS p(m, r)
),
days AS (
    SELECT d::date AS cost_date,
           extract(doy FROM d)::int AS doy,
           extract(month FROM d)::int AS m,
           d::date <= DATE '2025-11-30' AS in_main
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT d.cost_date, d.in_main,
           CASE WHEN d.in_main THEN 3576870.00 ELSE 3576870.00 * 0.08 END AS p_total,
           CASE WHEN d.in_main THEN 4480579.00 ELSE 4480579.00 * 0.08 END AS f_total,
           1 + 0.15 * sin(2 * pi() * d.doy / 365) AS wp,
           (1 + 0.15 * sin(2 * pi() * d.doy / 365)) * p.r AS wf
    FROM days d
    JOIN profile p ON p.m = d.m
),
rounded AS (
    SELECT cost_date, in_main, p_total, f_total,
           round((p_total * wp / sum(wp) OVER (PARTITION BY in_main))::numeric, 2) AS plan,
           round((f_total * wf / sum(wf) OVER (PARTITION BY in_main))::numeric, 2) AS fact
    FROM weighted
),
adjusted AS (
    SELECT cost_date,
           plan + CASE WHEN cost_date = DATE '2025-11-30'
                       THEN p_total - sum(plan) OVER (PARTITION BY in_main)
                       ELSE 0 END AS plan,
           fact + CASE WHEN cost_date = DATE '2025-11-30'
                       THEN f_total - sum(fact) OVER (PARTITION BY in_main)
                       ELSE 0 END AS fact
    FROM rounded
)
INSERT INTO dash_budget_daily (cost_date, plan_amount, fact_amount)
SELECT cost_date, plan, fact
FROM adjusted
ORDER BY cost_date
ON CONFLICT (cost_date) DO NOTHING;
