-- V5__Add_filters_and_sort_to_user_station_column_settings.sql
ALTER TABLE user_station_column_settings
    ADD COLUMN IF NOT EXISTS filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS sort_json JSONB NOT NULL DEFAULT '{}'::jsonb;