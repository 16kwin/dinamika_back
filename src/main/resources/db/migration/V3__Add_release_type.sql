-- Создание таблицы видов выпуска
CREATE TABLE IF NOT EXISTS spr_release (
    uid UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Добавление колонки в spr_material
ALTER TABLE spr_material
    ADD COLUMN IF NOT EXISTS release_uid UUID REFERENCES spr_release(uid) ON DELETE SET NULL;

-- Сиды
INSERT INTO spr_release (uid, name) VALUES
    (gen_random_uuid(), 'Полуфабрикат'),
    (gen_random_uuid(), 'Деталь'),
    (gen_random_uuid(), 'Продукция (товарное изделие)')
ON CONFLICT (name) DO NOTHING;