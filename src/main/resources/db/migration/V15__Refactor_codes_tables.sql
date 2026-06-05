-- V15__Refactor_codes_tables.sql

-- 1. Добавляем новые поля в spr_material_qrcodes
ALTER TABLE spr_material_qrcodes 
ADD COLUMN IF NOT EXISTS code_type VARCHAR(20) DEFAULT 'QR_CODE',
ADD COLUMN IF NOT EXISTS code_value text,
ADD COLUMN IF NOT EXISTS code_kind VARCHAR(20) DEFAULT 'QR';

COMMENT ON COLUMN spr_material_qrcodes.code_type IS 'Тип кода (QR_CODE, EAN_13, CODE_128, UPC_A)';
COMMENT ON COLUMN spr_material_qrcodes.code_value IS 'Значение кода (текст)';
COMMENT ON COLUMN spr_material_qrcodes.code_kind IS 'Категория кода (BARCODE, SKU, QR)';

-- 2. Обновляем существующие QR-коды — проставляем code_kind = 'QR'
UPDATE spr_material_qrcodes SET code_kind = 'QR' WHERE code_kind IS NULL;

-- 3. Удаляем старые поля barcode из spr_material (если есть)
ALTER TABLE spr_material 
DROP COLUMN IF EXISTS barcode;

-- 4. Переименовываем таблицу
ALTER TABLE spr_material_qrcodes RENAME TO spr_material_codes;

-- 5. Комментарий к переименованной таблице
COMMENT ON TABLE spr_material_codes IS 'Коды номенклатуры (QR, штрихкоды, SKU)';