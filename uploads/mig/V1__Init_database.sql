-- V1__Init_database.sql

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

-- 4. Таблица предприятий
CREATE TABLE IF NOT EXISTS enterprises (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Таблица цехов
CREATE TABLE IF NOT EXISTS workshops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    enterprise_id BIGINT NOT NULL REFERENCES enterprises(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, enterprise_id)
);

-- 6. Таблица участков
CREATE TABLE IF NOT EXISTS sections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    workshop_id BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, workshop_id)
);

-- 7. Таблица станций
CREATE TABLE IF NOT EXISTS stations (
    id BIGSERIAL PRIMARY KEY,
    uid VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    enterprise_id BIGINT REFERENCES enterprises(id) ON DELETE SET NULL,
    workshop_id BIGINT REFERENCES workshops(id) ON DELETE SET NULL,
    section_id BIGINT REFERENCES sections(id) ON DELETE SET NULL,
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
CREATE INDEX IF NOT EXISTS idx_stations_enterprise ON stations(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_stations_workshop ON stations(workshop_id);
CREATE INDEX IF NOT EXISTS idx_stations_section ON stations(section_id);

-- 8. Таблица фильтров пользователя
CREATE TABLE IF NOT EXISTS user_filters (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filter_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- 9. Таблица test_documents
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

-- Вставка роли OPERATOR
INSERT INTO roles (name, description) 
SELECT 'OPERATOR', 'Оператор'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'OPERATOR');

-- Вставка пользователя admin с НОВЫМ ЗАШИФРОВАННЫМ паролем
INSERT INTO users (username, password, first_name, middle_name, last_name, role_id)
SELECT 'admin', 
       '$2y$12$DuBHFqPN/liLWOOpazYz7eStzx9bIaUFQCQP1W52Vxy3JI5BxTEua',
       'Admin',
       'Adminovich',
       'Administrator',
       (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Вставка предприятий
INSERT INTO enterprises (name) VALUES 
    ('Предприятие №1'),
    ('Предприятие №2'),
    ('Предприятие №3'),
    ('Предприятие №4')
ON CONFLICT (name) DO NOTHING;

-- Вставка цехов
INSERT INTO workshops (name, enterprise_id) VALUES 
    ('Цех №1', (SELECT id FROM enterprises WHERE name = 'Предприятие №1')),
    ('Цех №2', (SELECT id FROM enterprises WHERE name = 'Предприятие №1')),
    ('Цех №1', (SELECT id FROM enterprises WHERE name = 'Предприятие №2')),
    ('Цех №2', (SELECT id FROM enterprises WHERE name = 'Предприятие №2')),
    ('Цех №3', (SELECT id FROM enterprises WHERE name = 'Предприятие №3')),
    ('Цех №4', (SELECT id FROM enterprises WHERE name = 'Предприятие №4'))
ON CONFLICT (name, enterprise_id) DO NOTHING;

-- Вставка участков
INSERT INTO sections (name, workshop_id) VALUES 
    ('Участок А', (SELECT id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №1'))),
    ('Участок Б', (SELECT id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №1'))),
    ('Участок В', (SELECT id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №1'))),
    ('Участок Г', (SELECT id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №1'))),
    ('Участок Д', (SELECT id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №2'))),
    ('Участок Е', (SELECT id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №2'))),
    ('Участок Ж', (SELECT id FROM workshops WHERE name = 'Цех №3' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №3'))),
    ('Участок З', (SELECT id FROM workshops WHERE name = 'Цех №4' AND enterprise_id = (SELECT id FROM enterprises WHERE name = 'Предприятие №4')))
ON CONFLICT (name, workshop_id) DO NOTHING;

-- Вставка станций
DO $$
DECLARE
    ent1_id BIGINT;
    ent2_id BIGINT;
    ent3_id BIGINT;
    ent4_id BIGINT;
    ws1_id BIGINT;
    ws2_id BIGINT;
    ws3_id BIGINT;
    ws4_id BIGINT;
    ws5_id BIGINT;
    ws6_id BIGINT;
    secA_id BIGINT;
    secB_id BIGINT;
    secC_id BIGINT;
    secD_id BIGINT;
    secE_id BIGINT;
    secF_id BIGINT;
    secG_id BIGINT;
    secH_id BIGINT;
BEGIN
    -- Получаем ID предприятий
    SELECT id INTO ent1_id FROM enterprises WHERE name = 'Предприятие №1';
    SELECT id INTO ent2_id FROM enterprises WHERE name = 'Предприятие №2';
    SELECT id INTO ent3_id FROM enterprises WHERE name = 'Предприятие №3';
    SELECT id INTO ent4_id FROM enterprises WHERE name = 'Предприятие №4';
    
    -- Получаем ID цехов
    SELECT id INTO ws1_id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = ent1_id;
    SELECT id INTO ws2_id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = ent1_id;
    SELECT id INTO ws3_id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = ent2_id;
    SELECT id INTO ws4_id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = ent2_id;
    SELECT id INTO ws5_id FROM workshops WHERE name = 'Цех №3' AND enterprise_id = ent3_id;
    SELECT id INTO ws6_id FROM workshops WHERE name = 'Цех №4' AND enterprise_id = ent4_id;
    
    -- Получаем ID участков
    SELECT id INTO secA_id FROM sections WHERE name = 'Участок А' AND workshop_id = ws1_id;
    SELECT id INTO secB_id FROM sections WHERE name = 'Участок Б' AND workshop_id = ws1_id;
    SELECT id INTO secC_id FROM sections WHERE name = 'Участок В' AND workshop_id = ws2_id;
    SELECT id INTO secD_id FROM sections WHERE name = 'Участок Г' AND workshop_id = ws2_id;
    SELECT id INTO secE_id FROM sections WHERE name = 'Участок Д' AND workshop_id = ws3_id;
    SELECT id INTO secF_id FROM sections WHERE name = 'Участок Е' AND workshop_id = ws4_id;
    SELECT id INTO secG_id FROM sections WHERE name = 'Участок Ж' AND workshop_id = ws5_id;
    SELECT id INTO secH_id FROM sections WHERE name = 'Участок З' AND workshop_id = ws6_id;
    
    -- Вставка станций
    INSERT INTO stations (uid, name, enterprise_id, workshop_id, section_id, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, station_type, parent_uid, has_error, is_tmc, is_sgd, is_ok)
    SELECT * FROM (
        VALUES 
            ('ST-001', 'Инструментальная станция №1', ent1_id, ws1_id, secA_id, 'WORKING', 24, 18, 15, 9, 100, 67, 'DRUM_TYPE', NULL, false, true, false, true),
            ('ST-002', 'Инструментальная станция №2', ent1_id, ws1_id, secB_id, 'WORKING', 32, 32, 20, 12, 150, 89, 'DRUM_TYPE', NULL, false, true, false, true),
            ('ST-003', 'Универсальная станция №1', ent2_id, ws3_id, secE_id, 'MINIMAL_STOCK', 48, 40, 35, 5, 200, 180, 'POSTAMAT_TYPE', NULL, false, true, true, true),
            ('ST-004', 'Универсальная станция №2', ent2_id, ws4_id, secF_id, 'CRITICAL_STOCK', 40, 38, 30, 2, 180, 175, 'POSTAMAT_TYPE', NULL, true, true, false, false),
            ('ST-005', 'Дополнительный модуль 1', ent1_id, ws1_id, secA_id, 'OFFLINE', 12, 0, 0, 0, 0, 0, 'ADDITIONAL_MODULE', 'ST-001', true, false, false, false),
            ('ST-006', 'Дополнительный модуль 2', ent1_id, ws1_id, secA_id, 'WORKING', 8, 6, 5, 3, 40, 25, 'ADDITIONAL_MODULE', 'ST-001', false, false, false, false),
            ('ST-007', 'Универсальная станция №3', ent3_id, ws5_id, secG_id, 'WORKING', 56, 42, 40, 25, 250, 210, 'POSTAMAT_TYPE', NULL, false, true, true, true),
            ('ST-008', 'Инструментальная станция №3', ent3_id, ws5_id, secG_id, 'WORKING', 28, 28, 22, 15, 120, 95, 'DRUM_TYPE', NULL, false, true, false, false),
            ('ST-009', 'Дополнительный модуль 3', ent2_id, ws3_id, secE_id, 'MINIMAL_STOCK', 6, 6, 5, 1, 30, 28, 'ADDITIONAL_MODULE', 'ST-003', false, false, false, false),
            ('ST-010', 'Универсальная станция №4', ent4_id, ws6_id, secH_id, 'WORKING', 64, 50, 48, 30, 300, 250, 'POSTAMAT_TYPE', NULL, false, true, true, false)
    ) AS v(uid, name, enterprise_id, workshop_id, section_id, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, station_type, parent_uid, has_error, is_tmc, is_sgd, is_ok)
    WHERE NOT EXISTS (SELECT 1 FROM stations WHERE stations.uid = v.uid);
END $$;