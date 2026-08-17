-- Создаём таблицу для документов станций
CREATE TABLE IF NOT EXISTS station_documents (
    uid UUID PRIMARY KEY,
    station_uid VARCHAR(255) NOT NULL REFERENCES stations(uid) ON DELETE CASCADE,
    document_name VARCHAR(500),
    file_path VARCHAR(1000),
    original_name VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_station_documents_station ON station_documents(station_uid);