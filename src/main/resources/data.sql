-- Создание роли ADMIN если её нет
INSERT INTO roles (name, description) 
SELECT 'ADMIN', 'Администратор системы'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

-- Создание пользователя admin если его нет
-- Пароль: Tuduta95 (хешированный BCrypt)
INSERT INTO users (username, password, first_name, middle_name, last_name, role_id)
SELECT 'admin', 
       '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVFUiA', -- Tuduta95
       'Admin',
       'Adminovich',
       'Administrator',
       (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');