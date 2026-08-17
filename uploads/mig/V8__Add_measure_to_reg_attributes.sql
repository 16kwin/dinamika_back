-- V8__Add_measure_to_reg_attributes.sql

ALTER TABLE reg_attributes 
ADD COLUMN IF NOT EXISTS measure_uid uuid,
ADD COLUMN IF NOT EXISTS material_uid uuid;

ALTER TABLE reg_attributes
ADD CONSTRAINT reg_attributes_measure_fkey 
FOREIGN KEY (measure_uid) REFERENCES spr_measure(uid),
ADD CONSTRAINT reg_attributes_material_fkey 
FOREIGN KEY (material_uid) REFERENCES spr_material(uid);