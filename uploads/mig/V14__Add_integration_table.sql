-- V14__Add_integration_table.sql

CREATE TABLE IF NOT EXISTS public.reg_integration
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    event text COLLATE pg_catalog."default" NOT NULL DEFAULT 'Объект синхронизирован',
    exchange_type text COLLATE pg_catalog."default" NOT NULL,
    direction text COLLATE pg_catalog."default" NOT NULL,
    protocol text COLLATE pg_catalog."default" NOT NULL,
    target_system text COLLATE pg_catalog."default" NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_integration_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_integration_material_fkey FOREIGN KEY (material_uid)
        REFERENCES public.spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE public.reg_integration IS 'Регистр Интеграция';
COMMENT ON COLUMN public.reg_integration.event IS 'Событие';
COMMENT ON COLUMN public.reg_integration.exchange_type IS 'Тип обмена (Внутренний/Внешний)';
COMMENT ON COLUMN public.reg_integration.direction IS 'Направление (Исходящий/Входящий)';
COMMENT ON COLUMN public.reg_integration.protocol IS 'Протокол (WebSocket/REST)';
COMMENT ON COLUMN public.reg_integration.target_system IS 'Система обмена (1С:Предприятие/SAP/Oracle EBS)';