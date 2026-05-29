-- V3__Add_type_hierarchy.sql

BEGIN;

-- 1. Добавляем связь "Группа учета -> Группа номенклатуры"
ALTER TABLE IF EXISTS public.spr_type_purpose
    ADD COLUMN IF NOT EXISTS type_material_uid uuid;

ALTER TABLE IF EXISTS public.spr_type_purpose
    ADD CONSTRAINT spr_type_purpose_type_material_fkey 
    FOREIGN KEY (type_material_uid)
    REFERENCES public.spr_type_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE SET NULL;

COMMENT ON COLUMN public.spr_type_purpose.type_material_uid
    IS 'Ссылка на группу учета (ТМЦ, Готовые детали и т.д.)';

-- 2. Добавляем связь "Группа номенклатуры -> Вид номенклатуры"
ALTER TABLE IF EXISTS public.spr_type_product
    ADD COLUMN IF NOT EXISTS type_purpose_uid uuid;

ALTER TABLE IF EXISTS public.spr_type_product
    ADD CONSTRAINT spr_type_product_type_purpose_fkey 
    FOREIGN KEY (type_purpose_uid)
    REFERENCES public.spr_type_purpose (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE SET NULL;

COMMENT ON COLUMN public.spr_type_product.type_purpose_uid
    IS 'Ссылка на группу номенклатуры';

-- 3. Заполняем тестовыми данными
-- Сначала получим uid'ы для групп учета (они создаются отдельно, пока используем подзапросы)
-- Если таблицы пустые — вставим базовые значения

-- Группы учета
INSERT INTO public.spr_type_material (uid, type_name)
SELECT gen_random_uuid(), 'ТМЦ'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_material WHERE type_name = 'ТМЦ');

INSERT INTO public.spr_type_material (uid, type_name)
SELECT gen_random_uuid(), 'Готовая деталь'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_material WHERE type_name = 'Готовая деталь');

-- Группы номенклатуры для ТМЦ
DO $$
DECLARE
    tmc_uid uuid;
    ready_uid uuid;
BEGIN
    SELECT uid INTO tmc_uid FROM public.spr_type_material WHERE type_name = 'ТМЦ';
    SELECT uid INTO ready_uid FROM public.spr_type_material WHERE type_name = 'Готовая деталь';

    -- Для ТМЦ
    INSERT INTO public.spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Металлообрабатывающий инструмент', tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент');

    INSERT INTO public.spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Слесарный инструмент', tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_purpose WHERE type_name = 'Слесарный инструмент');

    INSERT INTO public.spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Оснастка', tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_purpose WHERE type_name = 'Оснастка');

    -- Для Готовых деталей
    INSERT INTO public.spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Готовые детали', ready_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_purpose WHERE type_name = 'Готовые детали' AND type_material_uid = ready_uid);
END $$;

-- Виды номенклатуры
DO $$
DECLARE
    metal_uid uuid;
    slesar_uid uuid;
    osnastka_uid uuid;
    ready_detail_uid uuid;
BEGIN
    SELECT uid INTO metal_uid FROM public.spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент';
    SELECT uid INTO slesar_uid FROM public.spr_type_purpose WHERE type_name = 'Слесарный инструмент';
    SELECT uid INTO osnastka_uid FROM public.spr_type_purpose WHERE type_name = 'Оснастка';
    SELECT uid INTO ready_detail_uid FROM public.spr_type_purpose WHERE type_name = 'Готовые детали';

    -- Для Металлообрабатывающего инструмента
    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Сверло', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Сверло' AND type_purpose_uid = metal_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Фреза', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Фреза' AND type_purpose_uid = metal_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Резец', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Резец' AND type_purpose_uid = metal_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Метчик', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Метчик' AND type_purpose_uid = metal_uid);

    -- Для Слесарного инструмента
    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Молоток', slesar_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Молоток' AND type_purpose_uid = slesar_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Отвертка', slesar_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Отвертка' AND type_purpose_uid = slesar_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Ключ гаечный', slesar_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Ключ гаечный' AND type_purpose_uid = slesar_uid);

    -- Для Оснастки
    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Тиски', osnastka_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Тиски' AND type_purpose_uid = osnastka_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Патрон', osnastka_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Патрон' AND type_purpose_uid = osnastka_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Кондуктор', osnastka_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Кондуктор' AND type_purpose_uid = osnastka_uid);

    -- Для Готовых деталей
    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Вал', ready_detail_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Вал' AND type_purpose_uid = ready_detail_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Втулка', ready_detail_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Втулка' AND type_purpose_uid = ready_detail_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Корпус', ready_detail_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Корпус' AND type_purpose_uid = ready_detail_uid);

    INSERT INTO public.spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Крышка', ready_detail_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_type_product WHERE type_name = 'Крышка' AND type_purpose_uid = ready_detail_uid);
END $$;

COMMIT;