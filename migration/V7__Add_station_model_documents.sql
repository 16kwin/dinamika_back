-- V6__Add_station_model_documents.sql
CREATE TABLE IF NOT EXISTS station_model_documents (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_uid UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    document_name VARCHAR(500),
    file_path VARCHAR(1000),
    original_name VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_station_model_documents_model ON station_model_documents(model_uid);