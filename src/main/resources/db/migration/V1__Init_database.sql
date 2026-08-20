-- V1__Init_database.sql
-- Единая миграция (объединение V1-V28 + Station.id)

BEGIN;

-- ============================================================
-- 1. ПОЛЬЗОВАТЕЛИ И АВТОРИЗАЦИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    middle_name VARCHAR(255),
    last_name VARCHAR(255),
    role_id BIGINT REFERENCES roles(id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token TEXT,
    user_id INTEGER REFERENCES users(id),
    created_at TIMESTAMP,
    expires_at TIMESTAMP,
    is_actual BOOLEAN
);

-- ============================================================
-- 2. ХОЛДИНГИ
-- ============================================================

CREATE TABLE IF NOT EXISTS holdings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 3. ПРЕДПРИЯТИЯ, ЦЕХА, УЧАСТКИ
-- ============================================================

CREATE TABLE IF NOT EXISTS enterprises (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    holding_id BIGINT REFERENCES holdings(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workshops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    enterprise_id BIGINT NOT NULL REFERENCES enterprises(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, enterprise_id)
);

CREATE TABLE IF NOT EXISTS sections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    workshop_id BIGINT NOT NULL REFERENCES workshops(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, workshop_id)
);

-- ============================================================
-- 4. ТИПЫ СТАНЦИЙ, ПРОИЗВОДИТЕЛИ, МОДЕЛИ, КОНФИГУРАЦИИ
-- ============================================================

