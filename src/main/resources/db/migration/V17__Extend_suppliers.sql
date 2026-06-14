-- V18__Extend_suppliers.sql

BEGIN;

-- 1. Справочник кратких описаний поставщиков
CREATE TABLE IF NOT EXISTS public.spr_supplier_description_types
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    name text COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT spr_supplier_description_types_pkey PRIMARY KEY (uid)
);

COMMENT ON TABLE public.spr_supplier_description_types IS 'Справочник вариантов краткого описания поставщика';

-- Предзаполняем варианты
INSERT INTO public.spr_supplier_description_types (uid, name) VALUES
    (gen_random_uuid(), 'Производитель'),
    (gen_random_uuid(), 'Официальный дистрибьютор'),
    (gen_random_uuid(), 'Дилер'),
    (gen_random_uuid(), 'Оптовый поставщик'),
    (gen_random_uuid(), 'Розничный поставщик'),
    (gen_random_uuid(), 'Импортер'),
    (gen_random_uuid(), 'Сервисный центр'),
    (gen_random_uuid(), 'Партнер')
ON CONFLICT DO NOTHING;

-- 2. Расширяем spr_suppliers
ALTER TABLE public.spr_suppliers
    ADD COLUMN IF NOT EXISTS code integer,
    ADD COLUMN IF NOT EXISTS country_uid uuid,
    ADD COLUMN IF NOT EXISTS address text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS short_description_uid uuid,
    ADD COLUMN IF NOT EXISTS description text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS email text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS website text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS phone text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS brand_uid uuid,
    -- Реквизиты
    ADD COLUMN IF NOT EXISTS inn text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS ogrn text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS kpp text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS contact_person text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS contact_position text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS contact_phone text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS director text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS director_position text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS bank_name text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS bik text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS correspondent_account text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS settlement_account text COLLATE pg_catalog."default",
    ADD COLUMN IF NOT EXISTS created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at timestamp DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN public.spr_suppliers.code IS 'Код поставщика';
COMMENT ON COLUMN public.spr_suppliers.country_uid IS 'Страна';
COMMENT ON COLUMN public.spr_suppliers.address IS 'Адрес';
COMMENT ON COLUMN public.spr_suppliers.short_description_uid IS 'Краткое описание (из справочника)';
COMMENT ON COLUMN public.spr_suppliers.description IS 'Полное описание';
COMMENT ON COLUMN public.spr_suppliers.email IS 'Email';
COMMENT ON COLUMN public.spr_suppliers.website IS 'Сайт';
COMMENT ON COLUMN public.spr_suppliers.phone IS 'Телефон';
COMMENT ON COLUMN public.spr_suppliers.brand_uid IS 'Бренд';
COMMENT ON COLUMN public.spr_suppliers.inn IS 'ИНН';
COMMENT ON COLUMN public.spr_suppliers.ogrn IS 'ОГРН';
COMMENT ON COLUMN public.spr_suppliers.kpp IS 'КПП';
COMMENT ON COLUMN public.spr_suppliers.contact_person IS 'Контактное лицо (ФИО)';
COMMENT ON COLUMN public.spr_suppliers.contact_position IS 'Должность контактного лица';
COMMENT ON COLUMN public.spr_suppliers.contact_phone IS 'Телефон контактного лица';
COMMENT ON COLUMN public.spr_suppliers.director IS 'Руководитель (ФИО)';
COMMENT ON COLUMN public.spr_suppliers.director_position IS 'Должность руководителя';
COMMENT ON COLUMN public.spr_suppliers.bank_name IS 'Наименование банка';
COMMENT ON COLUMN public.spr_suppliers.bik IS 'БИК';
COMMENT ON COLUMN public.spr_suppliers.correspondent_account IS 'Корреспондентский счет';
COMMENT ON COLUMN public.spr_suppliers.settlement_account IS 'Расчетный счет';

