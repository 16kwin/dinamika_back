-- V19__Add_supplier_event_log.sql

CREATE TABLE IF NOT EXISTS public.reg_supplier_event_log
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    supplier_uid uuid NOT NULL,
    event_type text COLLATE pg_catalog."default",
    event_description text COLLATE pg_catalog."default",
    field_name text COLLATE pg_catalog."default",
    old_value text COLLATE pg_catalog."default",
    new_value text COLLATE pg_catalog."default",
    author text COLLATE pg_catalog."default",
    source text COLLATE pg_catalog."default",
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_supplier_event_log_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_supplier_event_log_supplier_fkey FOREIGN KEY (supplier_uid)
        REFERENCES public.spr_suppliers (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.reg_supplier_event_log IS 'Журнал событий поставщика';
COMMENT ON COLUMN public.reg_supplier_event_log.event_type IS 'Тип события: CREATE, UPDATE, ADD, DELETE';
COMMENT ON COLUMN public.reg_supplier_event_log.event_description IS 'Описание события';
COMMENT ON COLUMN public.reg_supplier_event_log.field_name IS 'Название изменённого поля';
COMMENT ON COLUMN public.reg_supplier_event_log.old_value IS 'Старое значение';
COMMENT ON COLUMN public.reg_supplier_event_log.new_value IS 'Новое значение';
COMMENT ON COLUMN public.reg_supplier_event_log.author IS 'Автор изменения';
COMMENT ON COLUMN public.reg_supplier_event_log.source IS 'Источник изменения';

CREATE INDEX IF NOT EXISTS idx_reg_supplier_event_log_supplier ON public.reg_supplier_event_log(supplier_uid);