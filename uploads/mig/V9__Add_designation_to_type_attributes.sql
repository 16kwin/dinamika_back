-- V9__Add_designation_to_type_attributes.sql

ALTER TABLE spr_type_attributes 
ADD COLUMN IF NOT EXISTS designation VARCHAR(10);

-- Предустановленные виды характеристик
INSERT INTO spr_type_attributes (uid, name, designation) 
SELECT gen_random_uuid(), 'Длина', 'L'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Длина');

INSERT INTO spr_type_attributes (uid, name, designation) 
SELECT gen_random_uuid(), 'Ширина', 'W'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Ширина');

INSERT INTO spr_type_attributes (uid, name, designation) 
SELECT gen_random_uuid(), 'Глубина', 'D'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Глубина');

INSERT INTO spr_type_attributes (uid, name, designation) 
SELECT gen_random_uuid(), 'Высота', 'H'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Высота');

INSERT INTO spr_type_attributes (uid, name, designation) 
SELECT gen_random_uuid(), 'Масса', 'M'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Масса');

INSERT INTO spr_type_attributes (uid, name, designation) 
SELECT gen_random_uuid(), 'Срок эксплуатации', 'T'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Срок эксплуатации');