CREATE TABLE IF NOT EXISTS station_types (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_manufacturers (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_models (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code INTEGER NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    article VARCHAR(255),
    revision VARCHAR(255),
    type_id UUID REFERENCES station_types(uid) ON DELETE SET NULL,
    manufacturer_id UUID REFERENCES station_manufacturers(uid) ON DELETE SET NULL,
    purpose TEXT,
    cells_structure TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_model_images (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_uid UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_configurations (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    model_id UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    cells_structure TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 5. СПРАВОЧНИКИ НОМЕНКЛАТУРЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS data_type (
    uid UUID PRIMARY KEY,
    type_text TEXT,
    type_number DOUBLE PRECISION,
    type_spr UUID
);

CREATE TABLE IF NOT EXISTS spr_type_material (
    uid UUID PRIMARY KEY,
    type_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS spr_type_purpose (
    uid UUID PRIMARY KEY,
    type_name TEXT NOT NULL,
    type_material_uid UUID REFERENCES spr_type_material(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS spr_type_product (
    uid UUID PRIMARY KEY,
    type_name TEXT NOT NULL,
    type_purpose_uid UUID REFERENCES spr_type_purpose(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS spr_measure (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS spr_manufacturer (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS spr_brand (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    manufacturer_uid UUID REFERENCES spr_manufacturer(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS spr_model_of_brand (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    brand UUID REFERENCES spr_brand(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS spr_country (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS spr_type_attributes (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    designation VARCHAR(10),
    data_type UUID REFERENCES data_type(uid) ON DELETE SET NULL
);

-- ============================================================
-- 6. ГРУППЫ МАТЕРИАЛОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_group_material (
    uid UUID PRIMARY KEY,
    group_name TEXT NOT NULL,
    parent_group UUID REFERENCES reg_group_material(uid) ON DELETE SET NULL,
    group_code INTEGER
);

-- ============================================================
-- 7. ПОСТАВЩИКИ
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_supplier_description_types (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS spr_suppliers (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    code INTEGER,
    country_uid UUID REFERENCES spr_country(uid) ON DELETE SET NULL,
    address TEXT,
    short_description_uid UUID REFERENCES spr_supplier_description_types(uid) ON DELETE SET NULL,
    description TEXT,
    email TEXT,
    website TEXT,
    phone TEXT,
    brand_uid UUID REFERENCES spr_brand(uid) ON DELETE SET NULL,
    inn TEXT,
    ogrn TEXT,
    kpp TEXT,
    contact_person TEXT,
    contact_position TEXT,
    contact_phone TEXT,
    director TEXT,
    director_position TEXT,
    bank_name TEXT,
    bik TEXT,
    correspondent_account TEXT,
    settlement_account TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_supplier_images (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_uid UUID NOT NULL REFERENCES spr_suppliers(uid) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    original_name TEXT,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_supplier_documents (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_uid UUID NOT NULL REFERENCES spr_suppliers(uid) ON DELETE CASCADE,
    document_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    original_name TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_supplier_ratings (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_uid UUID NOT NULL REFERENCES spr_suppliers(uid) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    author TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_supplier_integration (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_uid UUID NOT NULL REFERENCES spr_suppliers(uid) ON DELETE CASCADE,
    event TEXT,
    exchange_type TEXT,
    direction TEXT,
    protocol TEXT,
    target_system TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_supplier_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_uid UUID NOT NULL REFERENCES spr_suppliers(uid) ON DELETE CASCADE,
    event_type TEXT,
    event_description TEXT,
    field_name TEXT,
    old_value TEXT,
    new_value TEXT,
    author TEXT,
    source TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 8. ШАБЛОНЫ ПОПОЛНЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS template_categories (
    id BIGSERIAL PRIMARY KEY,
    uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS doc_pattern (
    uid UUID PRIMARY KEY,
    name_pattern TEXT NOT NULL,
    number BIGINT UNIQUE,
    category_id BIGINT REFERENCES template_categories(id) ON DELETE SET NULL,
    configuration TEXT DEFAULT '',
    configuration_uid UUID REFERENCES station_configurations(uid) ON DELETE SET NULL,
    total_cells INTEGER NOT NULL DEFAULT 0,
    filled_cells INTEGER NOT NULL DEFAULT 0,
    free_cells INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 9. СТАНЦИИ (id BIGSERIAL PK + uid UNIQUE)
-- ============================================================

CREATE TABLE IF NOT EXISTS stations (
    id BIGSERIAL PRIMARY KEY,
    uid VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    code INTEGER UNIQUE,
    description TEXT,
    production_date DATE,
    serial_number VARCHAR(255),
    model_id UUID REFERENCES station_models(uid) ON DELETE SET NULL,
    configuration_uid UUID REFERENCES station_configurations(uid) ON DELETE SET NULL,
    enterprise_id BIGINT REFERENCES enterprises(id) ON DELETE SET NULL,
    workshop_id BIGINT REFERENCES workshops(id) ON DELETE SET NULL,
    section_id BIGINT REFERENCES sections(id) ON DELETE SET NULL,
    holding_id BIGINT REFERENCES holdings(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'WORKING',
    total_cells INTEGER NOT NULL DEFAULT 0,
    filled_cells INTEGER NOT NULL DEFAULT 0,
    template_nomenclature_count INTEGER NOT NULL DEFAULT 0,
    remaining_nomenclature_count INTEGER NOT NULL DEFAULT 0,
    max_ready_parts INTEGER NOT NULL DEFAULT 0,
    ready_parts_count INTEGER NOT NULL DEFAULT 0,
    parent_uid VARCHAR(50),
    has_error BOOLEAN NOT NULL DEFAULT FALSE,
    is_tmc BOOLEAN NOT NULL DEFAULT FALSE,
    is_sgd BOOLEAN NOT NULL DEFAULT FALSE,
    is_ok BOOLEAN NOT NULL DEFAULT FALSE,
    is_additional_module BOOLEAN NOT NULL DEFAULT FALSE,
    has_additional_module BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(50),
    network_port INTEGER,
    active_template_uid UUID REFERENCES doc_pattern(uid) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_documents (
    uid UUID PRIMARY KEY,
    station_uid VARCHAR(255) NOT NULL REFERENCES stations(uid) ON DELETE CASCADE,
    document_name VARCHAR(500),
    file_path VARCHAR(1000),
    original_name VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================
-- 10. НОМЕНКЛАТУРА (ОСНОВНАЯ ТАБЛИЦА)
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_material (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guid_1c BIT VARYING(128)[],
    uid_other_sys BIT VARYING(128)[],
    uid_store BIT VARYING(128)[],
    url_image UUID,
    code_material SERIAL NOT NULL,
    group_material UUID REFERENCES reg_group_material(uid) ON DELETE SET NULL,
    type_main UUID REFERENCES spr_type_material(uid) ON DELETE SET NULL,
    type_purpose UUID REFERENCES spr_type_purpose(uid) ON DELETE SET NULL,
    type_product UUID REFERENCES spr_type_product(uid) ON DELETE SET NULL,
    manufacturer UUID REFERENCES spr_manufacturer(uid) ON DELETE SET NULL,
    country UUID REFERENCES spr_country(uid) ON DELETE SET NULL,
    brand UUID REFERENCES spr_brand(uid) ON DELETE SET NULL,
    model_of_brand UUID REFERENCES spr_model_of_brand(uid) ON DELETE SET NULL,
    measure UUID REFERENCES spr_measure(uid) ON DELETE SET NULL,
    name_material TEXT,
    article TEXT,
    description TEXT,
    resharpen BOOLEAN,
    usage BOOLEAN,
    waste_material BOOLEAN,
    recycle_material BOOLEAN,
    attached UUID,
    suppliers UUID,
    attributes UUID,
    price UUID,
    syncronized_mother_system BOOLEAN,
    syncronized_supplier BOOLEAN,
    create_date TIME WITHOUT TIME ZONE DEFAULT now()
);

-- ============================================================
-- 11. ЗАВИСИМЫЕ ОТ spr_material ТАБЛИЦЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_attached (
    uid UUID PRIMARY KEY,
    name_file TEXT NOT NULL,
    url_file UUID NOT NULL,
    link UUID REFERENCES spr_material(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS reg_attributes (
    uid UUID PRIMARY KEY,
    name UUID REFERENCES spr_type_attributes(uid) ON DELETE SET NULL,
    meaning TEXT,
    measure_uid UUID REFERENCES spr_measure(uid) ON DELETE SET NULL,
    material_uid UUID REFERENCES spr_material(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS doc_entrance (
    uid UUID PRIMARY KEY,
    price DOUBLE PRECISION NOT NULL,
    supplier UUID REFERENCES spr_suppliers(uid) ON DELETE SET NULL,
    entrance_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_price (
    uid UUID PRIMARY KEY,
    price DOUBLE PRECISION NOT NULL,
    price_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    link UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    doc_entrance_uid UUID REFERENCES doc_entrance(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS reg_suppliers (
    uid UUID PRIMARY KEY,
    material_uid UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    supplier_uid UUID REFERENCES spr_suppliers(uid) ON DELETE SET NULL,
    supply_date TIMESTAMP,
    document_name TEXT,
    file_path TEXT,
    original_name TEXT
);

-- Добавляем FK для spr_material (attached, suppliers, attributes, price)
ALTER TABLE spr_material
    ADD CONSTRAINT spr_material_attached_fkey FOREIGN KEY (attached)
    REFERENCES reg_attached(uid) ON DELETE SET NULL;

ALTER TABLE spr_material
    ADD CONSTRAINT spr_material_suppliers_fkey FOREIGN KEY (suppliers)
    REFERENCES reg_suppliers(uid) ON DELETE SET NULL;

ALTER TABLE spr_material
    ADD CONSTRAINT spr_material_attributes_fkey FOREIGN KEY (attributes)
    REFERENCES reg_attributes(uid) ON DELETE SET NULL;

ALTER TABLE spr_material
    ADD CONSTRAINT spr_material_price_fkey FOREIGN KEY (price)
    REFERENCES reg_price(uid) ON DELETE SET NULL;

-- ============================================================
-- 12. РЕГИСТРЫ НОМЕНКЛАТУРЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_analog (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    analog_material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    compatibility_percent INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_rating (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 0 AND rating <= 5),
    comment TEXT,
    author TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_integration (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    event TEXT NOT NULL DEFAULT 'Объект синхронизирован',
    exchange_type TEXT NOT NULL,
    direction TEXT NOT NULL,
    protocol TEXT NOT NULL,
    target_system TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS reg_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 13. МЕДИА-ТАБЛИЦЫ НОМЕНКЛАТУРЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_material_images (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    original_name TEXT,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_material_blueprints (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    original_name TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_material_codes (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    original_name TEXT,
    code_type VARCHAR(20) DEFAULT 'QR_CODE',
    code_value TEXT,
    code_kind VARCHAR(20) DEFAULT 'QR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_material_documents (
    uid UUID PRIMARY KEY,
    material_uid UUID NOT NULL REFERENCES spr_material(uid) ON DELETE CASCADE,
    document_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    original_name TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 14. ЯЧЕЙКИ ШАБЛОНОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_cells (
    uid UUID PRIMARY KEY,
    doc_pattern_uid UUID REFERENCES doc_pattern(uid) ON DELETE SET NULL,
    number_cell INTEGER,
    name_material UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
    quantity INTEGER,
    type_main UUID REFERENCES spr_type_material(uid) ON DELETE SET NULL,
    purpose_material TEXT,
    purpose_sgd TEXT,
    max_quantity INTEGER,
    dimensions TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 15. ЗАКАЗЫ AWMS
-- ============================================================

CREATE TABLE IF NOT EXISTS awms_orders_list (
    order_uid VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255),
    order_number VARCHAR(255),
    order_datetime TIMESTAMP WITH TIME ZONE,
    status VARCHAR(255),
    statusreason VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS awms_orders_full (
    order_uid VARCHAR(255) PRIMARY KEY REFERENCES awms_orders_list(order_uid),
    order_json JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS awms_order_statuses (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    order_uid VARCHAR(255) NOT NULL REFERENCES awms_orders_list(order_uid),
    status VARCHAR(255),
    sub_status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS awms_order_tracking (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    order_uid VARCHAR(255) NOT NULL REFERENCES awms_orders_list(order_uid),
    tracking_status VARCHAR(255),
    tracking_sub_status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS awms_tkp_list (
    tkp_uid VARCHAR(255) PRIMARY KEY,
    order_uid VARCHAR(255) NOT NULL REFERENCES awms_orders_list(order_uid),
    customer_id VARCHAR(255),
    order_number VARCHAR(255),
    order_datetime TIMESTAMP WITH TIME ZONE,
    total_cost NUMERIC(15, 2),
    delivery_date DATE,
    status VARCHAR(255),
    statusinvoice VARCHAR(255),
    synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS awms_tkp_full (
    tkp_uid VARCHAR(255) PRIMARY KEY REFERENCES awms_tkp_list(tkp_uid),
    order_uid VARCHAR(255) NOT NULL REFERENCES awms_orders_list(order_uid),
    tkp_json JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS awms_tkp_statuses (
    id BIGSERIAL PRIMARY KEY,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tkp_uid VARCHAR(255) NOT NULL REFERENCES awms_tkp_list(tkp_uid),
    order_uid VARCHAR(255) REFERENCES awms_orders_list(order_uid),
    status VARCHAR(255),
    sub_status VARCHAR(255)
);

-- ============================================================
-- 16. ПРОЧЕЕ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_filters (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filter_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

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

-- ============================================================
-- 17. ИНДЕКСЫ
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_stations_uid ON stations(uid);
CREATE INDEX IF NOT EXISTS idx_stations_status ON stations(status);
CREATE INDEX IF NOT EXISTS idx_stations_parent ON stations(parent_uid);
CREATE INDEX IF NOT EXISTS idx_stations_enterprise ON stations(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_stations_workshop ON stations(workshop_id);
CREATE INDEX IF NOT EXISTS idx_stations_section ON stations(section_id);
CREATE INDEX IF NOT EXISTS idx_stations_model ON stations(model_id);
CREATE INDEX IF NOT EXISTS idx_stations_code ON stations(code);
CREATE INDEX IF NOT EXISTS idx_stations_configuration ON stations(configuration_uid);
CREATE INDEX IF NOT EXISTS idx_stations_holding ON stations(holding_id);

CREATE INDEX IF NOT EXISTS idx_station_models_code ON station_models(code);
CREATE INDEX IF NOT EXISTS idx_station_models_type ON station_models(type_id);
CREATE INDEX IF NOT EXISTS idx_station_models_manufacturer ON station_models(manufacturer_id);

CREATE INDEX IF NOT EXISTS idx_station_model_images_model ON station_model_images(model_uid);

CREATE INDEX IF NOT EXISTS idx_station_configurations_model ON station_configurations(model_id);

CREATE INDEX IF NOT EXISTS idx_station_documents_station ON station_documents(station_uid);

CREATE INDEX IF NOT EXISTS idx_test_documents_user_completed ON test_documents(user_id, completed);

CREATE INDEX IF NOT EXISTS idx_doc_pattern_number ON doc_pattern(number);
CREATE INDEX IF NOT EXISTS idx_doc_pattern_category ON doc_pattern(category_id);
CREATE INDEX IF NOT EXISTS idx_doc_patterns_configuration ON doc_pattern(configuration_uid);

CREATE INDEX IF NOT EXISTS idx_event_log_material ON reg_event_log(material_uid);
CREATE INDEX IF NOT EXISTS idx_event_log_created ON reg_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_enterprises_holding ON enterprises(holding_id);

CREATE INDEX IF NOT EXISTS idx_spr_suppliers_code ON spr_suppliers(code);
CREATE INDEX IF NOT EXISTS idx_spr_suppliers_country ON spr_suppliers(country_uid);
CREATE INDEX IF NOT EXISTS idx_spr_suppliers_brand ON spr_suppliers(brand_uid);

CREATE INDEX IF NOT EXISTS idx_spr_supplier_images_supplier ON spr_supplier_images(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_spr_supplier_documents_supplier ON spr_supplier_documents(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_reg_supplier_ratings_supplier ON reg_supplier_ratings(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_reg_supplier_integration_supplier ON reg_supplier_integration(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_reg_supplier_event_log_supplier ON reg_supplier_event_log(supplier_uid);

CREATE INDEX IF NOT EXISTS idx_awms_order_statuses_uid ON awms_order_statuses(order_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_list_order_uid ON awms_tkp_list(order_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_full_order_uid ON awms_tkp_full(order_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_statuses_tkp_uid ON awms_tkp_statuses(tkp_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_statuses_order_uid ON awms_tkp_statuses(order_uid);

-- ============================================================
-- 18. СИДЫ (НАЧАЛЬНЫЕ ДАННЫЕ)
-- ============================================================

-- Роли
INSERT INTO roles (name, description)
SELECT 'ADMIN', 'Администратор системы'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description)
SELECT 'OPERATOR', 'Оператор'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'OPERATOR');

-- Пользователь admin
INSERT INTO users (username, password, first_name, middle_name, last_name, role_id)
SELECT 'admin',
       '$2y$12$DuBHFqPN/liLWOOpazYz7eStzx9bIaUFQCQP1W52Vxy3JI5BxTEua',
       'Admin',
       'Adminovich',
       'Administrator',
       (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- Холдинги
INSERT INTO holdings (name, description) VALUES
    ('Холдинг Север', 'Северная группа предприятий'),
    ('Холдинг Юг', 'Южная группа предприятий')
ON CONFLICT (name) DO NOTHING;

-- Предприятия
INSERT INTO enterprises (name, holding_id) VALUES
    ('Предприятие №1', (SELECT id FROM holdings WHERE name = 'Холдинг Север')),
    ('Предприятие №2', (SELECT id FROM holdings WHERE name = 'Холдинг Север')),
    ('Предприятие №3', (SELECT id FROM holdings WHERE name = 'Холдинг Юг')),
    ('Предприятие №4', (SELECT id FROM holdings WHERE name = 'Холдинг Юг'))
ON CONFLICT (name) DO NOTHING;

-- Цеха
INSERT INTO workshops (name, enterprise_id) VALUES
    ('Цех №1', (SELECT id FROM enterprises WHERE name = 'Предприятие №1')),
    ('Цех №2', (SELECT id FROM enterprises WHERE name = 'Предприятие №1')),
    ('Цех №1', (SELECT id FROM enterprises WHERE name = 'Предприятие №2')),
    ('Цех №2', (SELECT id FROM enterprises WHERE name = 'Предприятие №2')),
    ('Цех №3', (SELECT id FROM enterprises WHERE name = 'Предприятие №3')),
    ('Цех №4', (SELECT id FROM enterprises WHERE name = 'Предприятие №4'))
ON CONFLICT (name, enterprise_id) DO NOTHING;

-- Участки
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

-- Типы станций (русские названия)
INSERT INTO station_types (uid, name, description) VALUES
    (gen_random_uuid(), 'Барабан', 'Барабанного типа'),
    (gen_random_uuid(), 'Постамат', 'Постамат'),
    (gen_random_uuid(), 'Дополнительный модуль', 'Дополнительный модуль')
ON CONFLICT (name) DO NOTHING;

-- Производители станций
INSERT INTO station_manufacturers (uid, name, description) VALUES
    (gen_random_uuid(), 'СтанкоПром', 'Отечественный производитель станков'),
    (gen_random_uuid(), 'TechMachines', 'Зарубежный производитель оборудования'),
    (gen_random_uuid(), 'ИнструментСервис', 'Производитель инструментальных станций')
ON CONFLICT (name) DO NOTHING;

-- Станции
DO $$
DECLARE
    hold_north_id BIGINT;
    hold_south_id BIGINT;
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
    v_counter INTEGER;
BEGIN
    SELECT id INTO hold_north_id FROM holdings WHERE name = 'Холдинг Север';
    SELECT id INTO hold_south_id FROM holdings WHERE name = 'Холдинг Юг';

    SELECT id INTO ent1_id FROM enterprises WHERE name = 'Предприятие №1';
    SELECT id INTO ent2_id FROM enterprises WHERE name = 'Предприятие №2';
    SELECT id INTO ent3_id FROM enterprises WHERE name = 'Предприятие №3';
    SELECT id INTO ent4_id FROM enterprises WHERE name = 'Предприятие №4';

    SELECT id INTO ws1_id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = ent1_id;
    SELECT id INTO ws2_id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = ent1_id;
    SELECT id INTO ws3_id FROM workshops WHERE name = 'Цех №1' AND enterprise_id = ent2_id;
    SELECT id INTO ws4_id FROM workshops WHERE name = 'Цех №2' AND enterprise_id = ent2_id;
    SELECT id INTO ws5_id FROM workshops WHERE name = 'Цех №3' AND enterprise_id = ent3_id;
    SELECT id INTO ws6_id FROM workshops WHERE name = 'Цех №4' AND enterprise_id = ent4_id;

    SELECT id INTO secA_id FROM sections WHERE name = 'Участок А' AND workshop_id = ws1_id;
    SELECT id INTO secB_id FROM sections WHERE name = 'Участок Б' AND workshop_id = ws1_id;
    SELECT id INTO secC_id FROM sections WHERE name = 'Участок В' AND workshop_id = ws2_id;
    SELECT id INTO secD_id FROM sections WHERE name = 'Участок Г' AND workshop_id = ws2_id;
    SELECT id INTO secE_id FROM sections WHERE name = 'Участок Д' AND workshop_id = ws3_id;
    SELECT id INTO secF_id FROM sections WHERE name = 'Участок Е' AND workshop_id = ws4_id;
    SELECT id INTO secG_id FROM sections WHERE name = 'Участок Ж' AND workshop_id = ws5_id;
    SELECT id INTO secH_id FROM sections WHERE name = 'Участок З' AND workshop_id = ws6_id;

    SELECT COALESCE(MAX(code), 0) INTO v_counter FROM stations;

    INSERT INTO stations (uid, name, code, holding_id, enterprise_id, workshop_id, section_id, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, parent_uid, has_error, is_tmc, is_sgd, is_ok, is_additional_module, has_additional_module)
    SELECT * FROM (VALUES
        ('ST-001', 'Инструментальная станция №1', v_counter + 1, hold_north_id, ent1_id, ws1_id, secA_id, 'WORKING', 24, 18, 15, 9, 100, 67, NULL, false, true, false, true, false, false),
        ('ST-002', 'Инструментальная станция №2', v_counter + 2, hold_north_id, ent1_id, ws1_id, secB_id, 'WORKING', 32, 32, 20, 12, 150, 89, NULL, false, true, false, true, false, false),
        ('ST-003', 'Универсальная станция №1', v_counter + 3, hold_north_id, ent2_id, ws3_id, secE_id, 'MINIMAL_STOCK', 48, 40, 35, 5, 200, 180, NULL, false, true, true, true, false, false),
        ('ST-004', 'Универсальная станция №2', v_counter + 4, hold_north_id, ent2_id, ws4_id, secF_id, 'CRITICAL_STOCK', 40, 38, 30, 2, 180, 175, NULL, true, true, false, false, false, false),
        ('ST-005', 'Дополнительный модуль 1', v_counter + 5, hold_north_id, ent1_id, ws1_id, secA_id, 'OFFLINE', 12, 0, 0, 0, 0, 0, 'ST-001', true, false, false, false, true, false),
        ('ST-006', 'Дополнительный модуль 2', v_counter + 6, hold_north_id, ent1_id, ws1_id, secA_id, 'WORKING', 8, 6, 5, 3, 40, 25, 'ST-001', false, false, false, false, true, false),
        ('ST-007', 'Универсальная станция №3', v_counter + 7, hold_south_id, ent3_id, ws5_id, secG_id, 'WORKING', 56, 42, 40, 25, 250, 210, NULL, false, true, true, true, false, false),
        ('ST-008', 'Инструментальная станция №3', v_counter + 8, hold_south_id, ent3_id, ws5_id, secG_id, 'WORKING', 28, 28, 22, 15, 120, 95, NULL, false, true, false, false, false, false),
        ('ST-009', 'Дополнительный модуль 3', v_counter + 9, hold_north_id, ent2_id, ws3_id, secE_id, 'MINIMAL_STOCK', 6, 6, 5, 1, 30, 28, 'ST-003', false, false, false, false, true, false),
        ('ST-010', 'Универсальная станция №4', v_counter + 10, hold_south_id, ent4_id, ws6_id, secH_id, 'WORKING', 64, 50, 48, 30, 300, 250, NULL, false, true, true, false, false, false)
    ) AS t(uid, name, code, holding_id, enterprise_id, workshop_id, section_id, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, parent_uid, has_error, is_tmc, is_sgd, is_ok, is_additional_module, has_additional_module)
    WHERE NOT EXISTS (SELECT 1 FROM stations s WHERE s.uid = t.uid);
END $$;

-- Группы учета
INSERT INTO spr_type_material (uid, type_name)
SELECT gen_random_uuid(), 'ТМЦ'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_material WHERE type_name = 'ТМЦ');

INSERT INTO spr_type_material (uid, type_name)
SELECT gen_random_uuid(), 'Готовая деталь'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_material WHERE type_name = 'Готовая деталь');

-- Группы номенклатуры
DO $$
DECLARE
    tmc_uid UUID;
    ready_uid UUID;
BEGIN
    SELECT uid INTO tmc_uid FROM spr_type_material WHERE type_name = 'ТМЦ';
    SELECT uid INTO ready_uid FROM spr_type_material WHERE type_name = 'Готовая деталь';

    INSERT INTO spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Металлообрабатывающий инструмент', tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент');

    INSERT INTO spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Слесарный инструмент', tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_purpose WHERE type_name = 'Слесарный инструмент');

    INSERT INTO spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Оснастка', tmc_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_purpose WHERE type_name = 'Оснастка');

    INSERT INTO spr_type_purpose (uid, type_name, type_material_uid)
    SELECT gen_random_uuid(), 'Готовые детали', ready_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_purpose WHERE type_name = 'Готовые детали' AND type_material_uid = ready_uid);
END $$;

-- Виды номенклатуры
DO $$
DECLARE
    metal_uid UUID;
    slesar_uid UUID;
    osnastka_uid UUID;
    ready_uid UUID;
BEGIN
    SELECT uid INTO metal_uid FROM spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент';
    SELECT uid INTO slesar_uid FROM spr_type_purpose WHERE type_name = 'Слесарный инструмент';
    SELECT uid INTO osnastka_uid FROM spr_type_purpose WHERE type_name = 'Оснастка';
    SELECT uid INTO ready_uid FROM spr_type_purpose WHERE type_name = 'Готовые детали';

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Сверло', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Сверло' AND type_purpose_uid = metal_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Фреза', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Фреза' AND type_purpose_uid = metal_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Резец', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Резец' AND type_purpose_uid = metal_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Метчик', metal_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Метчик' AND type_purpose_uid = metal_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Молоток', slesar_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Молоток' AND type_purpose_uid = slesar_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Отвертка', slesar_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Отвертка' AND type_purpose_uid = slesar_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Ключ гаечный', slesar_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Ключ гаечный' AND type_purpose_uid = slesar_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Тиски', osnastka_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Тиски' AND type_purpose_uid = osnastka_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Патрон', osnastka_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Патрон' AND type_purpose_uid = osnastka_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Кондуктор', osnastka_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Кондуктор' AND type_purpose_uid = osnastka_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Вал', ready_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Вал' AND type_purpose_uid = ready_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Втулка', ready_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Втулка' AND type_purpose_uid = ready_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Корпус', ready_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Корпус' AND type_purpose_uid = ready_uid);

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid)
    SELECT gen_random_uuid(), 'Крышка', ready_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_type_product WHERE type_name = 'Крышка' AND type_purpose_uid = ready_uid);
END $$;

-- Единицы измерения
INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'мм', 'Миллиметр'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'мм');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'см', 'Сантиметр'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'см');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'м', 'Метр'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'м');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'шт', 'Штука'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'шт');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'кг', 'Килограмм'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'кг');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'л', 'Литр'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'л');

INSERT INTO spr_measure (uid, name, description)
SELECT gen_random_uuid(), 'компл', 'Комплект'
WHERE NOT EXISTS (SELECT 1 FROM spr_measure WHERE name = 'компл');

-- Производители
INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "СтанкоДеталь"', 'Производство оснастки'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'АО "ПромТех"', 'Промышленное оборудование'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'АО "ПромТех"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ИП Иванов', 'Метизы'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ИП Иванов');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'ООО "СмазТех"', 'Смазочные материалы'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'ООО "СмазТех"');

INSERT INTO spr_manufacturer (uid, name, description)
SELECT gen_random_uuid(), 'АО "ЭлектроПром"', 'Электрооборудование'
WHERE NOT EXISTS (SELECT 1 FROM spr_manufacturer WHERE name = 'АО "ЭлектроПром"');

-- Бренды
DO $$
DECLARE
    stanko_uid UUID;
    promtex_uid UUID;
    electroprom_uid UUID;
BEGIN
    SELECT uid INTO stanko_uid FROM spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"';
    SELECT uid INTO promtex_uid FROM spr_manufacturer WHERE name = 'АО "ПромТех"';
    SELECT uid INTO electroprom_uid FROM spr_manufacturer WHERE name = 'АО "ЭлектроПром"';

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'SKF', 'Подшипники SKF', stanko_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'SKF');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'FAG', 'Подшипники FAG', stanko_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'FAG');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'Gates', 'Ремни Gates', promtex_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'Gates');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'Mobil', 'Смазки Mobil', promtex_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'Mobil');

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid)
    SELECT gen_random_uuid(), 'Shell', 'Масла Shell', electroprom_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_brand WHERE name = 'Shell');
END $$;

-- Модели брендов
DO $$
DECLARE
    skf_uid UUID;
    fag_uid UUID;
    gates_uid UUID;
    mobil_uid UUID;
BEGIN
    SELECT uid INTO skf_uid FROM spr_brand WHERE name = 'SKF';
    SELECT uid INTO fag_uid FROM spr_brand WHERE name = 'FAG';
    SELECT uid INTO gates_uid FROM spr_brand WHERE name = 'Gates';
    SELECT uid INTO mobil_uid FROM spr_brand WHERE name = 'Mobil';

    INSERT INTO spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), '6204-2RS', 'Шариковый радиальный', skf_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_model_of_brand WHERE name = '6204-2RS');

    INSERT INTO spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), '6205-C3', 'Шариковый радиальный', fag_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_model_of_brand WHERE name = '6205-C3');

    INSERT INTO spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), '6306-ZZ', 'Шариковый с защитой', skf_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_model_of_brand WHERE name = '6306-ZZ');

    INSERT INTO spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), 'A-1000', 'Ремень клиновой', gates_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_model_of_brand WHERE name = 'A-1000');

    INSERT INTO spr_model_of_brand (uid, name, description, brand)
    SELECT gen_random_uuid(), 'Mobilux EP2', 'Смазка литиевая', mobil_uid
    WHERE NOT EXISTS (SELECT 1 FROM spr_model_of_brand WHERE name = 'Mobilux EP2');
END $$;

-- Страны
INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Россия'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Россия');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Германия'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Германия');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Япония'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Япония');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'США'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'США');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Китай'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Китай');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Италия'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Италия');

INSERT INTO spr_country (uid, name)
SELECT gen_random_uuid(), 'Франция'
WHERE NOT EXISTS (SELECT 1 FROM spr_country WHERE name = 'Франция');

-- Виды характеристик
INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Длина', 'L'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Длина');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Ширина', 'W'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Ширина');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Глубина', 'D'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Глубина');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Высота', 'H'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Высота');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Масса', 'M'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Масса');

INSERT INTO spr_type_attributes (uid, name, designation)
SELECT gen_random_uuid(), 'Срок эксплуатации', 'T'
WHERE NOT EXISTS (SELECT 1 FROM spr_type_attributes WHERE name = 'Срок эксплуатации');

-- Корневая группа номенклатуры
INSERT INTO reg_group_material (uid, group_name, parent_group, group_code)
SELECT gen_random_uuid(), 'Номенклатура', NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM reg_group_material WHERE group_code = 0);

-- Типы описаний поставщиков
INSERT INTO spr_supplier_description_types (uid, name) VALUES
    (gen_random_uuid(), 'Производитель'),
    (gen_random_uuid(), 'Официальный дистрибьютор'),
    (gen_random_uuid(), 'Дилер'),
    (gen_random_uuid(), 'Оптовый поставщик'),
    (gen_random_uuid(), 'Розничный поставщик'),
    (gen_random_uuid(), 'Импортер'),
    (gen_random_uuid(), 'Сервисный центр'),
    (gen_random_uuid(), 'Партнер')
ON CONFLICT DO NOTHING;

-- Категории шаблонов
INSERT INTO template_categories (name) VALUES
    ('Инструментальные'),
    ('Универсальные'),
    ('Специальные'),
    ('Тестовые')
ON CONFLICT (name) DO NOTHING;

COMMIT;