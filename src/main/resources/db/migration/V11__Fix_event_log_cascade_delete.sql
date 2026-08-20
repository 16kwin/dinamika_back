-- V11__Fix_event_log_cascade_delete.sql

BEGIN;

-- ============================================================
-- Убираем ON DELETE CASCADE у всех event_log таблиц
-- История должна сохраняться после удаления объекта
-- ============================================================

-- События: Расположения
ALTER TABLE location_event_log
    DROP CONSTRAINT IF EXISTS location_event_log_location_uid_fkey;
ALTER TABLE location_event_log
    ADD CONSTRAINT location_event_log_location_uid_fkey
    FOREIGN KEY (location_uid) REFERENCES locations(uid) ON DELETE SET NULL;
ALTER TABLE location_event_log
    ALTER COLUMN location_uid DROP NOT NULL;

-- События: Страны
ALTER TABLE country_event_log
    DROP CONSTRAINT IF EXISTS country_event_log_country_uid_fkey;
ALTER TABLE country_event_log
    ADD CONSTRAINT country_event_log_country_uid_fkey
    FOREIGN KEY (country_uid) REFERENCES spr_country(uid) ON DELETE SET NULL;
ALTER TABLE country_event_log
    ALTER COLUMN country_uid DROP NOT NULL;

-- События: Типы станций
ALTER TABLE station_type_event_log
    DROP CONSTRAINT IF EXISTS station_type_event_log_station_type_uid_fkey;
ALTER TABLE station_type_event_log
    ADD CONSTRAINT station_type_event_log_station_type_uid_fkey
    FOREIGN KEY (station_type_uid) REFERENCES station_types(uid) ON DELETE SET NULL;
ALTER TABLE station_type_event_log
    ALTER COLUMN station_type_uid DROP NOT NULL;

-- События: Производители станций
ALTER TABLE station_manufacturer_event_log
    DROP CONSTRAINT IF EXISTS station_manufacturer_event_log_station_manufacturer_uid_fkey;
ALTER TABLE station_manufacturer_event_log
    ADD CONSTRAINT station_manufacturer_event_log_station_manufacturer_uid_fkey
    FOREIGN KEY (station_manufacturer_uid) REFERENCES station_manufacturers(uid) ON DELETE SET NULL;
ALTER TABLE station_manufacturer_event_log
    ALTER COLUMN station_manufacturer_uid DROP NOT NULL;

-- События: Холдинги
ALTER TABLE holding_event_log
    DROP CONSTRAINT IF EXISTS holding_event_log_holding_id_fkey;
ALTER TABLE holding_event_log
    ADD CONSTRAINT holding_event_log_holding_id_fkey
    FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE SET NULL;
ALTER TABLE holding_event_log
    ALTER COLUMN holding_id DROP NOT NULL;

-- События: Предприятия
ALTER TABLE enterprise_event_log
    DROP CONSTRAINT IF EXISTS enterprise_event_log_enterprise_id_fkey;
ALTER TABLE enterprise_event_log
    ADD CONSTRAINT enterprise_event_log_enterprise_id_fkey
    FOREIGN KEY (enterprise_id) REFERENCES enterprises(id) ON DELETE SET NULL;
ALTER TABLE enterprise_event_log
    ALTER COLUMN enterprise_id DROP NOT NULL;

-- События: Цеха
ALTER TABLE workshop_event_log
    DROP CONSTRAINT IF EXISTS workshop_event_log_workshop_id_fkey;
ALTER TABLE workshop_event_log
    ADD CONSTRAINT workshop_event_log_workshop_id_fkey
    FOREIGN KEY (workshop_id) REFERENCES workshops(id) ON DELETE SET NULL;
ALTER TABLE workshop_event_log
    ALTER COLUMN workshop_id DROP NOT NULL;

-- События: Участки
ALTER TABLE section_event_log
    DROP CONSTRAINT IF EXISTS section_event_log_section_id_fkey;
ALTER TABLE section_event_log
    ADD CONSTRAINT section_event_log_section_id_fkey
    FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE SET NULL;
ALTER TABLE section_event_log
    ALTER COLUMN section_id DROP NOT NULL;

-- События: Модели станций
ALTER TABLE station_model_event_log
    DROP CONSTRAINT IF EXISTS station_model_event_log_station_model_uid_fkey;
ALTER TABLE station_model_event_log
    ADD CONSTRAINT station_model_event_log_station_model_uid_fkey
    FOREIGN KEY (station_model_uid) REFERENCES station_models(uid) ON DELETE SET NULL;
ALTER TABLE station_model_event_log
    ALTER COLUMN station_model_uid DROP NOT NULL;

-- События: Конфигурации станций
ALTER TABLE station_configuration_event_log
    DROP CONSTRAINT IF EXISTS station_configuration_event_log_station_configuration_uid_fkey;
ALTER TABLE station_configuration_event_log
    ADD CONSTRAINT station_configuration_event_log_station_configuration_uid_fkey
    FOREIGN KEY (station_configuration_uid) REFERENCES station_configurations(uid) ON DELETE SET NULL;
ALTER TABLE station_configuration_event_log
    ALTER COLUMN station_configuration_uid DROP NOT NULL;

-- События: Поставщики
ALTER TABLE reg_supplier_event_log
    DROP CONSTRAINT IF EXISTS reg_supplier_event_log_supplier_uid_fkey;
ALTER TABLE reg_supplier_event_log
    ADD CONSTRAINT reg_supplier_event_log_supplier_uid_fkey
    FOREIGN KEY (supplier_uid) REFERENCES spr_suppliers(uid) ON DELETE SET NULL;
ALTER TABLE reg_supplier_event_log
    ALTER COLUMN supplier_uid DROP NOT NULL;

-- События: Материалы (номенклатура)
ALTER TABLE reg_event_log
    DROP CONSTRAINT IF EXISTS reg_event_log_material_uid_fkey;
ALTER TABLE reg_event_log
    ADD CONSTRAINT reg_event_log_material_uid_fkey
    FOREIGN KEY (material_uid) REFERENCES spr_material(uid) ON DELETE SET NULL;
ALTER TABLE reg_event_log
    ALTER COLUMN material_uid DROP NOT NULL;

COMMIT;