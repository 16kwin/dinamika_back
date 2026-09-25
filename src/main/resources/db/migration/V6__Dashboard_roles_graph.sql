-- V6__Dashboard_roles_graph.sql
-- Панели ролей «Контролер», «Главный контролер», «Начальник цеха», «Служба закупа», «Аудитор»
-- и отдельные экраны «Граф закупок» и «Экран событий текущего дня»: структура и демо-данные.
--
-- Граф закупок строится из справочников (группы, номенклатура с нормативной ценой, поставщики,
-- связи аффилированности) и отдельных заказов. Сиды графа показываются заказчику, поэтому итоги
-- за 01.01.2025–30.11.2025 заданы точно: цена у всех заказов пары одинаковая, количество делится
-- между заказами целыми, и средняя цена пары ровно равна цене при любом периоде.
-- Ленты контроля и экрана событий хранят не дату, а смещение от текущего дня (days_ago) и время суток:
-- дата считается при запросе, поэтому лента всегда «свежая».
-- Позже эти таблицы заменяются реальными источниками (заказы на поставку, ОТК, журнал операций).

-- ==================== Структура ====================

-- Группы номенклатуры графа закупок (sort_order — порядок в легенде и фильтрах)
CREATE TABLE IF NOT EXISTS dash_graph_group (
    group_key  VARCHAR(64)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    sort_order INTEGER      NOT NULL
);

-- Номенклатура графа. ref_price — нормативная цена: закупка «свыше лимита»,
-- если средняя цена пары выше ref_price × (1 + лимит/100). favorite — избранная позиция.
CREATE TABLE IF NOT EXISTS dash_graph_nomenclature (
    nom_key    VARCHAR(64)   PRIMARY KEY,
    name       VARCHAR(255)  NOT NULL,
    group_key  VARCHAR(64)   NOT NULL REFERENCES dash_graph_group(group_key),
    unit       VARCHAR(16)   NOT NULL,
    ref_price  NUMERIC(14,2) NOT NULL,
    favorite   BOOLEAN       NOT NULL,
    sort_order INTEGER       NOT NULL
);

-- Поставщики графа. anchor — якорный (основной) поставщик
CREATE TABLE IF NOT EXISTS dash_graph_supplier (
    supplier_key VARCHAR(64)  PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    anchor       BOOLEAN      NOT NULL,
    inn          VARCHAR(12),
    city         VARCHAR(128),
    sort_order   INTEGER      NOT NULL
);

-- Заказы на закупку: одна строка — один заказ позиции номенклатуры у поставщика
CREATE TABLE IF NOT EXISTS dash_graph_purchase (
    id           BIGSERIAL PRIMARY KEY,
    order_no     VARCHAR(32)   NOT NULL,
    nom_key      VARCHAR(64)   NOT NULL REFERENCES dash_graph_nomenclature(nom_key),
    supplier_key VARCHAR(64)   NOT NULL REFERENCES dash_graph_supplier(supplier_key),
    purchase_at  TIMESTAMP     NOT NULL,
    qty          NUMERIC(14,2) NOT NULL,
    price        NUMERIC(14,2) NOT NULL
);

-- Связи между поставщиками (kind='affiliated' — аффилированность); от периода не зависят
CREATE TABLE IF NOT EXISTS dash_graph_supplier_link (
    supplier_a VARCHAR(64)  NOT NULL REFERENCES dash_graph_supplier(supplier_key),
    supplier_b VARCHAR(64)  NOT NULL REFERENCES dash_graph_supplier(supplier_key),
    kind       VARCHAR(16)  NOT NULL,
    reason     VARCHAR(255),
    PRIMARY KEY (supplier_a, supplier_b)
);

-- Панель аудитора: операций за день, из них инцидентов и выдач сверх нормы
CREATE TABLE IF NOT EXISTS dash_audit_daily (
    stat_date  DATE    PRIMARY KEY,
    operations INTEGER NOT NULL,
    incidents  INTEGER NOT NULL,
    over_norm  INTEGER NOT NULL
);

-- Лента контролёра: события контроля качества. status: pending — «На контроль»,
-- passed — «Контроль пройден», failed — «Контроль не пройден».
-- Момент события = CURRENT_DATE - days_ago + time_of_day.
CREATE TABLE IF NOT EXISTS dash_control_event (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    department  VARCHAR(128) NOT NULL,
    executor    VARCHAR(255) NOT NULL,
    controller  VARCHAR(255) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    days_ago    INTEGER      NOT NULL,
    time_of_day TIME         NOT NULL
);

