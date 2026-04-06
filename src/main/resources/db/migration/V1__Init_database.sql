-- 1. Таблица roles
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    description VARCHAR(255)
);

-- 2. Таблица users
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    middle_name VARCHAR(255),
    last_name VARCHAR(255),
    role_id BIGINT REFERENCES roles(id)
);

-- 3. Таблица refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token TEXT,
    user_id INTEGER REFERENCES users(id),
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    is_actual BOOLEAN
);

-- 4. Таблица stations
CREATE TABLE IF NOT EXISTS stations (
    id BIGSERIAL PRIMARY KEY,
    uid VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    workshop VARCHAR(100),
    section VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'WORKING',
    total_cells INTEGER NOT NULL DEFAULT 0,
    filled_cells INTEGER NOT NULL DEFAULT 0,
    template_nomenclature_count INTEGER NOT NULL DEFAULT 0,
    remaining_nomenclature_count INTEGER NOT NULL DEFAULT 0,
    max_ready_parts INTEGER NOT NULL DEFAULT 0,
    ready_parts_count INTEGER NOT NULL DEFAULT 0,
    station_type VARCHAR(50) NOT NULL,
    parent_uid VARCHAR(50),
    has_error BOOLEAN NOT NULL DEFAULT FALSE,
    is_tmc BOOLEAN NOT NULL DEFAULT FALSE,
    is_sgd BOOLEAN NOT NULL DEFAULT FALSE,
    is_ok BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы stations
CREATE INDEX IF NOT EXISTS idx_stations_uid ON stations(uid);
CREATE INDEX IF NOT EXISTS idx_stations_status ON stations(status);
CREATE INDEX IF NOT EXISTS idx_stations_type ON stations(station_type);
CREATE INDEX IF NOT EXISTS idx_stations_parent ON stations(parent_uid);

-- 5. Таблица test_documents
CREATE TABLE IF NOT EXISTS test_documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255),
    field2 VARCHAR(500),
    field3 VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed BOOLEAN DEFAULT FALSE
);

-- Индекс test_documents
CREATE INDEX IF NOT EXISTS idx_test_documents_user_completed ON test_documents(user_id, completed);

-- Вставка роли ADMIN
INSERT INTO roles (name, description) 
SELECT 'ADMIN', 'Администратор системы'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

-- Вставка пользователя admin
INSERT INTO users (username, password, first_name, middle_name, last_name, role_id)
SELECT 'admin', 
       '$2a$12$JHJRYuHX7/rkte1QhaBLr.vpk1d7uLZKv008VfxdEsrt8wAouCev.',
       'Admin',
       'Adminovich',
       'Administrator',
       (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Вставка станций
INSERT INTO stations (uid, name, workshop, section, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, station_type, parent_uid, has_error, is_tmc, is_sgd, is_ok)
SELECT * FROM (
    VALUES 
        ('ST-001', 'Инструментальная станция №1', 'Цех №1', 'Участок А', 'WORKING', 24, 18, 15, 9, 100, 67, 'DRUM_TYPE', NULL, false, true, false, true),
        ('ST-002', 'Инструментальная станция №2', 'Цех №1', 'Участок Б', 'WORKING', 32, 32, 20, 12, 150, 89, 'DRUM_TYPE', NULL, false, true, false, true),
        ('ST-003', 'Универсальная станция №1', 'Цех №2', 'Участок В', 'MINIMAL_STOCK', 48, 40, 35, 5, 200, 180, 'POSTAMAT_TYPE', NULL, false, true, true, true),
        ('ST-004', 'Универсальная станция №2', 'Цех №2', 'Участок Г', 'CRITICAL_STOCK', 40, 38, 30, 2, 180, 175, 'POSTAMAT_TYPE', NULL, true, true, false, false),
        ('ST-005', 'Дополнительный модуль 1', 'Цех №1', 'Участок А', 'OFFLINE', 12, 0, 0, 0, 0, 0, 'ADDITIONAL_MODULE', 'ST-001', true, false, false, false),
        ('ST-006', 'Дополнительный модуль 2', 'Цех №1', 'Участок А', 'WORKING', 8, 6, 5, 3, 40, 25, 'ADDITIONAL_MODULE', 'ST-001', false, false, false, false),
        ('ST-007', 'Универсальная станция №3', 'Цех №3', 'Участок Д', 'WORKING', 56, 42, 40, 25, 250, 210, 'POSTAMAT_TYPE', NULL, false, true, true, true),
        ('ST-008', 'Инструментальная станция №3', 'Цех №3', 'Участок Е', 'WORKING', 28, 28, 22, 15, 120, 95, 'DRUM_TYPE', NULL, false, true, false, false),
        ('ST-009', 'Дополнительный модуль 3', 'Цех №2', 'Участок В', 'MINIMAL_STOCK', 6, 6, 5, 1, 30, 28, 'ADDITIONAL_MODULE', 'ST-003', false, false, false, false),
        ('ST-010', 'Универсальная станция №4', 'Цех №4', 'Участок Ж', 'WORKING', 64, 50, 48, 30, 300, 250, 'POSTAMAT_TYPE', NULL, false, true, true, false)
) AS v(uid, name, workshop, section, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, station_type, parent_uid, has_error, is_tmc, is_sgd, is_ok)
WHERE NOT EXISTS (SELECT 1 FROM stations WHERE stations.uid = v.uid);