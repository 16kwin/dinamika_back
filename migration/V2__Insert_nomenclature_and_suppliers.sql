-- V2__Insert_nomenclature_and_suppliers.sql
-- Загрузка 10 сверл твердосплавных New Century Drill 5XD + медиа
-- Загрузка 6 поставщиков с реквизитами и логотипами

BEGIN;

-- ============================================================
-- 1. СПРАВОЧНИКИ (создать если нет)
-- ============================================================

-- Единицы измерения
INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'мм', 'Миллиметр'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'мм');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'град', 'Градус'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'град');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'кг', 'Килограмм'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'кг');

-- Страны
INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Китай'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Китай');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Россия'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Россия');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Германия'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Германия');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Беларусь'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Беларусь');

-- Производители
INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'New Century', 'New Century Drill'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'New Century');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "ПромСнаб"', 'Ведущий поставщик промышленного оборудования'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ООО "ПромСнаб"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'АО "ТехКомплект"', 'Комплексные поставки для машиностроения'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'АО "ТехКомплект"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ИП Иванов А.А.', 'Индивидуальный предприниматель, метизы и крепеж'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ИП Иванов А.А.');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "МетизТорг"', 'Оптовая торговля металлоизделиями'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ООО "МетизТорг"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ЗАО "ИнструментСервис"', 'Сервисное обслуживание и поставка инструмента'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ЗАО "ИнструментСервис"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "ЗАДЕЛ"', 'Производитель заделов и оснастки'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ООО "ЗАДЕЛ"');

-- Бренды
DO $$
DECLARE
    v_nc_uid UUID;
    v_promsnab_uid UUID;
    v_techkomplekt_uid UUID;
    v_ivanov_uid UUID;
    v_metiztorg_uid UUID;
    v_instrumentservis_uid UUID;
    v_zadel_uid UUID;
BEGIN
    SELECT uid INTO v_nc_uid FROM spr_manufacturer WHERE name = 'New Century';
    SELECT uid INTO v_promsnab_uid FROM spr_manufacturer WHERE name = 'ООО "ПромСнаб"';
    SELECT uid INTO v_techkomplekt_uid FROM spr_manufacturer WHERE name = 'АО "ТехКомплект"';
    SELECT uid INTO v_ivanov_uid FROM spr_manufacturer WHERE name = 'ИП Иванов А.А.';
    SELECT uid INTO v_metiztorg_uid FROM spr_manufacturer WHERE name = 'ООО "МетизТорг"';
    SELECT uid INTO v_instrumentservis_uid FROM spr_manufacturer WHERE name = 'ЗАО "ИнструментСервис"';
    SELECT uid INTO v_zadel_uid FROM spr_manufacturer WHERE name = 'ООО "ЗАДЕЛ"';

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'New Century', 'New Century Drill', v_nc_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'New Century');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'PromSnab', 'Собственный бренд ПромСнаб', v_promsnab_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'PromSnab');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'TechKomplekt', 'Бренд ТехКомплект', v_techkomplekt_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'TechKomplekt');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'IvanovMetiz', 'Бренд Иванов Метиз', v_ivanov_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'IvanovMetiz');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'MetizTrade', 'Бренд МетизТорг', v_metiztorg_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'MetizTrade');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'InstrumentService', 'Бренд ИнструментСервис', v_instrumentservis_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'InstrumentService');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'ZADEL', 'Бренд ЗАДЕЛ', v_zadel_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'ZADEL');
END $$;

-- Модель DH224
DO $$
DECLARE
    v_brand_uid UUID;
BEGIN
    SELECT uid INTO v_brand_uid FROM spr_brand WHERE name = 'New Century';
    
    INSERT INTO spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), 'DH224', 'Серия DH224 5XD', v_brand_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_model_of_brand WHERE name = 'DH224');
END $$;

-- Группа учета ТМЦ
INSERT INTO spr_type_material (uid, type_name)
SELECT gen_random_uuid(), 'ТМЦ'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_material WHERE type_name = 'ТМЦ');

