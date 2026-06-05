-- V12__Refactor_reg_analog.sql

-- Удаляем старый внешний ключ из spr_material
ALTER TABLE spr_material DROP CONSTRAINT IF EXISTS spr_material_analog_fkey;

-- Удаляем старое поле analog из spr_material (если есть)
ALTER TABLE spr_material DROP COLUMN IF EXISTS analog;

-- Пересоздаём reg_analog
DROP TABLE IF EXISTS reg_analog CASCADE;

CREATE TABLE reg_analog
(
    uid uuid NOT NULL,
    material_uid uuid NOT NULL,
    analog_material_uid uuid NOT NULL,
    compatibility_percent integer NOT NULL DEFAULT 0,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT reg_analog_pkey PRIMARY KEY (uid),
    CONSTRAINT reg_analog_material_fkey FOREIGN KEY (material_uid)
        REFERENCES spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT reg_analog_analog_material_fkey FOREIGN KEY (analog_material_uid)
        REFERENCES spr_material (uid) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE
);

COMMENT ON TABLE reg_analog IS 'Регистр Аналоги';
COMMENT ON COLUMN reg_analog.material_uid IS 'Исходный материал';
COMMENT ON COLUMN reg_analog.analog_material_uid IS 'Материал-аналог';
COMMENT ON COLUMN reg_analog.compatibility_percent IS 'Процент совместимости (0-100)';