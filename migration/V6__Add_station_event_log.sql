-- V5__Add_station_event_log.sql
CREATE TABLE IF NOT EXISTS station_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_uid VARCHAR(50) NOT NULL REFERENCES stations(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_station_event_log_station ON station_event_log(station_uid);
CREATE INDEX IF NOT EXISTS idx_station_event_log_created ON station_event_log(created_at DESC);