-- Группа номенклатуры "Металлообрабатывающий инструмент"
DO $$
DECLARE
    v_tmc_uid UUID;
BEGIN
    SELECT uid INTO v_tmc_uid FROM spr_type_material WHERE type_name = 'ТМЦ';
    
    INSERT INTO spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Металлообрабатывающий инструмент', v_tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент');
END $$;

-- Вид номенклатуры "Сверло"
DO $$
DECLARE
    v_purpose_uid UUID;
BEGIN
    SELECT uid INTO v_purpose_uid FROM spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент';
    
    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Сверло', v_purpose_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Сверло');
END $$;

-- Группа материалов "Сверла твердосплавные"
DO $$
DECLARE
    v_root_uid UUID;
BEGIN
    SELECT uid INTO v_root_uid FROM reg_group_material WHERE parent_group IS NULL LIMIT 1;
    
    IF v_root_uid IS NOT NULL THEN
        INSERT INTO reg_group_material (uid, group_name, parent_group, group_code)
        SELECT gen_random_uuid(), 'Сверла твердосплавные', v_root_uid, COALESCE((SELECT MAX(group_code) FROM reg_group_material) + 1, 1)
        WHERE NOT EXISTS (SELECT 1 FROM reg_group_material WHERE group_name = 'Сверла твердосплавные');
    ELSE
        INSERT INTO reg_group_material (uid, group_name, parent_group, group_code)
        SELECT gen_random_uuid(), 'Сверла твердосплавные', NULL, 1
        WHERE NOT EXISTS (SELECT 1 FROM reg_group_material WHERE group_name = 'Сверла твердосплавные');
    END IF;
END $$;

-- Виды характеристик
INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Стандарт исполнения', 'ГОСТ/DIN'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Стандарт исполнения');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Покрытие', 'Покр.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Покрытие');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Тип хвостовика', 'Хвост.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Тип хвостовика');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Глубина сверления', 'Глуб.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Глубина сверления');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Угол заточки', 'Уг.зат.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Угол заточки');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Тип охлаждения', 'Охл.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Тип охлаждения');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Материал инструмента', 'Матер.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Материал инструмента');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Назначение', 'Назн.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Назначение');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Группа обрабатываемых материалов', 'Гр.обр.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Группа обрабатываемых материалов');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Особенность инструмента', 'Особ.'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Особенность инструмента');

-- Типы описаний поставщиков
INSERT INTO spr_supplier_description_types (uid, name)
SELECT gen_random_uuid(), 'Производитель'
WHERE NOT EXISTS (SELECT 1 FROM spr_supplier_description_types WHERE name = 'Производитель');

INSERT INTO spr_supplier_description_types (uid, name)
SELECT gen_random_uuid(), 'Официальный дистрибьютор'
WHERE NOT EXISTS (SELECT 1 FROM spr_supplier_description_types WHERE name = 'Официальный дистрибьютор');

INSERT INTO spr_supplier_description_types (uid, name)
SELECT gen_random_uuid(), 'Оптовый поставщик'
WHERE NOT EXISTS (SELECT 1 FROM spr_supplier_description_types WHERE name = 'Оптовый поставщик');

INSERT INTO spr_supplier_description_types (uid, name)
SELECT gen_random_uuid(), 'Дилер'
WHERE NOT EXISTS (SELECT 1 FROM spr_supplier_description_types WHERE name = 'Дилер');

INSERT INTO spr_supplier_description_types (uid, name)
SELECT gen_random_uuid(), 'Импортер'
WHERE NOT EXISTS (SELECT 1 FROM spr_supplier_description_types WHERE name = 'Импортер');

-- ============================================================
-- 2. ЗАГРУЗКА 10 СВЁРЛ
-- ============================================================

