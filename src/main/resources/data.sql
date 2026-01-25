-- Создание роли ADMIN если её нет
INSERT INTO roles (name, description) 
SELECT 'ADMIN', 'Администратор системы'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

-- Создание пользователя admin если его нет
-- Пароль: Tuduta95 (хешированный BCrypt)
INSERT INTO users (username, password, first_name, middle_name, last_name, role_id)
SELECT 'admin', 
       '$2a$12$JHJRYuHX7/rkte1QhaBLr.vpk1d7uLZKv008VfxdEsrt8wAouCev.', -- Tuduta95
       'Admin',
       'Adminovich',
       'Administrator',
       (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Создание тестовой модели станции если её нет
INSERT INTO station_model (model_number)
SELECT 100
WHERE NOT EXISTS (SELECT 1 FROM station_model WHERE model_number = 100);

-- Создание тестового завода если его нет
INSERT INTO location (location_name, level)
SELECT 'Тестовый завод', 1
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = 'Тестовый завод' AND level = 1);

-- Создание тестового цеха если его нет
INSERT INTO location (location_name, level)
SELECT 'Тестовый цех', 2
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = 'Тестовый цех' AND level = 2);

-- Создание тестового участка если его нет
INSERT INTO location (location_name, level)
SELECT 'Тестовый участок', 3
WHERE NOT EXISTS (SELECT 1 FROM location WHERE location_name = 'Тестовый участок' AND level = 3);

-- Связь: Тестовый цех → Тестовый завод (если связи нет)
INSERT INTO location_dependency (child_location_id, parent_location_id)
SELECT 
    (SELECT id FROM location WHERE location_name = 'Тестовый цех' AND level = 2),
    (SELECT id FROM location WHERE location_name = 'Тестовый завод' AND level = 1)
WHERE NOT EXISTS (
    SELECT 1 FROM location_dependency 
    WHERE child_location_id = (SELECT id FROM location WHERE location_name = 'Тестовый цех' AND level = 2)
);

-- Связь: Тестовый участок → Тестовый цех (если связи нет)
INSERT INTO location_dependency (child_location_id, parent_location_id)
SELECT 
    (SELECT id FROM location WHERE location_name = 'Тестовый участок' AND level = 3),
    (SELECT id FROM location WHERE location_name = 'Тестовый цех' AND level = 2)
WHERE NOT EXISTS (
    SELECT 1 FROM location_dependency 
    WHERE child_location_id = (SELECT id FROM location WHERE location_name = 'Тестовый участок' AND level = 3)
);

-- Создание тестового станка если его нет
-- Станок привязан к участку (все три уровня)
INSERT INTO station (
    station_name, 
    station_model_id, 
    serial_number, 
    current_capacity, 
    ip_address, 
    is_enabled,
    capacity,
    fullness,
    has_errors,
    issued,
    issued_over_norm,
    finished_parts,
    level_1_factory_id, 
    level_2_object_id, 
    level_3_zone_id
)
SELECT 
    'Тестовая станция',
    (SELECT id FROM station_model WHERE model_number = 100),
    12345,
    500,
    '192.168.1.100',
    true, -- is_enabled
    1000, -- capacity
    350,  -- fullness
    false, -- has_errors
    150,   -- issued
    25,    -- issued_over_norm
    85,    -- finished_parts
    (SELECT id FROM location WHERE location_name = 'Тестовый завод' AND level = 1),
    (SELECT id FROM location WHERE location_name = 'Тестовый цех' AND level = 2),
    (SELECT id FROM location WHERE location_name = 'Тестовый участок' AND level = 3)
WHERE NOT EXISTS (SELECT 1 FROM station WHERE station_name = 'Тестовая станция');