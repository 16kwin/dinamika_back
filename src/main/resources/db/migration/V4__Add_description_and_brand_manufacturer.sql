-- V4__Add_description_and_brand_manufacturer.sql

BEGIN;

-- Добавляем description в spr_measure
ALTER TABLE IF EXISTS public.spr_measure
    ADD COLUMN IF NOT EXISTS description text COLLATE pg_catalog."default";

COMMENT ON COLUMN public.spr_measure.description IS 'Описание единицы измерения';

-- Добавляем description в spr_manufacturer
ALTER TABLE IF EXISTS public.spr_manufacturer
    ADD COLUMN IF NOT EXISTS description text COLLATE pg_catalog."default";

COMMENT ON COLUMN public.spr_manufacturer.description IS 'Описание производителя';

-- Добавляем описание и связь с производителем в spr_brand
ALTER TABLE IF EXISTS public.spr_brand
    ADD COLUMN IF NOT EXISTS description text COLLATE pg_catalog."default";

ALTER TABLE IF EXISTS public.spr_brand
    ADD COLUMN IF NOT EXISTS manufacturer_uid uuid;

ALTER TABLE IF EXISTS public.spr_brand
    ADD CONSTRAINT spr_brand_manufacturer_fkey 
    FOREIGN KEY (manufacturer_uid)
    REFERENCES public.spr_manufacturer (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE SET NULL;

COMMENT ON COLUMN public.spr_brand.description IS 'Описание бренда';
COMMENT ON COLUMN public.spr_brand.manufacturer_uid IS 'Ссылка на производителя';

-- Добавляем description в spr_model_of_brand
ALTER TABLE IF EXISTS public.spr_model_of_brand
    ADD COLUMN IF NOT EXISTS description text COLLATE pg_catalog."default";

COMMENT ON COLUMN public.spr_model_of_brand.description IS 'Описание модели';

-- Заполняем тестовыми данными (если таблицы пустые)

-- Единицы измерения
INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'мм', 'Миллиметр'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'мм');

INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'см', 'Сантиметр'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'см');

INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'м', 'Метр'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'м');

INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'шт', 'Штука'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'шт');

INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'кг', 'Килограмм'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'кг');

INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'л', 'Литр'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'л');

INSERT INTO public.spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'компл', 'Комплект'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_measure WHERE name = 'компл');

-- Производители
INSERT INTO public.spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "СтанкоДеталь"', 'Производство оснастки'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"');

INSERT INTO public.spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'АО "ПромТех"', 'Промышленное оборудование'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_manufacturer WHERE name = 'АО "ПромТех"');

INSERT INTO public.spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ИП Иванов', 'Метизы'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_manufacturer WHERE name = 'ИП Иванов');

INSERT INTO public.spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "СмазТех"', 'Смазочные материалы'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_manufacturer WHERE name = 'ООО "СмазТех"');

INSERT INTO public.spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'АО "ЭлектроПром"', 'Электрооборудование'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_manufacturer WHERE name = 'АО "ЭлектроПром"');

-- Бренды
DO $$
DECLARE
    stanko_uid uuid;
    promtex_uid uuid;
    electroprom_uid uuid;
BEGIN
    SELECT uid INTO stanko_uid FROM public.spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"';
    SELECT uid INTO promtex_uid FROM public.spr_manufacturer WHERE name = 'АО "ПромТех"';
    SELECT uid INTO electroprom_uid FROM public.spr_manufacturer WHERE name = 'АО "ЭлектроПром"';

    INSERT INTO public.spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'SKF', 'Подшипники SKF', stanko_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_brand WHERE name = 'SKF');

    INSERT INTO public.spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'FAG', 'Подшипники FAG', stanko_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_brand WHERE name = 'FAG');

    INSERT INTO public.spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'Gates', 'Ремни Gates', promtex_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_brand WHERE name = 'Gates');

    INSERT INTO public.spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'Mobil', 'Смазки Mobil', promtex_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_brand WHERE name = 'Mobil');

    INSERT INTO public.spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'Shell', 'Масла Shell', electroprom_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_brand WHERE name = 'Shell');
END $$;

-- Модели
DO $$
DECLARE
    skf_uid uuid;
    fag_uid uuid;
    gates_uid uuid;
    mobil_uid uuid;
BEGIN
    SELECT uid INTO skf_uid FROM public.spr_brand WHERE name = 'SKF';
    SELECT uid INTO fag_uid FROM public.spr_brand WHERE name = 'FAG';
    SELECT uid INTO gates_uid FROM public.spr_brand WHERE name = 'Gates';
    SELECT uid INTO mobil_uid FROM public.spr_brand WHERE name = 'Mobil';

    INSERT INTO public.spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), '6204-2RS', 'Шариковый радиальный', skf_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_model_of_brand WHERE name = '6204-2RS');

    INSERT INTO public.spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), '6205-C3', 'Шариковый радиальный', fag_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_model_of_brand WHERE name = '6205-C3');

    INSERT INTO public.spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), '6306-ZZ', 'Шариковый с защитой', skf_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_model_of_brand WHERE name = '6306-ZZ');

    INSERT INTO public.spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), 'A-1000', 'Ремень клиновой', gates_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_model_of_brand WHERE name = 'A-1000');

    INSERT INTO public.spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), 'Mobilux EP2', 'Смазка литиевая', mobil_uid
    WHERE NOT EXISTS (SELECT 1 FROM public.spr_model_of_brand WHERE name = 'Mobilux EP2');
END $$;

-- Страны
INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'Россия'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'Россия');

INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'Германия'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'Германия');

INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'Япония'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'Япония');

INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'США'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'США');

INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'Китай'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'Китай');

INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'Италия'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'Италия');

INSERT INTO public.spr_country (uid, name)
SELECT gen_random_uuid(), 'Франция'
WHERE NOT EXISTS (SELECT 1 FROM public.spr_country WHERE name = 'Франция');

COMMIT;