DO $$
DECLARE
    v_tmc_uid UUID;
    v_purpose_uid UUID;
    v_product_uid UUID;
    v_measure_mm_uid UUID;
    v_measure_deg_uid UUID;
    v_manufacturer_uid UUID;
    v_brand_uid UUID;
    v_model_uid UUID;
    v_country_uid UUID;
    v_group_uid UUID;
    
    v_attr_length_uid UUID;
    v_attr_width_uid UUID;
    v_attr_height_uid UUID;
    v_attr_mass_uid UUID;
    v_attr_standard_uid UUID;
    v_attr_coating_uid UUID;
    v_attr_shank_uid UUID;
    v_attr_depth_uid UUID;
    v_attr_angle_uid UUID;
    v_attr_cooling_uid UUID;
    v_attr_material_uid UUID;
    v_attr_purpose_uid UUID;
    v_attr_material_group_uid UUID;
    v_attr_feature_uid UUID;
    
    v_material_uid UUID;
    v_photo_uid UUID;
    v_blueprint_uid UUID;
    v_code INTEGER;
    
    v_data TEXT[][] := ARRAY[
        ARRAY['1', '8', 'DH2240100', 'Сверло твердосплавное 5XD с покрытием TiАIN 1X3X8X55'],
        ARRAY['1.1', '12', 'DH2240110', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.1X3X12X55'],
        ARRAY['1.2', '12', 'DH2240120', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.2X3X12X55'],
        ARRAY['1.3', '12', 'DH2240130', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.3X3X12X55'],
        ARRAY['1.4', '12', 'DH2240140', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.4X3X12X55'],
        ARRAY['1.5', '16', 'DH2240150', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.5X3X16X55'],
        ARRAY['1.6', '16', 'DH2240160', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.6X3X16X55'],
        ARRAY['1.7', '16', 'DH2240170', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.7X3X16X55'],
        ARRAY['1.8', '16', 'DH2240180', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.8X3X16X55'],
        ARRAY['1.83', '16', 'DH2240183', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.83X3X16X55']
    ];
    
    v_description TEXT := 'Спиральное сверло из твердого сплава с правым вращением и цилиндрическим хвостовиком без каналов для подачи охлаждающей жидкости. Идеально подходит для сверления стали общего назначения, легированных сталей, чугуна и закаленных материалов до HRc 55. Не требует предварительной зацентровки, так как самоцентрируется. Специальная конструкция исключает необходимость развертывания отверстий. Эффективный отвод стружки повышает производительность. Покрытие из нитрида титана-алюминия (TiAlN) увеличивает износостойкость и улучшает рабочие характеристики инструмента.';
    
    v_idx INTEGER;
    v_diameter TEXT;
    v_flute_length TEXT;
    v_article TEXT;
    v_name TEXT;
    v_photo_filename TEXT;
    v_blueprint_filename TEXT;
BEGIN
    SELECT uid INTO v_tmc_uid FROM spr_type_material WHERE type_name = 'ТМЦ';
    SELECT uid INTO v_purpose_uid FROM spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент';
    SELECT uid INTO v_product_uid FROM spr_type_product WHERE type_name = 'Сверло';
    SELECT uid INTO v_measure_mm_uid FROM spr_measure WHERE name = 'шт';
    SELECT uid INTO v_measure_deg_uid FROM spr_measure WHERE name = 'град';
    SELECT uid INTO v_manufacturer_uid FROM spr_manufacturer WHERE name = 'New Century';
    SELECT uid INTO v_brand_uid FROM spr_brand WHERE name = 'New Century';
    SELECT uid INTO v_model_uid FROM spr_model_of_brand WHERE name = 'DH224';
    SELECT uid INTO v_country_uid FROM spr_country WHERE name = 'Китай';
    SELECT uid INTO v_group_uid FROM reg_group_material WHERE group_name = 'Сверла твердосплавные';
    
    SELECT uid INTO v_attr_length_uid FROM spr_type_attributes WHERE name = 'Длина';
    SELECT uid INTO v_attr_width_uid FROM spr_type_attributes WHERE name = 'Ширина';
    SELECT uid INTO v_attr_height_uid FROM spr_type_attributes WHERE name = 'Высота';
    SELECT uid INTO v_attr_mass_uid FROM spr_type_attributes WHERE name = 'Масса';
    SELECT uid INTO v_attr_standard_uid FROM spr_type_attributes WHERE name = 'Стандарт исполнения';
    SELECT uid INTO v_attr_coating_uid FROM spr_type_attributes WHERE name = 'Покрытие';
    SELECT uid INTO v_attr_shank_uid FROM spr_type_attributes WHERE name = 'Тип хвостовика';
    SELECT uid INTO v_attr_depth_uid FROM spr_type_attributes WHERE name = 'Глубина сверления';
    SELECT uid INTO v_attr_angle_uid FROM spr_type_attributes WHERE name = 'Угол заточки';
    SELECT uid INTO v_attr_cooling_uid FROM spr_type_attributes WHERE name = 'Тип охлаждения';
    SELECT uid INTO v_attr_material_uid FROM spr_type_attributes WHERE name = 'Материал инструмента';
    SELECT uid INTO v_attr_purpose_uid FROM spr_type_attributes WHERE name = 'Назначение';
    SELECT uid INTO v_attr_material_group_uid FROM spr_type_attributes WHERE name = 'Группа обрабатываемых материалов';
    SELECT uid INTO v_attr_feature_uid FROM spr_type_attributes WHERE name = 'Особенность инструмента';
    
    SELECT COALESCE(MAX(code_material), 0) INTO v_code FROM spr_material;
    
    FOR v_idx IN 1..10 LOOP
        v_diameter := v_data[v_idx][1];
        v_flute_length := v_data[v_idx][2];
        v_article := v_data[v_idx][3];
        v_name := v_data[v_idx][4];
        
        v_code := v_code + 1;
        v_material_uid := gen_random_uuid();
        
        INSERT INTO spr_material (
            uid, code_material, name_material, article, description,
            group_material, type_main, type_purpose, type_product,
            manufacturer, brand, model_of_brand, country, measure,
            usage, resharpen, waste_material, recycle_material
        ) VALUES (
            v_material_uid, v_code, v_name, v_article, v_description,
            v_group_uid, v_tmc_uid, v_purpose_uid, v_product_uid,
            v_manufacturer_uid, v_brand_uid, v_model_uid, v_country_uid, v_measure_mm_uid,
            true, false, false, false
        );
        
        -- Характеристики
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_length_uid, '55', v_measure_mm_uid, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_width_uid, v_diameter, v_measure_mm_uid, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_height_uid, '3', v_measure_mm_uid, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_mass_uid, '0', v_measure_mm_uid, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_standard_uid, 'DIN6539', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_coating_uid, 'TiAlN', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_shank_uid, 'HA-Цилиндр', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_depth_uid, '5xD', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_angle_uid, '140', v_measure_deg_uid, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_cooling_uid, 'Внешнее', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_material_uid, 'HM-Твердый сплав', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_purpose_uid, 'Универсальные', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_material_group_uid, 'P-стали; M-нержавеющие стали; K-чугун; H-Твердые закаленные материалы', NULL, v_material_uid);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid)
        VALUES (gen_random_uuid(), v_attr_feature_uid, 'Удлиненное (от 5 до 10хD)', NULL, v_material_uid);
        
        -- Фото
        v_photo_uid := gen_random_uuid();
        v_photo_filename := v_photo_uid::text || '.png';
        INSERT INTO spr_material_images (uid, material_uid, file_path, original_name, sort_order, created_at)
        VALUES (v_photo_uid, v_material_uid, v_photo_filename, v_article || '_photo.png', 0, NOW());
        
        -- Чертеж
        v_blueprint_uid := gen_random_uuid();
        v_blueprint_filename := v_blueprint_uid::text || '.png';
        INSERT INTO spr_material_blueprints (uid, material_uid, file_path, original_name, created_at)
        VALUES (v_blueprint_uid, v_material_uid, v_blueprint_filename, v_article || '_blueprint.png', NOW());
        
    END LOOP;
    
END $$;

-- ============================================================
-- 3. ЗАГРУЗКА 6 ПОСТАВЩИКОВ
-- ============================================================

DO $$
DECLARE
    v_country_rus UUID;
    v_country_ger UUID;
    v_country_chn UUID;
    v_country_blr UUID;
    
    v_desc_producer UUID;
    v_desc_distributor UUID;
    v_desc_wholesale UUID;
    v_desc_dealer UUID;
    v_desc_importer UUID;
    
    v_brand_promsnab UUID;
    v_brand_techkomplekt UUID;
    v_brand_ivanov UUID;
    v_brand_metiztorg UUID;
    v_brand_instrumentservice UUID;
    v_brand_zadel UUID;
    
    v_code INTEGER;
    v_supplier_uid UUID;
    v_logo_uid UUID;
    v_logo_filename TEXT;
BEGIN
    SELECT uid INTO v_country_rus FROM spr_country WHERE name = 'Россия';
    SELECT uid INTO v_country_ger FROM spr_country WHERE name = 'Германия';
    SELECT uid INTO v_country_chn FROM spr_country WHERE name = 'Китай';
    SELECT uid INTO v_country_blr FROM spr_country WHERE name = 'Беларусь';
    
    SELECT uid INTO v_desc_producer FROM spr_supplier_description_types WHERE name = 'Производитель';
    SELECT uid INTO v_desc_distributor FROM spr_supplier_description_types WHERE name = 'Официальный дистрибьютор';
    SELECT uid INTO v_desc_wholesale FROM spr_supplier_description_types WHERE name = 'Оптовый поставщик';
    SELECT uid INTO v_desc_dealer FROM spr_supplier_description_types WHERE name = 'Дилер';
    SELECT uid INTO v_desc_importer FROM spr_supplier_description_types WHERE name = 'Импортер';
    
    SELECT uid INTO v_brand_promsnab FROM spr_brand WHERE name = 'PromSnab';
    SELECT uid INTO v_brand_techkomplekt FROM spr_brand WHERE name = 'TechKomplekt';
    SELECT uid INTO v_brand_ivanov FROM spr_brand WHERE name = 'IvanovMetiz';
    SELECT uid INTO v_brand_metiztorg FROM spr_brand WHERE name = 'MetizTrade';
    SELECT uid INTO v_brand_instrumentservice FROM spr_brand WHERE name = 'InstrumentService';
    SELECT uid INTO v_brand_zadel FROM spr_brand WHERE name = 'ZADEL';
    
    SELECT COALESCE(MAX(code), 0) INTO v_code FROM spr_suppliers;
    
    -- Поставщик 1: ООО "ПромСнаб"
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (
        uid, code, name, country_uid, address, short_description_uid, description,
        email, website, phone, brand_uid,
        inn, ogrn, kpp,
        contact_person, contact_position, contact_phone,
        director, director_position,
        bank_name, bik, correspondent_account, settlement_account
    ) VALUES (
        v_supplier_uid, v_code, 'ООО "ПромСнаб"',
        v_country_rus, '125212, г. Москва, ул. Адмирала Макарова, д. 10, стр. 1, офис 45',
        v_desc_producer,
        'Ведущий российский производитель и поставщик промышленного оборудования. Более 20 лет на рынке. Специализация: металлообрабатывающие станки, оснастка, комплектующие.',
        'info@promsnab.ru', 'www.promsnab.ru', '+7 (495) 123-45-67',
        v_brand_promsnab,
        '7712345678', '1027700123456', '771201001',
        'Петров Сергей Владимирович', 'Руководитель отдела продаж', '+7 (495) 123-45-68',
        'Кузнецов Алексей Николаевич', 'Генеральный директор',
        'ПАО "Сбербанк"', '044525225', '30101810400000000225', '40702810900000000123'
    );
    v_logo_uid := gen_random_uuid();
    v_logo_filename := v_logo_uid::text || '.svg';
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at)
    VALUES (v_logo_uid, v_supplier_uid, v_logo_filename, 'PromSnab_logo.svg', 0, NOW());
    
    -- Поставщик 2: АО "ТехКомплект"
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (
        uid, code, name, country_uid, address, short_description_uid, description,
        email, website, phone, brand_uid,
        inn, ogrn, kpp,
        contact_person, contact_position, contact_phone,
        director, director_position,
        bank_name, bik, correspondent_account, settlement_account
    ) VALUES (
        v_supplier_uid, v_code, 'АО "ТехКомплект"',
        v_country_rus, '620014, г. Екатеринбург, ул. Малышева, д. 51, офис 302',
        v_desc_distributor,
        'Официальный дистрибьютор ведущих мировых производителей металлорежущего инструмента. Прямые поставки из Германии, Японии, Швейцарии. Складская программа — более 10 000 наименований.',
        'sales@techkomplekt.ru', 'www.techkomplekt.ru', '+7 (343) 234-56-78',
        v_brand_techkomplekt,
        '6671234567', '1036600123456', '667101001',
        'Смирнова Елена Александровна', 'Ведущий менеджер', '+7 (343) 234-56-79',
        'Морозов Дмитрий Игоревич', 'Генеральный директор',
        'АО "Альфа-Банк"', '044525593', '30101810200000000593', '40702810300000000456'
    );
    v_logo_uid := gen_random_uuid();
    v_logo_filename := v_logo_uid::text || '.svg';
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at)
    VALUES (v_logo_uid, v_supplier_uid, v_logo_filename, 'TechKomplekt_logo.svg', 0, NOW());
    
    -- Поставщик 3: ИП Иванов А.А.
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (
        uid, code, name, country_uid, address, short_description_uid, description,
        email, website, phone, brand_uid,
        inn, ogrn, kpp,
        contact_person, contact_position, contact_phone,
        director, director_position,
        bank_name, bik, correspondent_account, settlement_account
    ) VALUES (
        v_supplier_uid, v_code, 'ИП Иванов А.А.',
        v_country_blr, '220030, Республика Беларусь, г. Минск, ул. Интернациональная, д. 15',
        v_desc_wholesale,
        'Индивидуальный предприниматель. Специализация: метизы, крепежные изделия, строительный инструмент. Работаем с 2010 года. Гибкие условия для оптовых покупателей.',
        'ivanov@metiz.by', 'www.ivanov-metiz.by', '+375 (17) 345-67-89',
        v_brand_ivanov,
        '192345678', '304192345600012', '—',
        'Иванов Александр Александрович', 'Собственник', '+375 (29) 111-22-33',
        'Иванов Александр Александрович', 'Индивидуальный предприниматель',
        'ОАО "АСБ Беларусбанк"', '153001795', '30101810200000000795', '40702810900000000789'
    );
    v_logo_uid := gen_random_uuid();
    v_logo_filename := v_logo_uid::text || '.svg';
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at)
    VALUES (v_logo_uid, v_supplier_uid, v_logo_filename, 'Ivanov_logo.svg', 0, NOW());
    
    -- Поставщик 4: ООО "МетизТорг"
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (
        uid, code, name, country_uid, address, short_description_uid, description,
        email, website, phone, brand_uid,
        inn, ogrn, kpp,
        contact_person, contact_position, contact_phone,
        director, director_position,
        bank_name, bik, correspondent_account, settlement_account
    ) VALUES (
        v_supplier_uid, v_code, 'ООО "МетизТорг"',
        v_country_rus, '603000, г. Нижний Новгород, ул. Белинского, д. 32, пом. 12',
        v_desc_dealer,
        'Дилерская сеть по продаже металлоизделий и крепежа. Работаем с заводами-производителями напрямую. Доставка по всей России.',
        'info@metiztorg.ru', 'www.metiztorg.ru', '+7 (831) 456-78-90',
        v_brand_metiztorg,
        '5261234567', '1035200123456', '526101001',
        'Козлов Павел Сергеевич', 'Менеджер по работе с клиентами', '+7 (831) 456-78-91',
        'Новикова Ольга Владимировна', 'Исполнительный директор',
        'ПАО "ВТБ"', '044525187', '30101810200000000187', '40702810400000001012'
    );
    v_logo_uid := gen_random_uuid();
    v_logo_filename := v_logo_uid::text || '.svg';
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at)
    VALUES (v_logo_uid, v_supplier_uid, v_logo_filename, 'MetizTorg_logo.svg', 0, NOW());
    
    -- Поставщик 5: ЗАО "ИнструментСервис"
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (
        uid, code, name, country_uid, address, short_description_uid, description,
        email, website, phone, brand_uid,
        inn, ogrn, kpp,
        contact_person, contact_position, contact_phone,
        director, director_position,
        bank_name, bik, correspondent_account, settlement_account
    ) VALUES (
        v_supplier_uid, v_code, 'ЗАО "ИнструментСервис"',
        v_country_chn, '430000, Китай, г. Шанхай, Pudong New Area, Zhangjiang Hi-Tech Park, Building 12',
        v_desc_importer,
        'Прямой импортер высокоточного режущего инструмента из Китая. Эксклюзивный представитель заводов New Century, Sumitomo, Kyocera на территории РФ. Таможенное оформление под ключ.',
        'order@instrumentservice.pro', 'www.instrumentservice.pro', '+86 (21) 1234-5678',
        v_brand_instrumentservice,
        '9901234567', '1039900123456', '990101001',
        'Чжан Вэй', 'Руководитель отдела ВЭД', '+86 (21) 1234-5679',
        'Ли Цзянь', 'Генеральный директор',
        'Bank of China, Shanghai Branch', 'BKCHCNBJ300', '30101810200000000300', '40702810900000001314'
    );
    v_logo_uid := gen_random_uuid();
    v_logo_filename := v_logo_uid::text || '.svg';
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at)
    VALUES (v_logo_uid, v_supplier_uid, v_logo_filename, 'InstrumentService_logo.svg', 0, NOW());
    
    -- Поставщик 6: ООО "ЗАДЕЛ"
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (
        uid, code, name, country_uid, address, short_description_uid, description,
        email, website, phone, brand_uid,
        inn, ogrn, kpp,
        contact_person, contact_position, contact_phone,
        director, director_position,
        bank_name, bik, correspondent_account, settlement_account
    ) VALUES (
        v_supplier_uid, v_code, 'ООО "ЗАДЕЛ"',
        v_country_rus, '105264, г. Москва, ул. Верхняя Первомайская, д. 47, стр. 3',
        v_desc_producer,
        'Российский производитель технологической оснастки и заделов для машиностроительных предприятий. Собственное конструкторское бюро и производственные мощности. Изготовление по чертежам заказчика.',
        'info@zadel.pro', 'www.zadel.pro', '+7 (495) 987-65-43',
        v_brand_zadel,
        '7719876543', '1027700987654', '771901001',
        'Григорьев Андрей Павлович', 'Начальник отдела сбыта', '+7 (495) 987-65-44',
        'Соколов Михаил Леонидович', 'Генеральный директор',
        'ПАО "Сбербанк"', '044525225', '30101810400000000225', '40702810900000005678'
    );
    v_logo_uid := gen_random_uuid();
    v_logo_filename := v_logo_uid::text || '.svg';
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at)
    VALUES (v_logo_uid, v_supplier_uid, v_logo_filename, 'ZADEL_logo.svg', 0, NOW());
    
END $$;

COMMIT;