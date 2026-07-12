-- V28__Add_configuration_uid_to_doc_patterns.sql
ALTER TABLE IF EXISTS doc_pattern
    ADD COLUMN IF NOT EXISTS configuration_uid UUID REFERENCES station_configurations(uid) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_doc_patterns_configuration ON doc_pattern(configuration_uid);