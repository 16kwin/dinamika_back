-- V10__Add_material_documents.sql

CREATE TABLE IF NOT EXISTS public.spr_material_documents
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    document_name text COLLATE pg_catalog."default" NOT NULL,
    file_path text COLLATE pg_catalog."default" NOT NULL,
    original_name text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spr_material_documents_pkey PRIMARY KEY (uid),
    CONSTRAINT spr_material_documents_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.spr_material_documents IS 'Документы номенклатуры';
COMMENT ON COLUMN public.spr_material_documents.document_name IS 'Название документа';
COMMENT ON COLUMN public.spr_material_documents.file_path IS 'Путь к файлу относительно папки номенклатуры';
COMMENT ON COLUMN public.spr_material_documents.original_name IS 'Оригинальное имя файла при загрузке';