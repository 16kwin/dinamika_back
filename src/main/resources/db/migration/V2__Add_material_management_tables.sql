-- V2__Add_material_management_tables.sql

BEGIN;

CREATE TABLE IF NOT EXISTS public.data_type
(
    uid uuid NOT NULL,
    type_text text COLLATE pg_catalog."default",
    type_number double precision,
    type_spr uuid,
    CONSTRAINT data_type_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.data_type
    IS 'Типы данных';

COMMENT ON COLUMN public.data_type.type_number
    IS 'Тип данных значения';

CREATE TABLE IF NOT EXISTS public.doc_entrance
(
    uid uuid NOT NULL,
    price double precision NOT NULL,
    supplier uuid,
    CONSTRAINT doc_entrance_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.doc_entrance
    IS 'Документ Поступление материалов (приходная накладная, УПД)';

CREATE TABLE IF NOT EXISTS public.doc_pattern
(
    uid uuid NOT NULL,
    name_pattern text COLLATE pg_catalog."default" NOT NULL,
    status_doc boolean NOT NULL,
    name_station VARCHAR(50),
    CONSTRAINT doc_pattern_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.doc_pattern
    IS 'Документ Шаблон пополнения станции';

CREATE TABLE IF NOT EXISTS public.reg_analog
(
    uid uuid NOT NULL,
    name uuid,
    CONSTRAINT reg_analog_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_analog
    IS 'Регистр Аналоги';

CREATE TABLE IF NOT EXISTS public.reg_attached
(
    uid uuid NOT NULL,
    name_file text COLLATE pg_catalog."default" NOT NULL,
    url_file uuid NOT NULL,
    link uuid,
    CONSTRAINT reg_attached_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_attached
    IS 'Регистр Файлы';

CREATE TABLE IF NOT EXISTS public.reg_attributes
(
    uid uuid NOT NULL,
    name uuid,
    meaning text COLLATE pg_catalog."default",
    CONSTRAINT reg_attributes_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_attributes
    IS 'Регистр Атрибуты';

COMMENT ON COLUMN public.reg_attributes.meaning
    IS 'Значение';

CREATE TABLE IF NOT EXISTS public.reg_cells
(
    uid uuid NOT NULL,
    doc_pattern_uid uuid,
    number_cell integer,
    name_material uuid,
    quantity integer,
    type_main uuid,
    CONSTRAINT reg_cells_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_cells
    IS 'Регистр привязки ячеек к шаблону пополнения станции';

CREATE TABLE IF NOT EXISTS public.reg_group_material
(
    uid uuid NOT NULL,
    group_name text COLLATE pg_catalog."default" NOT NULL,
    parent_group uuid,
    group_code integer,
    CONSTRAINT reg_group_material_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_group_material
    IS 'Группы материалов в иерархии';

COMMENT ON COLUMN public.reg_group_material.parent_group
    IS 'Родительская группа';

COMMENT ON COLUMN public.reg_group_material.group_code
    IS 'Код группы (5 знаков)';

CREATE TABLE IF NOT EXISTS public.reg_price
(
    uid uuid NOT NULL,
    price double precision NOT NULL,
    link uuid,
    doc_entrance_uid uuid,
    CONSTRAINT reg_price_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_price
    IS 'Регистр цен';

CREATE TABLE IF NOT EXISTS public.reg_suppliers
(
    uid uuid NOT NULL,
    material_uid uuid,
    supplier_uid uuid,
    CONSTRAINT reg_suppliers_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.reg_suppliers
    IS 'Регистр Поставщики с привязкой к номенклатуре';

CREATE TABLE IF NOT EXISTS public.spr_brand
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_brand_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_brand
    IS 'Справочник Бренды';

CREATE TABLE IF NOT EXISTS public.spr_country
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_country_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_country
    IS 'Справочник Страны';

CREATE TABLE IF NOT EXISTS public.spr_manufacturer
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_manufacturer_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_manufacturer
    IS 'Справочник Производители';

CREATE TABLE IF NOT EXISTS public.spr_material
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    guid_1c bit varying(128)[],
    uid_other_sys bit varying(128)[],
    uid_store bit varying(128)[],
    url_image uuid,
    code_material serial NOT NULL,
    group_material uuid,
    type_main uuid,
    type_purpose uuid,
    resharpen boolean,
    name_material text COLLATE pg_catalog."default",
    article text COLLATE pg_catalog."default",
    type_product uuid,
    manufacturer uuid,
    country uuid,
    brand uuid,
    model_of_brand uuid,
    measure uuid,
    usage boolean,
    waste_material boolean,
    recycle_material boolean,
    barcode text COLLATE pg_catalog."default",
    description text COLLATE pg_catalog."default",
    attached uuid,
    suppliers uuid,
    analog uuid,
    attributes uuid,
    price uuid,
    syncronized_mother_system boolean,
    syncronized_supplier boolean,
    create_date time without time zone DEFAULT now(),
    CONSTRAINT spr_material_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_material
    IS 'Объект справочника Номенклатура';

COMMENT ON COLUMN public.spr_material.uid
    IS 'Уникальный идентификатор объекта номенклатуры';

COMMENT ON COLUMN public.spr_material.guid_1c
    IS 'GUID в 1С';

COMMENT ON COLUMN public.spr_material.uid_other_sys
    IS 'UID внешней материнской системы';

COMMENT ON COLUMN public.spr_material.uid_store
    IS 'UID связанного магазина';

COMMENT ON COLUMN public.spr_material.url_image
    IS 'Ссылка на изображение';

COMMENT ON COLUMN public.spr_material.code_material
    IS 'Код номенклатуры';

COMMENT ON COLUMN public.spr_material.group_material
    IS 'Группа материалов';

COMMENT ON COLUMN public.spr_material.type_main
    IS 'Основные группы хранимых материалов';

COMMENT ON COLUMN public.spr_material.type_purpose
    IS 'Тип назначения';

COMMENT ON COLUMN public.spr_material.resharpen
    IS 'Переточенный материал';

COMMENT ON COLUMN public.spr_material.name_material
    IS 'Наименование ТМЦ';

COMMENT ON COLUMN public.spr_material.article
    IS 'Артикул';

COMMENT ON COLUMN public.spr_material.type_product
    IS 'Вид товара';

COMMENT ON COLUMN public.spr_material.manufacturer
    IS 'Производитель';

COMMENT ON COLUMN public.spr_material.country
    IS 'Страна происхождения';

COMMENT ON COLUMN public.spr_material.brand
    IS 'Бренд товара';

COMMENT ON COLUMN public.spr_material.model_of_brand
    IS 'Конкретная модель бренда';

COMMENT ON COLUMN public.spr_material.measure
    IS 'Единица измерения';

COMMENT ON COLUMN public.spr_material.usage
    IS 'Использование (одноразовый/многоразовый)';

COMMENT ON COLUMN public.spr_material.waste_material
    IS 'Признак возврата в лом';

COMMENT ON COLUMN public.spr_material.recycle_material
    IS 'Признак возврата на переточку';

COMMENT ON COLUMN public.spr_material.barcode
    IS 'Штрихкод';

COMMENT ON COLUMN public.spr_material.description
    IS 'Описание';

COMMENT ON COLUMN public.spr_material.attached
    IS 'Список приложенных файлов';

COMMENT ON COLUMN public.spr_material.suppliers
    IS 'Список поставщиков';

COMMENT ON COLUMN public.spr_material.analog
    IS 'Список аналогов';

COMMENT ON COLUMN public.spr_material.attributes
    IS 'Список атрибутов и характеристик';

COMMENT ON COLUMN public.spr_material.price
    IS 'Ссылка на регистр цен';

COMMENT ON COLUMN public.spr_material.syncronized_mother_system
    IS 'Признак синхронизации с материнской системой';

COMMENT ON COLUMN public.spr_material.syncronized_supplier
    IS 'Признак синхронизации с системой поставщика';

CREATE TABLE IF NOT EXISTS public.spr_measure
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_measure_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_measure
    IS 'Справочник Единицы измерения';

CREATE TABLE IF NOT EXISTS public.spr_model_of_brand
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    brand uuid,
    CONSTRAINT spr_model_of_brand_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_model_of_brand
    IS 'Справочник Модели брендов';

CREATE TABLE IF NOT EXISTS public.spr_suppliers
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_suppliers_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_suppliers
    IS 'Справочник Поставщики';

CREATE TABLE IF NOT EXISTS public.spr_type_attributes
(
    uid uuid NOT NULL,
    name text COLLATE pg_catalog."default" NOT NULL,
    data_type uuid,
    CONSTRAINT spr_type_attributes_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_type_attributes
    IS 'Справочник Виды атрибутов';

COMMENT ON COLUMN public.spr_type_attributes.data_type
    IS 'Тип данных значения';

CREATE TABLE IF NOT EXISTS public.spr_type_material
(
    uid uuid NOT NULL,
    type_name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_type_material_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_type_material
    IS 'Справочник Основные виды хранимых материалов: ТМЦ, Готовая деталь, Инструмент на переточку, Брак готовой детали, Лом';

CREATE TABLE IF NOT EXISTS public.spr_type_product
(
    uid uuid NOT NULL,
    type_name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_type_product_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_type_product
    IS 'Справочник Виды товара';

CREATE TABLE IF NOT EXISTS public.spr_type_purpose
(
    uid uuid NOT NULL,
    type_name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_type_purpose_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_type_purpose
    IS 'Справочник Типы назначения материалов: Металлообрабатывающий инструмент, Механический инструмент, Оснастка, СИЗ, Расходные материалы и др.';

-- Создаём корневую группу "Номенклатура" с кодом 00000
INSERT INTO public.reg_group_material (uid, group_name, parent_group, group_code)
SELECT gen_random_uuid(), 'Номенклатура', NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM public.reg_group_material WHERE group_code = 0);

ALTER TABLE IF EXISTS public.doc_entrance
    ADD CONSTRAINT doc_entrance_supplier_fkey FOREIGN KEY (supplier)
    REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD CONSTRAINT doc_pattern_name_station_fkey FOREIGN KEY (name_station)
    REFERENCES public.stations (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_analog
    ADD CONSTRAINT fkey_analog_material FOREIGN KEY (name)
    REFERENCES public.spr_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_attached
    ADD CONSTRAINT fkey_attached_material FOREIGN KEY (link)
    REFERENCES public.spr_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_attributes
    ADD CONSTRAINT reg_attributes_name_fkey FOREIGN KEY (name)
    REFERENCES public.spr_type_attributes (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_cells
    ADD CONSTRAINT reg_cells_doc_pattern_uid_fkey FOREIGN KEY (doc_pattern_uid)
    REFERENCES public.doc_pattern (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_cells
    ADD CONSTRAINT reg_cells_name_material_fkey FOREIGN KEY (name_material)
    REFERENCES public.spr_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_cells
    ADD CONSTRAINT reg_cells_type_main_fkey FOREIGN KEY (type_main)
    REFERENCES public.spr_type_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_price
    ADD CONSTRAINT fkey_price_material FOREIGN KEY (link)
    REFERENCES public.spr_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_price
    ADD CONSTRAINT reg_price_doc_entrance_uid_fkey FOREIGN KEY (doc_entrance_uid)
    REFERENCES public.doc_entrance (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_suppliers
    ADD CONSTRAINT fkey_regsupp_material FOREIGN KEY (material_uid)
    REFERENCES public.spr_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.reg_suppliers
    ADD CONSTRAINT reg_suppliers_supplier_uid_fkey FOREIGN KEY (supplier_uid)
    REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_analog_fkey FOREIGN KEY (analog)
    REFERENCES public.reg_analog (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_attached_fkey FOREIGN KEY (attached)
    REFERENCES public.reg_attached (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_attributes_fkey FOREIGN KEY (attributes)
    REFERENCES public.reg_attributes (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_brand_fkey FOREIGN KEY (brand)
    REFERENCES public.spr_brand (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_country_fkey FOREIGN KEY (country)
    REFERENCES public.spr_country (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_group_material_fkey FOREIGN KEY (group_material)
    REFERENCES public.reg_group_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_manufacturer_fkey FOREIGN KEY (manufacturer)
    REFERENCES public.spr_manufacturer (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_measure_fkey FOREIGN KEY (measure)
    REFERENCES public.spr_measure (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_model_of_brand_fkey FOREIGN KEY (model_of_brand)
    REFERENCES public.spr_model_of_brand (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_price_fkey FOREIGN KEY (price)
    REFERENCES public.reg_price (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_suppliers_fkey FOREIGN KEY (suppliers)
    REFERENCES public.reg_suppliers (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_type_main_fkey FOREIGN KEY (type_main)
    REFERENCES public.spr_type_material (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_type_product_fkey FOREIGN KEY (type_product)
    REFERENCES public.spr_type_product (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_material
    ADD CONSTRAINT spr_material_type_purpose_fkey FOREIGN KEY (type_purpose)
    REFERENCES public.spr_type_purpose (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_model_of_brand
    ADD CONSTRAINT spr_model_of_brand_brand_fkey FOREIGN KEY (brand)
    REFERENCES public.spr_brand (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

ALTER TABLE IF EXISTS public.spr_type_attributes
    ADD CONSTRAINT spr_type_attributes_data_type_fkey FOREIGN KEY (data_type)
    REFERENCES public.data_type (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;

COMMIT;