-- «Экран событий текущего дня»: события источника (operator / control / release / overpriced) за сегодня.
-- done=false — колонка «В работе», done=true — «Завершено»; tone — цвет плашки статуса
-- (progress / success / danger). Момент события = CURRENT_DATE + time_of_day.
CREATE TABLE IF NOT EXISTS dash_day_event (
    id           BIGSERIAL PRIMARY KEY,
    source       VARCHAR(16)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    person       VARCHAR(128) NOT NULL,
    status_label VARCHAR(64)  NOT NULL,
    tone         VARCHAR(16)  NOT NULL,
    done         BOOLEAN      NOT NULL,
    time_of_day  TIME         NOT NULL,
    sort_order   INTEGER      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dash_graph_purchase_at ON dash_graph_purchase(purchase_at);
CREATE INDEX IF NOT EXISTS idx_dash_graph_purchase_pair ON dash_graph_purchase(nom_key, supplier_key);

-- ==================== Сиды графа закупок ====================

INSERT INTO dash_graph_group (group_key, name, sort_order) VALUES
    ('plates',  'Пластины',            1),
    ('mills',   'Фрезы',               2),
    ('drills',  'Свёрла',              3),
    ('cutters', 'Резцы',               4),
    ('taps',    'Метчики',             5),
    ('holders', 'Державки и оснастка', 6)
ON CONFLICT (group_key) DO NOTHING;

INSERT INTO dash_graph_nomenclature (nom_key, name, group_key, unit, ref_price, favorite, sort_order) VALUES
    ('ccmt-100-15',   'Пластина CCMT 100-15',         'plates',  'шт',   160.00, true,   1),
    ('cmt-200y',      'Пластина CMT 200Y',            'plates',  'шт',   120.00, true,   2),
    ('ccmt-156',      'Пластина CCMT-156',            'plates',  'шт',   210.00, false,  3),
    ('wnmg-080408',   'Пластина WNMG 080408',         'plates',  'шт',   340.00, false,  4),
    ('thread-16er',   'Пластина резьбовая 16ER AG60', 'plates',  'шт',   290.00, false,  5),
    ('aokt-060208',   'Пластина AOKT 060208PER',      'plates',  'шт',   180.00, false,  6),
    ('cmz-n4676',     'Фреза монолитная CMZ N4676',   'mills',   'шт',   450.00, true,   7),
    ('yymt-105-10',   'Фреза YYMT 105-10',            'mills',   'шт',  1000.00, false,  8),
    ('end-mill-12',   'Фреза концевая D12 Z4',        'mills',   'шт',  1850.00, false,  9),
    ('disk-mill-80',  'Фреза дисковая D80',           'mills',   'шт',  2600.00, false, 10),
    ('bd03ca0320',    'Сверло BD03CA0320',            'drills',  'шт',  2400.00, false, 11),
    ('drill-hss-8',   'Сверло HSS D8',                'drills',  'шт',   190.00, false, 12),
    ('drill-carb-10', 'Сверло твердосплавное D10',    'drills',  'шт',  3100.00, false, 13),
    ('center-drill',  'Сверло центровочное A3,15',    'drills',  'шт',   240.00, false, 14),
    ('cut-pass-25',   'Резец проходной 25×16',        'cutters', 'шт',   520.00, false, 15),
    ('cut-bore-16',   'Резец расточной 16×16',        'cutters', 'шт',   610.00, false, 16),
    ('cut-thread',    'Резец резьбовой М16',          'cutters', 'шт',   480.00, false, 17),
    ('tap-m8',        'Метчик М8×1,25',               'taps',    'шт',   310.00, false, 18),
    ('tap-m12',       'Метчик М12×1,75',              'taps',    'шт',   420.00, false, 19),
    ('tap-m16',       'Метчик машинный М16',          'taps',    'шт',   690.00, false, 20),
    ('ckjnl2525m16',  'Державка CKJNL2525M16',        'holders', 'шт',  5200.00, false, 21),
    ('collet-er32',   'Цанга ER32 D10',               'holders', 'шт',   980.00, false, 22),
    ('holder-sk40',   'Оправка SK40 ER32',            'holders', 'шт',  7400.00, false, 23),
    ('vise-100',      'Тиски станочные 100 мм',       'holders', 'шт', 12500.00, false, 24)
ON CONFLICT (nom_key) DO NOTHING;

-- Поставщики. ИНН вымышленные: код региона правдоподобный, контрольные разряды намеренно неверные,
-- чтобы номер не совпал с реальной организацией. У ИП — 12 цифр, у иностранной организации — 9909….
INSERT INTO dash_graph_supplier (supplier_key, name, anchor, inn, city, sort_order) VALUES
    ('cnc1',              'CNC1',                true,  '7701234567',   'Москва',            1),
    ('sandvik-dist',      'Сандвик-Дистрибуция', true,  '7715862044',   'Москва',            2),
    ('zadel',             'Задел',               false, '6658213479',   'Екатеринбург',      3),
    ('vetrov',            'Ветров ИП',           false, '710512384926', 'Тула',              4),
    ('zetov',             'Зетов ИП',            false, '710734951208', 'Тула',              5),
    ('kovalev',           'Ковалёв ИП',          false, '711603278415', 'Тула',              6),
    ('prominstrument',    'ПромИнструмент',      false, '7447125830',   'Челябинск',         7),
    ('metallprom',        'МеталлПром',          false, '5904381726',   'Пермь',             8),
    ('cncmagazine',       'CNCMagazine',         false, '7814526093',   'Санкт-Петербург',   9),
    ('hanjin',            'Ханжин Гуандонг',     false, '9909417352',   'Гуанчжоу',         10),
    ('instrument-servis', 'Инструмент-Сервис',   false, '1655284017',   'Казань',           11),
    ('tehnopark',         'Технопарк Урал',      false, '6671390255',   'Екатеринбург',     12),
    ('stanko-komplekt',   'СтанкоКомплект',      false, '6316047298',   'Самара',           13),
    ('ooo-rezets',        'ООО «Резец»',         false, '5260318476',   'Нижний Новгород',  14),
    ('vector-tools',      'Вектор Тулз',         false, '5406729138',   'Новосибирск',      15),
    ('iskra-snab',        'Искра-Снаб',          false, '0274163805',   'Уфа',              16),
    ('alfa-metiz',        'Альфа-Метиз',         false, '6163852094',   'Ростов-на-Дону',   17),
    ('baltinstrument',    'БалтИнструмент',      false, '3906274519',   'Калининград',      18)
ON CONFLICT (supplier_key) DO NOTHING;

-- Связи аффилированности между поставщиками
INSERT INTO dash_graph_supplier_link (supplier_a, supplier_b, kind, reason) VALUES
    ('vetrov',     'zetov',      'affiliated', 'Общий учредитель'),
    ('kovalev',    'vetrov',     'affiliated', 'Общий юридический адрес'),
    ('iskra-snab', 'alfa-metiz', 'affiliated', 'Один руководитель')
ON CONFLICT (supplier_a, supplier_b) DO NOTHING;

-- Заказы. Пара = номенклатура × поставщик: цена, Σ количества и число заказов заданы;
-- у шести пар время последнего заказа задано точно (last_at) — это верх ленты «Закупки с завышенной ценой».
-- Раскладка заказов пары (кроме заданного последнего) — равномерно по 2025-01-15 … 2025-11-20:
--   день = 2025-01-15 + floor((k - 1 + сдвиг) × 310 / n), сдвиг пары = дробная часть pair_no × 0.618…,
--   время = 08:00 + ((pair_no × 3 + k × 7) mod 10) ч + ((pair_no × 17 + k × 31) mod 60) мин.
-- Количество делится поровну, остаток — на последний заказ пары. order_no — сквозная нумерация 1, 2, 3…
-- по возрастанию purchase_at среди всех заказов (188 заказов), id вставляются в том же порядке.
-- Контроль за 01.01–30.11.2025 (лимит 20%): у ccmt-100-15 свыше лимита двое (vetrov, zetov),
-- у cmt-200y двое (metallprom, hanjin), у остальных — не больше одного; «красные» поставщики —
-- vetrov, zetov, kovalev, metallprom, hanjin, prominstrument. CNC1: Σ = 2 503 840.00 при Σqty 1457;
-- Задел 100 154.00; Ветров ИП 10 000.00; Зетов ИП 23 000.00.
WITH pairs(pair_no, nom_key, supplier_key, price, total_qty, orders_cnt, last_at) AS (VALUES
    -- 1. Пластина CCMT 100-15 (норматив 160)
    ( 1, 'ccmt-100-15',   'cnc1',                100.00, 540, 6, NULL::timestamp),
    ( 2, 'ccmt-100-15',   'zadel',               180.00, 300, 5, NULL),
    ( 3, 'ccmt-100-15',   'vetrov',              200.00,  50, 2, NULL),
    ( 4, 'ccmt-100-15',   'zetov',               230.00, 100, 3, NULL),
    -- 2. Пластина CMT 200Y (120)
    ( 5, 'cmt-200y',      'cnc1',                120.00, 307, 4, NULL),
    ( 6, 'cmt-200y',      'prominstrument',      120.00, 220, 3, NULL),
    ( 7, 'cmt-200y',      'cncmagazine',         130.00, 180, 3, NULL),
    ( 8, 'cmt-200y',      'metallprom',          150.00, 140, 2, NULL),
    ( 9, 'cmt-200y',      'hanjin',              220.00,  90, 2, NULL),
    -- 3. Пластина CCMT-156 (210)
    (10, 'ccmt-156',      'sandvik-dist',        205.00, 400, 6, NULL),
    (11, 'ccmt-156',      'instrument-servis',   215.00, 160, 3, NULL),
    (12, 'ccmt-156',      'kovalev',             290.00,  60, 2, TIMESTAMP '2025-11-27 17:03:00'),
    -- 4. Пластина WNMG 080408 (340)
    (13, 'wnmg-080408',   'sandvik-dist',        330.00, 380, 6, NULL),
    (14, 'wnmg-080408',   'vector-tools',        355.00, 120, 3, NULL),
    (15, 'wnmg-080408',   'cncmagazine',         345.00,  90, 2, NULL),
    -- 5. Пластина резьбовая 16ER AG60 (290)
    (16, 'thread-16er',   'tehnopark',           280.00, 140, 3, NULL),
    (17, 'thread-16er',   'cnc1',                300.00,  60, 2, NULL),
    (18, 'thread-16er',   'hanjin',              405.00,  30, 2, TIMESTAMP '2025-11-26 16:15:00'),
    -- 6. Пластина AOKT 060208PER (180)
    (19, 'aokt-060208',   'cnc1',                175.00, 200, 3, NULL),
    (20, 'aokt-060208',   'ooo-rezets',          185.00, 120, 2, NULL),
    (21, 'aokt-060208',   'kovalev',             250.00,  40, 2, TIMESTAMP '2025-11-21 16:47:00'),
    -- 7. Фреза монолитная CMZ N4676 (450)
    (22, 'cmz-n4676',     'zadel',               491.00,  94, 3, NULL),
    (23, 'cmz-n4676',     'prominstrument',      563.00,  40, 2, NULL),
    (24, 'cmz-n4676',     'metallprom',          430.00, 110, 3, NULL),
    -- 8. Фреза YYMT 105-10 (1000)
    (25, 'yymt-105-10',   'sandvik-dist',        980.00,  60, 3, NULL),
    (26, 'yymt-105-10',   'hanjin',             1350.00,  12, 2, TIMESTAMP '2025-11-28 18:36:00'),
    (27, 'yymt-105-10',   'vector-tools',       1040.00,  30, 2, NULL),
    -- 9. Фреза концевая D12 Z4 (1850)
    (28, 'end-mill-12',   'sandvik-dist',       1790.00,  70, 4, NULL),
    (29, 'end-mill-12',   'stanko-komplekt',    1920.00,  40, 2, NULL),
    (30, 'end-mill-12',   'prominstrument',     1880.00,  25, 1, NULL),
    -- 10. Фреза дисковая D80 (2600)
    (31, 'disk-mill-80',  'stanko-komplekt',    2550.00,  20, 2, NULL),
    (32, 'disk-mill-80',  'alfa-metiz',         2650.00,   8, 1, NULL),
    (33, 'disk-mill-80',  'tehnopark',          2700.00,  14, 2, NULL),
    -- 11. Сверло BD03CA0320 (2400)
    (34, 'bd03ca0320',    'sandvik-dist',       2350.00,  30, 3, NULL),
    (35, 'bd03ca0320',    'kovalev',            3150.00,   6, 2, TIMESTAMP '2025-11-24 13:53:00'),
    (36, 'bd03ca0320',    'instrument-servis',  2480.00,  18, 2, NULL),
    -- 12. Сверло HSS D8 (190)
    (37, 'drill-hss-8',   'alfa-metiz',          185.00, 600, 6, NULL),
    (38, 'drill-hss-8',   'iskra-snab',          195.00, 450, 5, NULL),
    (39, 'drill-hss-8',   'ooo-rezets',          180.00, 300, 3, NULL),
    -- 13. Сверло твердосплавное D10 (3100)
    (40, 'drill-carb-10', 'sandvik-dist',       3050.00,  40, 3, NULL),
    (41, 'drill-carb-10', 'vector-tools',       3200.00,  22, 2, NULL),
    -- 14. Сверло центровочное A3,15 (240)
    (42, 'center-drill',  'alfa-metiz',          230.00, 200, 3, NULL),
    (43, 'center-drill',  'baltinstrument',      250.00, 120, 2, NULL),
    (44, 'center-drill',  'cncmagazine',         245.00,  80, 2, NULL),
    -- 15. Резец проходной 25×16 (520)
    (45, 'cut-pass-25',   'ooo-rezets',          500.00, 150, 4, NULL),
    (46, 'cut-pass-25',   'tehnopark',           530.00,  90, 2, NULL),
    (47, 'cut-pass-25',   'metallprom',          540.00,  60, 2, NULL),
    -- 16. Резец расточной 16×16 (610)
    (48, 'cut-bore-16',   'ooo-rezets',          600.00,  80, 3, NULL),
    (49, 'cut-bore-16',   'stanko-komplekt',     640.00,  40, 2, NULL),
    (50, 'cut-bore-16',   'metallprom',          700.00,  15, 1, NULL),
    -- 17. Резец резьбовой М16 (480)
    (51, 'cut-thread',    'ooo-rezets',          470.00,  70, 2, NULL),
    (52, 'cut-thread',    'instrument-servis',   495.00,  50, 2, NULL),
    -- 18. Метчик М8×1,25 (310)
    (53, 'tap-m8',        'iskra-snab',          300.00, 240, 4, NULL),
    (54, 'tap-m8',        'alfa-metiz',          315.00, 200, 3, NULL),
    (55, 'tap-m8',        'hanjin',              395.00,  40, 1, NULL),
    -- 19. Метчик М12×1,75 (420)
    (56, 'tap-m12',       'iskra-snab',          410.00, 160, 3, NULL),
    (57, 'tap-m12',       'baltinstrument',      430.00,  90, 2, NULL),
    -- 20. Метчик машинный М16 (690)
    (58, 'tap-m16',       'vector-tools',        680.00,  60, 2, NULL),
    (59, 'tap-m16',       'cncmagazine',         700.00,  40, 2, NULL),
    (60, 'tap-m16',       'hanjin',              960.00,  20, 1, NULL),
    -- 21. Державка CKJNL2525M16 (5200)
    (61, 'ckjnl2525m16',  'cnc1',               5100.00, 100, 4, NULL),
    (62, 'ckjnl2525m16',  'kovalev',            6600.00,  10, 2, TIMESTAMP '2025-11-25 10:38:00'),
    (63, 'ckjnl2525m16',  'stanko-komplekt',    5350.00,  24, 2, NULL),
    -- 22. Цанга ER32 D10 (980)
    (64, 'collet-er32',   'baltinstrument',      960.00, 120, 3, NULL),
    (65, 'collet-er32',   'tehnopark',           990.00,  80, 2, NULL),
    (66, 'collet-er32',   'vector-tools',       1010.00,  40, 1, NULL),
    -- 23. Оправка SK40 ER32 (7400)
    (67, 'holder-sk40',   'cnc1',               7400.00, 250, 5, NULL),
    (68, 'holder-sk40',   'stanko-komplekt',    7600.00,  30, 2, NULL),
    (69, 'holder-sk40',   'instrument-servis',  7550.00,  20, 1, NULL),
    -- 24. Тиски станочные 100 мм (12500)
    (70, 'vise-100',      'stanko-komplekt',   12300.00,  12, 2, NULL),
    (71, 'vise-100',      'metallprom',        12900.00,   8, 1, NULL),
    (72, 'vise-100',      'prominstrument',    16200.00,   4, 1, NULL)
),
expanded AS (
    SELECT p.pair_no, p.nom_key, p.supplier_key, p.price, p.total_qty, p.orders_cnt, p.last_at, k,
           -- сколько заказов раскладывается равномерно: заданный последний заказ — вне раскладки
           p.orders_cnt - CASE WHEN p.last_at IS NULL THEN 0 ELSE 1 END AS spread_cnt,
           -- сдвиг пары внутри шага раскладки, [0; 1)
           p.pair_no * 0.6180339887 - floor(p.pair_no * 0.6180339887) AS phase
    FROM pairs p
    CROSS JOIN LATERAL generate_series(1, p.orders_cnt) AS k
),
timed AS (
    SELECT pair_no, nom_key, supplier_key, price, k,
           CASE WHEN last_at IS NOT NULL AND k = orders_cnt THEN last_at
                ELSE DATE '2025-01-15'
                     + floor((k - 1 + phase) * 310 / spread_cnt)::int
                     + make_time(8 + (pair_no * 3 + k * 7) % 10, (pair_no * 17 + k * 31) % 60, 0)
           END AS purchase_at,
           CASE WHEN k = orders_cnt THEN total_qty - (orders_cnt - 1) * (total_qty / orders_cnt)
                ELSE total_qty / orders_cnt
           END AS qty
    FROM expanded
)
INSERT INTO dash_graph_purchase (order_no, nom_key, supplier_key, purchase_at, qty, price)
SELECT (row_number() OVER (ORDER BY purchase_at, pair_no, k))::text,
       nom_key, supplier_key, purchase_at, qty, price
FROM timed
WHERE NOT EXISTS (SELECT 1 FROM dash_graph_purchase)
ORDER BY purchase_at, pair_no, k;

-- ==================== Сиды аудита ====================

-- Операции, инциденты и выдачи сверх нормы по дням 2025 года. Суммы за 01.01–30.11.2025 ровно:
-- операций 1000, инцидентов 177 (17,7 %), сверх нормы 273 (27,3 %). Декабрь — от 8 % сумм (80 / 14 / 22).
-- Вес дня w_d = 1 + 0.3 * sin(2*pi*doy/365 + phi), phi свой у каждого ряда. Раскладка целыми — через
-- нарастающий итог: floor(T * Σw_{≤d} / W) минус то же на предыдущий день. Простое floor(T * w_d / W)
-- здесь не годится: при ~0,5 инцидента в день оно даёт 0 каждый день и сносит все 177 инцидентов
-- на 30.11, где их становится больше, чем операций. Нарастающий итог даёт 2–4 операции,
-- 0–1 инцидент и 0–2 выдачи сверх нормы в день, поэтому incidents ≤ operations и over_norm ≤ operations
-- в каждый день; остаток (0 или 1 из-за погрешности float) — на 30.11 и 31.12.
WITH days AS (
    SELECT d::date AS stat_date,
           extract(doy FROM d)::int AS doy,
           d::date <= DATE '2025-11-30' AS in_main
    FROM generate_series(DATE '2025-01-01', DATE '2025-12-31', interval '1 day') AS d
),
weighted AS (
    SELECT stat_date, in_main,
           CASE WHEN in_main THEN 1000 ELSE 80 END AS o_total,
           CASE WHEN in_main THEN  177 ELSE 14 END AS i_total,
           CASE WHEN in_main THEN  273 ELSE 22 END AS n_total,
           1 + 0.3 * sin(2 * pi() * doy / 365 + 0.4) AS wo,
           1 + 0.3 * sin(2 * pi() * doy / 365 + 2.1) AS wi,
           1 + 0.3 * sin(2 * pi() * doy / 365 + 3.8) AS wn
    FROM days
),
running AS (
    SELECT stat_date, in_main, o_total, i_total, n_total,
           floor(o_total * sum(wo) OVER w_run / sum(wo) OVER w_all) AS o_cum,
           floor(i_total * sum(wi) OVER w_run / sum(wi) OVER w_all) AS i_cum,
           floor(n_total * sum(wn) OVER w_run / sum(wn) OVER w_all) AS n_cum
    FROM weighted
    WINDOW w_run AS (PARTITION BY in_main ORDER BY stat_date),
           w_all AS (PARTITION BY in_main)
),
daily AS (
    SELECT stat_date, in_main, o_total, i_total, n_total,
           (o_cum - COALESCE(lag(o_cum) OVER w, 0))::int AS operations,
           (i_cum - COALESCE(lag(i_cum) OVER w, 0))::int AS incidents,
           (n_cum - COALESCE(lag(n_cum) OVER w, 0))::int AS over_norm
    FROM running
    WINDOW w AS (PARTITION BY in_main ORDER BY stat_date)
),
adjusted AS (
    SELECT stat_date,
           operations + CASE WHEN stat_date IN (DATE '2025-11-30', DATE '2025-12-31')
                             THEN o_total - sum(operations) OVER (PARTITION BY in_main) ELSE 0 END AS operations,
           incidents + CASE WHEN stat_date IN (DATE '2025-11-30', DATE '2025-12-31')
                            THEN i_total - sum(incidents) OVER (PARTITION BY in_main) ELSE 0 END AS incidents,
           over_norm + CASE WHEN stat_date IN (DATE '2025-11-30', DATE '2025-12-31')
                            THEN n_total - sum(over_norm) OVER (PARTITION BY in_main) ELSE 0 END AS over_norm
    FROM daily
)
INSERT INTO dash_audit_daily (stat_date, operations, incidents, over_norm)
SELECT stat_date, operations, incidents, over_norm
FROM adjusted
ORDER BY stat_date
ON CONFLICT (stat_date) DO NOTHING;

-- ==================== Сиды ленты контроля ====================

-- 40 событий за последние 7 дней: 24 — «Цех 1, Участок 2» (участок контролёра: 6 pending / 13 passed /
-- 5 failed), 16 — другие подразделения (4 / 9 / 3). Всего 10 / 22 / 8 = 25 % / 55 % / 20 %,
-- сегодня (days_ago = 0) — 10 событий. id растут по времени события.
INSERT INTO dash_control_event (title, department, executor, controller, status, days_ago, time_of_day)
SELECT v.title, v.department, v.executor, v.controller, v.status, v.days_ago, v.time_of_day::time
FROM (VALUES
    ('Шкив',                    'Цех 1, Участок 2',     'Кетов Сергей Николаевич',      'Витков Олег Александрович', 'passed',  0, '17:40'),
    ('Лопатка',                 'Цех 1, Участок 2',     'Зверев Максим Константинович', 'Лаптева Наталья Сергеевна', 'pending', 0, '16:25'),
    ('Шестерня',                'Цех 1, Участок 2',     'Колпаков Андрей Викторович',   'Орлов Дмитрий Игоревич',    'failed',  0, '15:10'),
    ('Вал ведущий',             'Цех 1, Участок 2',     'Романова Ирина Петровна',      'Витков Олег Александрович', 'passed',  0, '13:56'),
    ('Корпус редуктора',        'Цех 1, Участок 2',     'Гусев Павел Андреевич',        'Лаптева Наталья Сергеевна', 'pending', 0, '11:30'),
    ('Фланец',                  'Цех 1, Участок 2',     'Кетов Сергей Николаевич',      'Орлов Дмитрий Игоревич',    'passed',  0, '09:15'),
    ('Втулка',                  'Цех 1, Участок 2',     'Зверев Максим Константинович', 'Витков Олег Александрович', 'pending', 1, '18:05'),
    ('Крыльчатка 200/КВ 01',    'Цех 1, Участок 2',     'Колпаков Андрей Викторович',   'Лаптева Наталья Сергеевна', 'passed',  1, '16:47'),
    ('Турбина ТС 2003',         'Цех 1, Участок 2',     'Романова Ирина Петровна',      'Орлов Дмитрий Игоревич',    'failed',  1, '14:20'),
    ('Генератор Р105',          'Цех 1, Участок 2',     'Гусев Павел Андреевич',        'Витков Олег Александрович', 'pending', 1, '11:00'),
    ('Патрубок АОК 10-70',      'Цех 1, Участок 2',     'Кетов Сергей Николаевич',      'Лаптева Наталья Сергеевна', 'passed',  1, '08:10'),
    ('Датчик кислородный N200', 'Цех 1, Участок 2',     'Зверев Максим Константинович', 'Орлов Дмитрий Игоревич',    'pending', 2, '17:24'),
    ('Привод 8100-А',           'Цех 1, Участок 2',     'Колпаков Андрей Викторович',   'Витков Олег Александрович', 'passed',  2, '15:15'),
    ('Пополнение №1002',        'Цех 1, Участок 2',     'Романова Ирина Петровна',      'Лаптева Наталья Сергеевна', 'failed',  2, '12:07'),
    ('Шкив',                    'Цех 1, Участок 2',     'Гусев Павел Андреевич',        'Орлов Дмитрий Игоревич',    'passed',  2, '09:42'),
    ('Шестерня',                'Цех 1, Участок 2',     'Кетов Сергей Николаевич',      'Витков Олег Александрович', 'pending', 3, '18:20'),
    ('Лопатка',                 'Цех 1, Участок 2',     'Зверев Максим Константинович', 'Лаптева Наталья Сергеевна', 'passed',  3, '14:48'),
    ('Вал ведущий',             'Цех 1, Участок 2',     'Колпаков Андрей Викторович',   'Орлов Дмитрий Игоревич',    'passed',  3, '10:38'),
    ('Корпус редуктора',        'Цех 1, Участок 2',     'Романова Ирина Петровна',      'Витков Олег Александрович', 'failed',  4, '16:15'),
    ('Фланец',                  'Цех 1, Участок 2',     'Гусев Павел Андреевич',        'Лаптева Наталья Сергеевна', 'passed',  4, '13:53'),
    ('Втулка',                  'Цех 1, Участок 2',     'Кетов Сергей Николаевич',      'Орлов Дмитрий Игоревич',    'passed',  4, '07:35'),
    ('Крыльчатка 200/КВ 01',    'Цех 1, Участок 2',     'Зверев Максим Константинович', 'Витков Олег Александрович', 'passed',  5, '15:52'),
    ('Турбина ТС 2003',         'Цех 1, Участок 2',     'Колпаков Андрей Викторович',   'Лаптева Наталья Сергеевна', 'failed',  5, '10:02'),
    ('Генератор Р105',          'Цех 1, Участок 2',     'Романова Ирина Петровна',      'Орлов Дмитрий Игоревич',    'passed',  6, '12:30'),
    ('Шкив',                    'Цех 1, Участок 1',     'Зверев Максим Константинович', 'Лаптева Наталья Сергеевна', 'pending', 0, '17:03'),
    ('Патрубок АОК 10-70',      'Цех 2, Участок 1',     'Гусев Павел Андреевич',        'Орлов Дмитрий Игоревич',    'passed',  0, '14:36'),
    ('Привод 8100-А',           'Цех металлорежущий',   'Кетов Сергей Николаевич',      'Витков Олег Александрович', 'failed',  0, '12:18'),
    ('Датчик кислородный N200', 'Цех металлообработки', 'Романова Ирина Петровна',      'Лаптева Наталья Сергеевна', 'passed',  0, '08:44'),
    ('Лопатка',                 'Цех 3, Участок 4',     'Колпаков Андрей Викторович',   'Орлов Дмитрий Игоревич',    'pending', 1, '17:50'),
    ('Шестерня',                'Цех 1, Участок 1',     'Кетов Сергей Николаевич',      'Витков Олег Александрович', 'passed',  1, '15:31'),
    ('Вал ведущий',             'Цех металлорежущий',   'Зверев Максим Константинович', 'Лаптева Наталья Сергеевна', 'passed',  1, '09:27'),
    ('Корпус редуктора',        'Цех 2, Участок 1',     'Романова Ирина Петровна',      'Орлов Дмитрий Игоревич',    'pending', 2, '16:40'),
    ('Фланец',                  'Цех металлообработки', 'Гусев Павел Андреевич',        'Витков Олег Александрович', 'passed',  2, '13:05'),
    ('Втулка',                  'Цех 3, Участок 4',     'Кетов Сергей Николаевич',      'Лаптева Наталья Сергеевна', 'failed',  3, '17:12'),
    ('Крыльчатка 200/КВ 01',    'Цех 1, Участок 1',     'Колпаков Андрей Викторович',   'Орлов Дмитрий Игоревич',    'passed',  3, '11:48'),
    ('Турбина ТС 2003',         'Цех металлорежущий',   'Зверев Максим Константинович', 'Витков Олег Александрович', 'pending', 4, '18:28'),
    ('Генератор Р105',          'Цех 2, Участок 1',     'Романова Ирина Петровна',      'Лаптева Наталья Сергеевна', 'passed',  4, '10:14'),
    ('Пополнение №1002',        'Цех металлообработки', 'Гусев Павел Андреевич',        'Орлов Дмитрий Игоревич',    'passed',  5, '14:22'),
    ('Шкив',                    'Цех 3, Участок 4',     'Кетов Сергей Николаевич',      'Витков Олег Александрович', 'failed',  5, '08:55'),
    ('Шестерня',                'Цех металлорежущий',   'Колпаков Андрей Викторович',   'Лаптева Наталья Сергеевна', 'passed',  6, '16:33')
) AS v(title, department, executor, controller, status, days_ago, time_of_day)
WHERE NOT EXISTS (SELECT 1 FROM dash_control_event)
ORDER BY v.days_ago DESC, v.time_of_day::time ASC;

-- ==================== Сиды экрана событий дня ====================

-- Склад, контроль качества и выпуск: по 12 событий на источник, 5 из них «в работе» (~40 %).
-- sort_order — порядок внутри источника при равном времени.
INSERT INTO dash_day_event (source, title, person, status_label, tone, done, time_of_day, sort_order)
SELECT v.source, v.title, v.person, v.status_label, v.tone, v.done, v.time_of_day::time, v.sort_order
FROM (VALUES
    ('operator', 'Пополнение станции Гибридная',  'Колпаков А.В.', 'В работе',            'progress', false, '17:40',  1),
    ('operator', 'Выдача ТМЦ на участок 2',       'Зверев М.К.',   'В работе',            'progress', false, '16:25',  2),
    ('operator', 'Перемещение',                   'Романова И.П.', 'Завершено',           'success',  true,  '15:48',  3),
    ('operator', 'Приёмка поставки № 214',        'Колпаков А.В.', 'В работе',            'progress', false, '14:05',  4),
    ('operator', 'Пополнение станции Гибридная',  'Зверев М.К.',   'Завершено',           'success',  true,  '13:30',  5),
    ('operator', 'Списание',                      'Романова И.П.', 'Завершено',           'success',  true,  '12:45',  6),
    ('operator', 'Инвентаризация станции 3',      'Колпаков А.В.', 'В работе',            'progress', false, '11:56',  7),
    ('operator', 'Выдача ТМЦ на участок 2',       'Романова И.П.', 'Завершено',           'success',  true,  '10:48',  8),
    ('operator', 'Пополнение станции Гибридная',  'Романова И.П.', 'Завершено',           'success',  true,  '10:13',  9),
    ('operator', 'Возврат ТМЦ на склад',          'Зверев М.К.',   'В работе',            'progress', false, '09:20', 10),
    ('operator', 'Перемещение',                   'Колпаков А.В.', 'Завершено',           'success',  true,  '08:35', 11),
    ('operator', 'Списание',                      'Зверев М.К.',   'Завершено',           'success',  true,  '07:50', 12),
    ('control',  'Контроль: Шкив',                'Витков О.А.',   'На контроле',         'progress', false, '17:10',  1),
    ('control',  'Контроль: Лопатка',             'Лаптева Н.С.',  'На контроле',         'progress', false, '16:02',  2),
    ('control',  'Контроль: Корпус редуктора',    'Орлов Д.И.',    'Контроль пройден',    'success',  true,  '15:25',  3),
    ('control',  'Контроль: Шестерня',            'Орлов Д.И.',    'На контроле',         'progress', false, '14:48',  4),
    ('control',  'Контроль: Вал ведущий',         'Витков О.А.',   'На контроле',         'progress', false, '13:56',  5),
    ('control',  'Контроль: Втулка',              'Витков О.А.',   'Контроль не пройден', 'danger',   true,  '12:30',  6),
    ('control',  'Контроль: Фланец',              'Лаптева Н.С.',  'На контроле',         'progress', false, '11:40',  7),
    ('control',  'Контроль: Крыльчатка 200/КВ 01', 'Лаптева Н.С.', 'Контроль пройден',    'success',  true,  '10:15',  8),
    ('control',  'Контроль: Турбина ТС 2003',     'Орлов Д.И.',    'Контроль пройден',    'success',  true,  '09:42',  9),
    ('control',  'Контроль: Генератор Р105',      'Витков О.А.',   'Контроль не пройден', 'danger',   true,  '08:58', 10),
    ('control',  'Контроль: Патрубок АОК 10-70',  'Лаптева Н.С.',  'Контроль пройден',    'success',  true,  '08:20', 11),
    ('control',  'Контроль: Привод 8100-А',       'Орлов Д.И.',    'Контроль пройден',    'success',  true,  '07:45', 12),
    ('release',  'Выпуск: Шкив',                  'Кетов С.Н.',    'В работе',            'progress', false, '17:25',  1),
    ('release',  'Выпуск: ГД 1',                  'Зверев М.К.',   'В работе',            'progress', false, '16:10',  2),
    ('release',  'Выпуск: ГД 2',                  'Кетов С.Н.',    'Выпущено',            'success',  true,  '15:50',  3),
    ('release',  'Выпуск: ГД 4',                  'Гусев П.А.',    'В работе',            'progress', false, '14:35',  4),
    ('release',  'Выпуск: Шестерня',              'Колпаков А.В.', 'В работе',            'progress', false, '13:20',  5),
    ('release',  'Выпуск: ГД 3',                  'Зверев М.К.',   'Выпущено',            'success',  true,  '12:40',  6),
    ('release',  'Выпуск: ГД 6',                  'Романова И.П.', 'В работе',            'progress', false, '11:05',  7),
    ('release',  'Выпуск: Шкив',                  'Колпаков А.В.', 'Выпущено',            'success',  true,  '10:30',  8),
    ('release',  'Выпуск: ГД 5',                  'Гусев П.А.',    'Выпущено',            'success',  true,  '09:55',  9),
    ('release',  'Выпуск: Шестерня',              'Романова И.П.', 'Выпущено',            'success',  true,  '09:10', 10),
    ('release',  'Выпуск: ГД 1',                  'Кетов С.Н.',    'Выпущено',            'success',  true,  '08:25', 11),
    ('release',  'Выпуск: ГД 2',                  'Зверев М.К.',   'Выпущено',            'success',  true,  '07:40', 12)
) AS v(source, title, person, status_label, tone, done, time_of_day, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM dash_day_event WHERE source IN ('operator', 'control', 'release'))
ORDER BY v.source, v.time_of_day::time ASC;

-- Закупки с завышенной ценой: 12 последних заказов с ценой выше нормативной более чем на 20 %
-- (те же, что в ленте аудитора), номер заказа берётся из dash_graph_purchase — заголовки совпадают
-- с лентой. 5 «На проверке», 5 «Проверено», 2 «Отклонено».
WITH overpriced AS (
    SELECT p.order_no, n.name AS nom_name,
           row_number() OVER (ORDER BY p.purchase_at DESC, p.id DESC) AS rn
    FROM dash_graph_purchase p
    JOIN dash_graph_nomenclature n ON n.nom_key = p.nom_key
    WHERE p.price > n.ref_price * 1.2
),
slots(rn, status_label, tone, done, time_of_day) AS (VALUES
    ( 1, 'На проверке', 'progress', false, '17:20'),
    ( 2, 'На проверке', 'progress', false, '16:05'),
    ( 3, 'На проверке', 'progress', false, '14:40'),
    ( 4, 'Проверено',   'success',  true,  '13:55'),
    ( 5, 'На проверке', 'progress', false, '12:30'),
    ( 6, 'Отклонено',   'danger',   true,  '11:45'),
    ( 7, 'Проверено',   'success',  true,  '11:10'),
    ( 8, 'На проверке', 'progress', false, '10:25'),
    ( 9, 'Проверено',   'success',  true,  '09:50'),
    (10, 'Отклонено',   'danger',   true,  '09:05'),
    (11, 'Проверено',   'success',  true,  '08:30'),
    (12, 'Проверено',   'success',  true,  '07:45')
)
INSERT INTO dash_day_event (source, title, person, status_label, tone, done, time_of_day, sort_order)
SELECT 'overpriced',
       'Проверка закупки: ' || o.nom_name || ' (заказ ' || o.order_no || ')',
       'Аудитор Смирнова Е.В.',
       s.status_label, s.tone, s.done, s.time_of_day::time, s.rn
FROM overpriced o
JOIN slots s ON s.rn = o.rn
WHERE NOT EXISTS (SELECT 1 FROM dash_day_event WHERE source = 'overpriced')
ORDER BY s.time_of_day::time ASC;
