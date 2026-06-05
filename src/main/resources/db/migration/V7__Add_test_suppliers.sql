-- V7__Add_test_suppliers.sql

BEGIN;

-- Добавляем тестовых поставщиков
INSERT INTO public.spr_suppliers (uid, name) VALUES 
(gen_random_uuid(), 'ООО "ПромСнаб"'),
(gen_random_uuid(), 'АО "ТехКомплект"'),
(gen_random_uuid(), 'ИП Иванов А.А.'),
(gen_random_uuid(), 'ООО "МетизТорг"'),
(gen_random_uuid(), 'ЗАО "ИнструментСервис"'),
(gen_random_uuid(), 'ООО "СтальПром"'),
(gen_random_uuid(), 'АО "ПодшипникМаш"'),
(gen_random_uuid(), 'ИП Петров В.С.'),
(gen_random_uuid(), 'ООО "Резинотехника"'),
(gen_random_uuid(), 'ЗАО "ЭлектроКомплект"')
ON CONFLICT DO NOTHING;

COMMIT;