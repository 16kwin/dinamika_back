-- V26__Add_holdings.sql

-- 1. Таблица холдингов
CREATE TABLE IF NOT EXISTS holdings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Добавляем holding_id в enterprises
ALTER TABLE IF EXISTS enterprises
    ADD COLUMN IF NOT EXISTS holding_id BIGINT REFERENCES holdings(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_enterprises_holding ON enterprises(holding_id);

-- 3. Добавляем holding_id в stations
ALTER TABLE IF EXISTS stations
    ADD COLUMN IF NOT EXISTS holding_id BIGINT REFERENCES holdings(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_stations_holding ON stations(holding_id);

-- 4. Тестовые холдинги
INSERT INTO holdings (name, description) VALUES 
    ('Холдинг Север', 'Северная группа предприятий'),
    ('Холдинг Юг', 'Южная группа предприятий')
ON CONFLICT (name) DO NOTHING;

-- 5. Привязываем предприятия к холдингам
UPDATE enterprises SET holding_id = (SELECT id FROM holdings WHERE name = 'Холдинг Север')
    WHERE name IN ('Предприятие №1', 'Предприятие №2')
    AND holding_id IS NULL;

UPDATE enterprises SET holding_id = (SELECT id FROM holdings WHERE name = 'Холдинг Юг')
    WHERE name IN ('Предприятие №3', 'Предприятие №4')
    AND holding_id IS NULL;