-- V2__Add_user_code_defaults.sql
-- Таблица настроек типов кода по умолчанию

CREATE TABLE IF NOT EXISTS user_code_defaults (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_kind VARCHAR(20) NOT NULL,
    code_type VARCHAR(20) NOT NULL,
    UNIQUE(user_id, code_kind)
);

CREATE INDEX IF NOT EXISTS idx_user_code_defaults_user ON user_code_defaults(user_id);