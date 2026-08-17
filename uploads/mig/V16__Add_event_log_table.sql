-- V16__Add_event_log_table.sql

CREATE TABLE IF NOT EXISTS public.reg_event_log
(
    uid uuid NOT NULL DEFAULT gen_random_uuid(),
    material_uid uuid NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description text COLLATE pg_catalog."default" NOT NULL,
    field_name VARCHAR(255),
    old_value text,
    new_value text,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_event_log_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_event_log_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.reg_event_log IS 'Журнал событий номенклатуры';
COMMENT ON COLUMN public.reg_event_log.event_type IS 'Тип события: CREATE, UPDATE, ADD, DELETE';
COMMENT ON COLUMN public.reg_event_log.event_description IS 'Описание события для отображения';
COMMENT ON COLUMN public.reg_event_log.field_name IS 'Название измененного поля';
COMMENT ON COLUMN public.reg_event_log.old_value IS 'Старое значение';
COMMENT ON COLUMN public.reg_event_log.new_value IS 'Новое значение';
COMMENT ON COLUMN public.reg_event_log.author IS 'Автор события';
COMMENT ON COLUMN public.reg_event_log.source IS 'Источник события';

CREATE INDEX IF NOT EXISTS idx_event_log_material ON public.reg_event_log(material_uid);
CREATE INDEX IF NOT EXISTS idx_event_log_created ON public.reg_event_log(created_at DESC);