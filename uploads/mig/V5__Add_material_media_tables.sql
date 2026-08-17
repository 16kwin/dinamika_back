-- V5__Add_material_media_tables.sql

BEGIN;

-- Таблица изображений номенклатуры
CREATE TABLE IF NOT EXISTS public.spr_material_images
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    file_path text COLLATE pg_catalog."default" NOT NULL,
    original_name text COLLATE pg_catalog."default",
    sort_order integer DEFAULT 0,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spr_material_images_pkey PRIMARY KEY (uid),
    CONSTRAINT spr_material_images_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.spr_material_images IS 'Изображения номенклатуры';
COMMENT ON COLUMN public.spr_material_images.file_path IS 'Путь к файлу относительно папки номенклатуры';
COMMENT ON COLUMN public.spr_material_images.original_name IS 'Оригинальное имя файла при загрузке';
COMMENT ON COLUMN public.spr_material_images.sort_order IS 'Порядок сортировки для отображения';

-- Таблица чертежей номенклатуры
CREATE TABLE IF NOT EXISTS public.spr_material_blueprints
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    file_path text COLLATE pg_catalog."default" NOT NULL,
    original_name text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spr_material_blueprints_pkey PRIMARY KEY (uid),
    CONSTRAINT spr_material_blueprints_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.spr_material_blueprints IS 'Чертежи номенклатуры';
COMMENT ON COLUMN public.spr_material_blueprints.file_path IS 'Путь к файлу относительно папки номенклатуры';
COMMENT ON COLUMN public.spr_material_blueprints.original_name IS 'Оригинальное имя файла при загрузке';

-- Таблица QR-кодов номенклатуры
CREATE TABLE IF NOT EXISTS public.spr_material_qrcodes
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    file_path text COLLATE pg_catalog."default" NOT NULL,
    original_name text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spr_material_qrcodes_pkey PRIMARY KEY (uid),
    CONSTRAINT spr_material_qrcodes_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.spr_material_qrcodes IS 'QR-коды номенклатуры';
COMMENT ON COLUMN public.spr_material_qrcodes.file_path IS 'Путь к файлу относительно папки номенклатуры';
COMMENT ON COLUMN public.spr_material_qrcodes.original_name IS 'Оригинальное имя файла при загрузке';

COMMIT;