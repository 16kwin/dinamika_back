-- V6__Add_price_date.sql

BEGIN;

-- Добавляем дату в регистр цен
ALTER TABLE IF EXISTS public.reg_price
    ADD COLUMN IF NOT EXISTS price_date timestamp DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN public.reg_price.price_date IS 'Дата установки цены';

-- Добавляем дату в документ поступления
ALTER TABLE IF EXISTS public.doc_entrance
    ADD COLUMN IF NOT EXISTS entrance_date timestamp DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN public.doc_entrance.entrance_date IS 'Дата поступления';

COMMIT;