-- Внешние ключи для spr_suppliers
ALTER TABLE public.spr_suppliers
    ADD CONSTRAINT spr_suppliers_country_fkey FOREIGN KEY (country_uid)
        REFERENCES public.spr_country (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE SET NULL,
    ADD CONSTRAINT spr_suppliers_brand_fkey FOREIGN KEY (brand_uid)
        REFERENCES public.spr_brand (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE SET NULL,
    ADD CONSTRAINT spr_suppliers_short_description_fkey FOREIGN KEY (short_description_uid)
        REFERENCES public.spr_supplier_description_types (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE SET NULL;

-- Индексы
CREATE INDEX IF NOT EXISTS idx_spr_suppliers_code ON public.spr_suppliers(code);
CREATE INDEX IF NOT EXISTS idx_spr_suppliers_country ON public.spr_suppliers(country_uid);
CREATE INDEX IF NOT EXISTS idx_spr_suppliers_brand ON public.spr_suppliers(brand_uid);

-- 3. Изображения поставщика
CREATE TABLE IF NOT EXISTS public.spr_supplier_images
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    supplier_uid uuid NOT NULL,
    file_path text COLLATE pg_catalog."default" NOT NULL,
    original_name text COLLATE pg_catalog."default",
    sort_order integer DEFAULT 0,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spr_supplier_images_pkey PRIMARY KEY (uid),
    CONSTRAINT spr_supplier_images_supplier_fkey FOREIGN KEY (supplier_uid)
        REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE CASCADE
);

COMMENT ON TABLE public.spr_supplier_images IS 'Изображения/логотипы поставщика';
COMMENT ON COLUMN public.spr_supplier_images.file_path IS 'Путь к файлу';
COMMENT ON COLUMN public.spr_supplier_images.original_name IS 'Оригинальное имя файла';
COMMENT ON COLUMN public.spr_supplier_images.sort_order IS 'Порядок сортировки';

CREATE INDEX IF NOT EXISTS idx_spr_supplier_images_supplier ON public.spr_supplier_images(supplier_uid);

-- 4. Документы поставщика
CREATE TABLE IF NOT EXISTS public.spr_supplier_documents
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    supplier_uid uuid NOT NULL,
    document_name text COLLATE pg_catalog."default" NOT NULL,
    file_path text COLLATE pg_catalog."default" NOT NULL,
    original_name text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spr_supplier_documents_pkey PRIMARY KEY (uid),
    CONSTRAINT spr_supplier_documents_supplier_fkey FOREIGN KEY (supplier_uid)
        REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE CASCADE
);

COMMENT ON TABLE public.spr_supplier_documents IS 'Документы поставщика';
COMMENT ON COLUMN public.spr_supplier_documents.document_name IS 'Название документа';
COMMENT ON COLUMN public.spr_supplier_documents.file_path IS 'Путь к файлу';
COMMENT ON COLUMN public.spr_supplier_documents.original_name IS 'Оригинальное имя файла';

CREATE INDEX IF NOT EXISTS idx_spr_supplier_documents_supplier ON public.spr_supplier_documents(supplier_uid);

-- 5. Рейтинг поставщика
CREATE TABLE IF NOT EXISTS public.reg_supplier_ratings
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    supplier_uid uuid NOT NULL,
    rating integer NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment text COLLATE pg_catalog."default",
    author text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_supplier_ratings_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_supplier_ratings_supplier_fkey FOREIGN KEY (supplier_uid)
        REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE CASCADE
);

COMMENT ON TABLE public.reg_supplier_ratings IS 'Рейтинг поставщика';
COMMENT ON COLUMN public.reg_supplier_ratings.rating IS 'Оценка (1-5)';
COMMENT ON COLUMN public.reg_supplier_ratings.comment IS 'Комментарий';
COMMENT ON COLUMN public.reg_supplier_ratings.author IS 'Автор';

CREATE INDEX IF NOT EXISTS idx_reg_supplier_ratings_supplier ON public.reg_supplier_ratings(supplier_uid);

-- 6. Интеграция поставщика
CREATE TABLE IF NOT EXISTS public.reg_supplier_integration
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    supplier_uid uuid NOT NULL,
    event text COLLATE pg_catalog."default",
    exchange_type text COLLATE pg_catalog."default",
    direction text COLLATE pg_catalog."default",
    protocol text COLLATE pg_catalog."default",
    target_system text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_supplier_integration_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_supplier_integration_supplier_fkey FOREIGN KEY (supplier_uid)
        REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
        ON UPDATE NO ACTION ON DELETE CASCADE
);

COMMENT ON TABLE public.reg_supplier_integration IS 'Интеграции поставщика';
COMMENT ON COLUMN public.reg_supplier_integration.event IS 'Событие';
COMMENT ON COLUMN public.reg_supplier_integration.exchange_type IS 'Тип обмена';
COMMENT ON COLUMN public.reg_supplier_integration.direction IS 'Направление';
COMMENT ON COLUMN public.reg_supplier_integration.protocol IS 'Протокол';
COMMENT ON COLUMN public.reg_supplier_integration.target_system IS 'Целевая система';

CREATE INDEX IF NOT EXISTS idx_reg_supplier_integration_supplier ON public.reg_supplier_integration(supplier_uid);

-- 7. Сиквенс для кода поставщика (если нужен автоинкремент)
CREATE SEQUENCE IF NOT EXISTS public.spr_suppliers_code_seq
    INCREMENT 1
    START 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1;

COMMIT;