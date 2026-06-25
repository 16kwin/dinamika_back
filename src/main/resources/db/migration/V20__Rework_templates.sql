-- V20__Rework_templates.sql

BEGIN;

-- ============================================================
-- 1. СОЗДАЁМ ТАБЛИЦУ КАТЕГОРИЙ ШАБЛОНОВ (плоская структура)
-- ============================================================
CREATE TABLE IF NOT EXISTS public.template_categories (
    id BIGSERIAL PRIMARY KEY,
    uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE public.template_categories IS 'Категории шаблонов пополнения';

-- ============================================================
-- 2. УДАЛЯЕМ СТАРЫЕ СВЯЗИ И НЕАКТУАЛЬНЫЕ ПОЛЯ doc_pattern
-- ============================================================
ALTER TABLE IF EXISTS public.doc_pattern
    DROP CONSTRAINT IF EXISTS doc_pattern_name_station_fkey;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS name_station;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS category_id;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS total_cells;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS filled_cells;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS free_cells;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS created_at;

ALTER TABLE IF EXISTS public.doc_pattern
    DROP COLUMN IF EXISTS updated_at;

-- ============================================================
-- 3. ДОБАВЛЯЕМ НОВЫЕ ПОЛЯ В doc_pattern
-- ============================================================

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS number BIGINT UNIQUE;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS category_id BIGINT;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD CONSTRAINT doc_pattern_category_fkey 
    FOREIGN KEY (category_id)
    REFERENCES public.template_categories (id) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE SET NULL;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS configuration TEXT DEFAULT '';

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS total_cells INTEGER NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS filled_cells INTEGER NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS free_cells INTEGER NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS public.doc_pattern
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN public.doc_pattern.number IS 'Автоинкрементный номер шаблона';
COMMENT ON COLUMN public.doc_pattern.category_id IS 'Ссылка на категорию шаблона';
COMMENT ON COLUMN public.doc_pattern.configuration IS 'Конфигурация шаблона (JSON/пусто)';
COMMENT ON COLUMN public.doc_pattern.total_cells IS 'Общее количество ячеек в шаблоне';
COMMENT ON COLUMN public.doc_pattern.filled_cells IS 'Количество заполненных ячеек';
COMMENT ON COLUMN public.doc_pattern.free_cells IS 'Количество свободных ячеек';

-- ============================================================
-- 4. УДАЛЯЕМ старый constraint и ДОБАВЛЯЕМ active_template_uid В stations
-- ============================================================
ALTER TABLE IF EXISTS public.stations
    DROP CONSTRAINT IF EXISTS stations_active_template_fkey;

ALTER TABLE IF EXISTS public.stations
    ADD COLUMN IF NOT EXISTS active_template_uid uuid;

ALTER TABLE IF EXISTS public.stations
    ADD CONSTRAINT stations_active_template_fkey 
    FOREIGN KEY (active_template_uid)
    REFERENCES public.doc_pattern (uid) MATCH SIMPLE
    ON UPDATE NO ACTION
    ON DELETE SET NULL;

COMMENT ON COLUMN public.stations.active_template_uid IS 'Активный шаблон пополнения станции';

-- ============================================================
-- 5. ДОБАВЛЯЕМ НОВЫЕ ПОЛЯ В reg_cells
-- ============================================================

ALTER TABLE IF EXISTS public.reg_cells
    ADD COLUMN IF NOT EXISTS purpose_material TEXT;

ALTER TABLE IF EXISTS public.reg_cells
    ADD COLUMN IF NOT EXISTS purpose_sgd TEXT;

ALTER TABLE IF EXISTS public.reg_cells
    ADD COLUMN IF NOT EXISTS max_quantity INTEGER;

ALTER TABLE IF EXISTS public.reg_cells
    ADD COLUMN IF NOT EXISTS dimensions TEXT;

ALTER TABLE IF EXISTS public.reg_cells
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE IF EXISTS public.reg_cells
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN public.reg_cells.purpose_material IS 'Назначение "Материал"';
COMMENT ON COLUMN public.reg_cells.purpose_sgd IS 'Назначение "СГД"';
COMMENT ON COLUMN public.reg_cells.max_quantity IS 'Максимальное количество номенклатуры в ячейке';
COMMENT ON COLUMN public.reg_cells.dimensions IS 'Габариты ячейки';

-- ============================================================
-- 6. ТЕСТОВЫЕ КАТЕГОРИИ
-- ============================================================
INSERT INTO public.template_categories (name) VALUES 
    ('Инструментальные'),
    ('Универсальные'),
    ('Специальные'),
    ('Тестовые')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 7. ПРОСТАВЛЯЕМ НОМЕРА СУЩЕСТВУЮЩИМ ШАБЛОНАМ
-- ============================================================
DO $$
DECLARE
    r RECORD;
    v_counter BIGINT := 1;
BEGIN
    FOR r IN SELECT uid FROM public.doc_pattern ORDER BY uid
    LOOP
        UPDATE public.doc_pattern SET number = v_counter WHERE uid = r.uid;
        v_counter := v_counter + 1;
    END LOOP;
END $$;

-- ============================================================
-- 8. ИНДЕКСЫ
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_doc_pattern_number ON public.doc_pattern(number);
CREATE INDEX IF NOT EXISTS idx_doc_pattern_category ON public.doc_pattern(category_id);

COMMIT;