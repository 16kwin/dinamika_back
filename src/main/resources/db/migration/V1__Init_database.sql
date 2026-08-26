-- V1__Init_database.sql
-- Единая миграция (объединение V1-V20 + все сиды)

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
-- 2. СПРАВОЧНИКИ (БАЗОВЫЕ)
-- ============================================================

CREATE TABLE IF NOT EXISTS data_type (
    uid UUID PRIMARY KEY,
    type_text TEXT,
    type_number DOUBLE PRECISION,
    type_spr UUID
);

CREATE TABLE IF NOT EXISTS spr_country (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS spr_measure (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    group_uid UUID
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

CREATE TABLE IF NOT EXISTS reg_group_material (
    uid UUID PRIMARY KEY,
    group_name TEXT NOT NULL,
    parent_group UUID REFERENCES reg_group_material(uid) ON DELETE SET NULL,
    group_code INTEGER
);

-- ============================================================
-- 3. ХОЛДИНГИ, ПРЕДПРИЯТИЯ, ЦЕХА, УЧАСТКИ, РАСПОЛОЖЕНИЯ
-- ============================================================

CREATE TABLE IF NOT EXISTS locations (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS holdings (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    location_uuid UUID REFERENCES locations(uid) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS enterprises (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    holding_id BIGINT REFERENCES holdings(id) ON DELETE SET NULL,
    location_uuid UUID REFERENCES locations(uid) ON DELETE SET NULL,
    description VARCHAR(500),
    address VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workshops (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    enterprise_id BIGINT NOT NULL REFERENCES enterprises(id) ON DELETE CASCADE,
    location_uuid UUID REFERENCES locations(uid) ON DELETE SET NULL,
    description VARCHAR(500),
    address VARCHAR(500),
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
    country_uuid UUID REFERENCES spr_country(uid) ON DELETE SET NULL,
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

CREATE TABLE IF NOT EXISTS station_model_documents (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_uid UUID NOT NULL REFERENCES station_models(uid) ON DELETE CASCADE,
    document_name VARCHAR(500),
    file_path VARCHAR(1000),
    original_name VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
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
-- 5. ГРУППЫ ХАРАКТЕРИСТИК, ВИДЫ ХАРАКТЕРИСТИК, ЕДИНИЦЫ
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_attribute_group (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_type_attributes (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    designation VARCHAR(10),
    data_type UUID REFERENCES data_type(uid) ON DELETE SET NULL,
    group_uid UUID REFERENCES spr_attribute_group(uid) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS spr_unit (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 6. ПРОИЗВОДИТЕЛИ НОМЕНКЛАТУРЫ, БРЕНДЫ, МОДЕЛИ БРЕНДОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_production_direction (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_manufacturer (
    uid UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    code INTEGER UNIQUE,
    country_uid UUID REFERENCES spr_country(uid) ON DELETE SET NULL,
    direction_uid UUID REFERENCES spr_production_direction(uid) ON DELETE SET NULL,
    address VARCHAR(500),
    email VARCHAR(255),
    website VARCHAR(255),
    phone VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS spr_manufacturer_images (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_uid UUID NOT NULL REFERENCES spr_manufacturer(uid) ON DELETE CASCADE,
    file_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS spr_manufacturer_documents (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_uid UUID NOT NULL REFERENCES spr_manufacturer(uid) ON DELETE CASCADE,
    document_name VARCHAR(500),
    file_path VARCHAR(1000),
    original_name VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
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
    brand UUID REFERENCES spr_brand(uid) ON DELETE SET NULL,
    manufacturer_uid UUID REFERENCES spr_manufacturer(uid) ON DELETE SET NULL
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

CREATE TABLE IF NOT EXISTS spr_supplier_brand (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    supplier_uid UUID REFERENCES spr_suppliers(uid) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, supplier_uid)
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
    supplier_uid UUID REFERENCES spr_suppliers(uid) ON DELETE SET NULL,
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
-- 9. СТАНЦИИ
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

CREATE TABLE IF NOT EXISTS station_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_uid VARCHAR(50) REFERENCES stations(uid) ON DELETE SET NULL,
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
-- 10. НОМЕНКЛАТУРА
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
-- 11. РЕГИСТРЫ НОМЕНКЛАТУРЫ
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
    material_uid UUID REFERENCES spr_material(uid) ON DELETE SET NULL,
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
-- 12. МЕДИА-ТАБЛИЦЫ НОМЕНКЛАТУРЫ
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
-- 13. ЯЧЕЙКИ ШАБЛОНОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS reg_cells (
    uid UUID PRIMARY KEY,
    doc_pattern_uid UUID REFERENCES doc_pattern(uid) ON DELETE SET NULL,
    number_cell INTEGER,
    column_number INTEGER,
    drum_number INTEGER,
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
-- 14. ЗАКАЗЫ AWMS
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
-- 15. ПРОЧЕЕ
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
-- 16. ТАБЛИЦЫ НАСТРОЕК КОЛОНОК
-- ============================================================

CREATE TABLE IF NOT EXISTS user_station_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_location_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_country_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_station_type_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_station_manufacturer_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_holding_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_enterprise_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_workshop_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_section_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_station_model_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_station_configuration_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_nomenclature_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    current_path_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_type_material_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_type_purpose_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_type_product_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_attribute_group_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_type_attribute_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_measure_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_unit_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_production_direction_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_manufacturer_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_brand_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_model_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_supplier_direction_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE TABLE IF NOT EXISTS user_supplier_brand_column_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- ============================================================
-- 17. ТАБЛИЦЫ СОБЫТИЙ (EVENT LOG)
-- ============================================================

CREATE TABLE IF NOT EXISTS location_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_uid UUID REFERENCES locations(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS country_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_uid UUID REFERENCES spr_country(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_type_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_type_uid UUID REFERENCES station_types(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_manufacturer_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_manufacturer_uid UUID REFERENCES station_manufacturers(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS holding_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id BIGINT REFERENCES holdings(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS enterprise_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enterprise_id BIGINT REFERENCES enterprises(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workshop_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workshop_id BIGINT REFERENCES workshops(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS section_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    section_id BIGINT REFERENCES sections(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_model_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_model_uid UUID REFERENCES station_models(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS station_configuration_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    station_configuration_uid UUID REFERENCES station_configurations(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS type_material_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_material_uid UUID REFERENCES spr_type_material(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS type_purpose_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_purpose_uid UUID REFERENCES spr_type_purpose(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS type_product_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_product_uid UUID REFERENCES spr_type_product(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attribute_group_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attribute_group_uid UUID REFERENCES spr_attribute_group(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS type_attribute_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type_attribute_uid UUID REFERENCES spr_type_attributes(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS measure_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    measure_uid UUID REFERENCES spr_measure(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS unit_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    unit_uid UUID REFERENCES spr_unit(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS production_direction_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    production_direction_uid UUID REFERENCES spr_production_direction(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS manufacturer_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer_uid UUID REFERENCES spr_manufacturer(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS brand_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_uid UUID REFERENCES spr_brand(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_uid UUID REFERENCES spr_model_of_brand(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supplier_direction_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_direction_uid UUID REFERENCES spr_supplier_description_types(uid) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL,
    event_description TEXT NOT NULL,
    field_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    author VARCHAR(255),
    source VARCHAR(100) DEFAULT 'Через карточку',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supplier_brand_event_log (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_brand_uid UUID REFERENCES spr_supplier_brand(uid) ON DELETE SET NULL,
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
-- 18. ИНДЕКСЫ
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
CREATE INDEX IF NOT EXISTS idx_station_model_documents_model ON station_model_documents(model_uid);

CREATE INDEX IF NOT EXISTS idx_station_configurations_model ON station_configurations(model_id);

CREATE INDEX IF NOT EXISTS idx_station_documents_station ON station_documents(station_uid);
CREATE INDEX IF NOT EXISTS idx_station_event_log_station ON station_event_log(station_uid);
CREATE INDEX IF NOT EXISTS idx_station_event_log_created ON station_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_test_documents_user_completed ON test_documents(user_id, completed);

CREATE INDEX IF NOT EXISTS idx_doc_pattern_number ON doc_pattern(number);
CREATE INDEX IF NOT EXISTS idx_doc_pattern_category ON doc_pattern(category_id);
CREATE INDEX IF NOT EXISTS idx_doc_patterns_configuration ON doc_pattern(configuration_uid);

CREATE INDEX IF NOT EXISTS idx_event_log_material ON reg_event_log(material_uid);
CREATE INDEX IF NOT EXISTS idx_event_log_created ON reg_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_enterprises_holding ON enterprises(holding_id);

CREATE INDEX IF NOT EXISTS idx_spr_suppliers_code ON spr_suppliers(code);
CREATE INDEX IF NOT EXISTS idx_spr_suppliers_country ON spr_suppliers(country_uid);

CREATE INDEX IF NOT EXISTS idx_spr_supplier_images_supplier ON spr_supplier_images(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_spr_supplier_documents_supplier ON spr_supplier_documents(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_reg_supplier_ratings_supplier ON reg_supplier_ratings(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_reg_supplier_integration_supplier ON reg_supplier_integration(supplier_uid);
CREATE INDEX IF NOT EXISTS idx_reg_supplier_event_log_supplier ON reg_supplier_event_log(supplier_uid);

CREATE INDEX IF NOT EXISTS idx_spr_supplier_brand_supplier ON spr_supplier_brand(supplier_uid);

CREATE INDEX IF NOT EXISTS idx_locations_name ON locations(name);
CREATE INDEX IF NOT EXISTS idx_holdings_location ON holdings(location_uuid);
CREATE INDEX IF NOT EXISTS idx_enterprises_location ON enterprises(location_uuid);
CREATE INDEX IF NOT EXISTS idx_workshops_location ON workshops(location_uuid);
CREATE INDEX IF NOT EXISTS idx_station_manufacturers_country ON station_manufacturers(country_uuid);

CREATE INDEX IF NOT EXISTS idx_spr_type_attributes_group ON spr_type_attributes(group_uid);
CREATE INDEX IF NOT EXISTS idx_spr_measure_group ON spr_measure(group_uid);

CREATE INDEX IF NOT EXISTS idx_spr_manufacturer_code ON spr_manufacturer(code);
CREATE INDEX IF NOT EXISTS idx_spr_manufacturer_country ON spr_manufacturer(country_uid);
CREATE INDEX IF NOT EXISTS idx_spr_manufacturer_direction ON spr_manufacturer(direction_uid);

CREATE INDEX IF NOT EXISTS idx_spr_model_of_brand_manufacturer ON spr_model_of_brand(manufacturer_uid);

CREATE INDEX IF NOT EXISTS idx_manufacturer_images_manufacturer ON spr_manufacturer_images(manufacturer_uid);
CREATE INDEX IF NOT EXISTS idx_manufacturer_documents_manufacturer ON spr_manufacturer_documents(manufacturer_uid);

CREATE INDEX IF NOT EXISTS idx_awms_order_statuses_uid ON awms_order_statuses(order_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_list_order_uid ON awms_tkp_list(order_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_full_order_uid ON awms_tkp_full(order_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_statuses_tkp_uid ON awms_tkp_statuses(tkp_uid);
CREATE INDEX IF NOT EXISTS idx_awms_tkp_statuses_order_uid ON awms_tkp_statuses(order_uid);

CREATE INDEX IF NOT EXISTS idx_user_nomenclature_settings_user ON user_nomenclature_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_type_material_settings_user ON user_type_material_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_type_purpose_settings_user ON user_type_purpose_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_type_product_settings_user ON user_type_product_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_attribute_group_settings_user ON user_attribute_group_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_type_attribute_settings_user ON user_type_attribute_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_measure_settings_user ON user_measure_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_unit_settings_user ON user_unit_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_production_direction_settings_user ON user_production_direction_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_manufacturer_settings_user ON user_manufacturer_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_brand_settings_user ON user_brand_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_model_settings_user ON user_model_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_supplier_direction_settings_user ON user_supplier_direction_column_settings(user_id);
CREATE INDEX IF NOT EXISTS idx_user_supplier_brand_settings_user ON user_supplier_brand_column_settings(user_id);

-- ============================================================
-- 19. СИДЫ: РОЛИ И ПОЛЬЗОВАТЕЛИ
-- ============================================================

INSERT INTO roles (name, description) VALUES
    ('ADMIN', 'Администратор системы'),
    ('OPERATOR', 'Оператор')
ON CONFLICT (name) DO NOTHING;

INSERT INTO users (username, password, first_name, middle_name, last_name, role_id)
SELECT 'admin',
       '$2y$12$DuBHFqPN/liLWOOpazYz7eStzx9bIaUFQCQP1W52Vxy3JI5BxTEua',
       'Admin',
       'Adminovich',
       'Administrator',
       (SELECT id FROM roles WHERE name = 'ADMIN')
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

-- ============================================================
-- 20. СИДЫ: СТРАНЫ
-- ============================================================

INSERT INTO spr_country (uid, name) VALUES
    (gen_random_uuid(), 'Россия'),
    (gen_random_uuid(), 'Германия'),
    (gen_random_uuid(), 'Япония'),
    (gen_random_uuid(), 'США'),
    (gen_random_uuid(), 'Китай'),
    (gen_random_uuid(), 'Италия'),
    (gen_random_uuid(), 'Франция'),
    (gen_random_uuid(), 'Беларусь')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 21. СИДЫ: ЕДИНИЦЫ ИЗМЕРЕНИЯ
-- ============================================================

INSERT INTO spr_measure (uid, name, description) VALUES
    (gen_random_uuid(), 'мм', 'Миллиметр'),
    (gen_random_uuid(), 'см', 'Сантиметр'),
    (gen_random_uuid(), 'м', 'Метр'),
    (gen_random_uuid(), 'шт', 'Штука'),
    (gen_random_uuid(), 'кг', 'Килограмм'),
    (gen_random_uuid(), 'л', 'Литр'),
    (gen_random_uuid(), 'компл', 'Комплект'),
    (gen_random_uuid(), 'град', 'Градус')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 22. СИДЫ: ГРУППЫ ХАРАКТЕРИСТИК
-- ============================================================

INSERT INTO spr_attribute_group (uid, name) VALUES
    (gen_random_uuid(), 'Геометрические'),
    (gen_random_uuid(), 'Физические'),
    (gen_random_uuid(), 'Эксплуатационные')
ON CONFLICT (name) DO NOTHING;

UPDATE spr_measure SET group_uid = (SELECT uid FROM spr_attribute_group WHERE name = 'Геометрические') WHERE name IN ('мм', 'см', 'м');
UPDATE spr_measure SET group_uid = (SELECT uid FROM spr_attribute_group WHERE name = 'Физические') WHERE name IN ('кг', 'л');
UPDATE spr_measure SET group_uid = (SELECT uid FROM spr_attribute_group WHERE name = 'Эксплуатационные') WHERE name IN ('шт', 'компл', 'град');

-- ============================================================
-- 23. СИДЫ: ЕДИНИЦЫ ИЗМЕРЕНИЯ (НОМЕНКЛАТУРА)
-- ============================================================

INSERT INTO spr_unit (uid, name, description) VALUES
    (gen_random_uuid(), 'шт', 'Штука'),
    (gen_random_uuid(), 'кг', 'Килограмм'),
    (gen_random_uuid(), 'упаковка', 'Упаковка')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 24. СИДЫ: НАПРАВЛЕНИЯ ПРОИЗВОДСТВА
-- ============================================================

INSERT INTO spr_production_direction (uid, name) VALUES
    (gen_random_uuid(), 'Металлообработка'),
    (gen_random_uuid(), 'Производство оснастки'),
    (gen_random_uuid(), 'Электрооборудование'),
    (gen_random_uuid(), 'Смазочные материалы'),
    (gen_random_uuid(), 'Метизы')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 25. СИДЫ: РАСПОЛОЖЕНИЯ
-- ============================================================

INSERT INTO locations (uid, name) VALUES
    (gen_random_uuid(), 'Москва'),
    (gen_random_uuid(), 'Санкт-Петербург'),
    (gen_random_uuid(), 'Казань'),
    (gen_random_uuid(), 'Новосибирск')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 26. СИДЫ: ХОЛДИНГИ
-- ============================================================

INSERT INTO holdings (name, description, location_uuid) VALUES
    ('Холдинг Север', 'Северная группа предприятий', (SELECT uid FROM locations WHERE name = 'Москва')),
    ('Холдинг Юг', 'Южная группа предприятий', (SELECT uid FROM locations WHERE name = 'Казань'))
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 27. СИДЫ: ПРЕДПРИЯТИЯ
-- ============================================================

INSERT INTO enterprises (name, holding_id, location_uuid, description, address) VALUES
    ('Предприятие №1', (SELECT id FROM holdings WHERE name = 'Холдинг Север'), (SELECT uid FROM locations WHERE name = 'Москва'), 'Основное производство', '125212, г. Москва, ул. Адмирала Макарова, д. 10'),
    ('Предприятие №2', (SELECT id FROM holdings WHERE name = 'Холдинг Север'), (SELECT uid FROM locations WHERE name = 'Санкт-Петербург'), 'Производство оснастки', '195112, г. Санкт-Петербург, Малоохтинский пр., д. 45'),
    ('Предприятие №3', (SELECT id FROM holdings WHERE name = 'Холдинг Юг'), (SELECT uid FROM locations WHERE name = 'Казань'), 'Металлообработка', '420107, г. Казань, ул. Спартаковская, д. 2'),
    ('Предприятие №4', (SELECT id FROM holdings WHERE name = 'Холдинг Юг'), (SELECT uid FROM locations WHERE name = 'Новосибирск'), 'Сборочное производство', '630007, г. Новосибирск, ул. Серебренниковская, д. 19')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 28. СИДЫ: ЦЕХА
-- ============================================================

INSERT INTO workshops (name, enterprise_id, location_uuid, description, address) VALUES
    ('Цех №1', (SELECT id FROM enterprises WHERE name = 'Предприятие №1'), (SELECT uid FROM locations WHERE name = 'Москва'), 'Цех металлообработки', '125212, г. Москва, ул. Адмирала Макарова, д. 10, корп. 1'),
    ('Цех №2', (SELECT id FROM enterprises WHERE name = 'Предприятие №1'), (SELECT uid FROM locations WHERE name = 'Москва'), 'Цех финишной обработки', '125212, г. Москва, ул. Адмирала Макарова, д. 10, корп. 2'),
    ('Цех №1', (SELECT id FROM enterprises WHERE name = 'Предприятие №2'), (SELECT uid FROM locations WHERE name = 'Санкт-Петербург'), 'Инструментальный цех', '195112, г. Санкт-Петербург, Малоохтинский пр., д. 45, корп. 1'),
    ('Цех №2', (SELECT id FROM enterprises WHERE name = 'Предприятие №2'), (SELECT uid FROM locations WHERE name = 'Санкт-Петербург'), 'Сборочный цех', '195112, г. Санкт-Петербург, Малоохтинский пр., д. 45, корп. 2'),
    ('Цех №3', (SELECT id FROM enterprises WHERE name = 'Предприятие №3'), (SELECT uid FROM locations WHERE name = 'Казань'), 'Основной цех', '420107, г. Казань, ул. Спартаковская, д. 2, корп. 1'),
    ('Цех №4', (SELECT id FROM enterprises WHERE name = 'Предприятие №4'), (SELECT uid FROM locations WHERE name = 'Новосибирск'), 'Сборочный цех', '630007, г. Новосибирск, ул. Серебренниковская, д. 19, корп. 1')
ON CONFLICT (name, enterprise_id) DO NOTHING;

-- ============================================================
-- 29. СИДЫ: УЧАСТКИ
-- ============================================================

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

-- ============================================================
-- 30. СИДЫ: ТИПЫ СТАНЦИЙ
-- ============================================================

INSERT INTO station_types (uid, name, description) VALUES
    (gen_random_uuid(), 'Барабан', 'Барабанного типа'),
    (gen_random_uuid(), 'Постамат', 'Постамат'),
    (gen_random_uuid(), 'Дополнительный модуль', 'Дополнительный модуль')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 31. СИДЫ: ПРОИЗВОДИТЕЛИ СТАНЦИЙ
-- ============================================================

INSERT INTO station_manufacturers (uid, name, description, country_uuid) VALUES
    (gen_random_uuid(), 'СтанкоПром', 'Отечественный производитель станков', (SELECT uid FROM spr_country WHERE name = 'Россия')),
    (gen_random_uuid(), 'TechMachines', 'Зарубежный производитель оборудования', (SELECT uid FROM spr_country WHERE name = 'Германия')),
    (gen_random_uuid(), 'ИнструментСервис', 'Производитель инструментальных станций', (SELECT uid FROM spr_country WHERE name = 'Россия'))
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 32. СИДЫ: МОДЕЛИ СТАНЦИЙ
-- ============================================================

INSERT INTO station_models (uid, code, name, article, revision, type_id, manufacturer_id, purpose, cells_structure) VALUES
    (gen_random_uuid(), 1001, 'Барабан СТ-100', 'АРТ-1001', 'Rev A', (SELECT uid FROM station_types WHERE name = 'Барабан'), (SELECT uid FROM station_manufacturers WHERE name = 'СтанкоПром'), 'Инструментальная станция', '{"drums": 2, "columns_per_drum": 12}'),
    (gen_random_uuid(), 1002, 'Барабан СТ-200', 'АРТ-1002', 'Rev B', (SELECT uid FROM station_types WHERE name = 'Барабан'), (SELECT uid FROM station_manufacturers WHERE name = 'TechMachines'), 'Универсальная станция', '{"drums": 3, "columns_per_drum": 16}'),
    (gen_random_uuid(), 2001, 'Постамат ПМ-50', 'АРТ-2001', 'Rev A', (SELECT uid FROM station_types WHERE name = 'Постамат'), (SELECT uid FROM station_manufacturers WHERE name = 'СтанкоПром'), 'Постамат для выдачи', '{"cells": 50}'),
    (gen_random_uuid(), 3001, 'Модуль ДМ-10', 'АРТ-3001', 'Rev A', (SELECT uid FROM station_types WHERE name = 'Дополнительный модуль'), (SELECT uid FROM station_manufacturers WHERE name = 'ИнструментСервис'), 'Дополнительный модуль', '{"cells": 10}')
ON CONFLICT (code) DO NOTHING;

INSERT INTO station_model_images (uid, model_uid, file_path, original_name, sort_order)
SELECT gen_random_uuid(), uid, uid::text || '_image1.png', 'СТ-100_front.png', 0 FROM station_models WHERE code = 1001;

INSERT INTO station_model_images (uid, model_uid, file_path, original_name, sort_order)
SELECT gen_random_uuid(), uid, uid::text || '_image1.png', 'СТ-200_front.png', 0 FROM station_models WHERE code = 1002;

INSERT INTO station_model_images (uid, model_uid, file_path, original_name, sort_order)
SELECT gen_random_uuid(), uid, uid::text || '_image1.png', 'ПМ-50_front.png', 0 FROM station_models WHERE code = 2001;

INSERT INTO station_model_documents (uid, model_uid, document_name, file_path, original_name)
SELECT gen_random_uuid(), uid, 'Руководство по эксплуатации', uid::text || '_manual.pdf', 'СТ-100_manual.pdf' FROM station_models WHERE code = 1001;

INSERT INTO station_model_documents (uid, model_uid, document_name, file_path, original_name)
SELECT gen_random_uuid(), uid, 'Сертификат соответствия', uid::text || '_cert.pdf', 'СТ-100_cert.pdf' FROM station_models WHERE code = 1001;

-- ============================================================
-- 33. СИДЫ: КОНФИГУРАЦИИ СТАНЦИЙ
-- ============================================================

INSERT INTO station_configurations (uid, name, model_id, cells_structure) VALUES
    (gen_random_uuid(), 'Конфигурация СТ-100 Стандарт', (SELECT uid FROM station_models WHERE code = 1001), '{"drums": 2, "columns_per_drum": 12, "total_cells": 24}'),
    (gen_random_uuid(), 'Конфигурация СТ-100 Расширенная', (SELECT uid FROM station_models WHERE code = 1001), '{"drums": 2, "columns_per_drum": 16, "total_cells": 32}'),
    (gen_random_uuid(), 'Конфигурация СТ-200 Стандарт', (SELECT uid FROM station_models WHERE code = 1002), '{"drums": 3, "columns_per_drum": 16, "total_cells": 48}'),
    (gen_random_uuid(), 'Конфигурация ПМ-50 Стандарт', (SELECT uid FROM station_models WHERE code = 2001), '{"cells": 50}')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 34. СИДЫ: ГРУППЫ УЧЕТА
-- ============================================================

INSERT INTO spr_type_material (uid, type_name) VALUES
    (gen_random_uuid(), 'ТМЦ'),
    (gen_random_uuid(), 'Готовая деталь')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 35. СИДЫ: ГРУППЫ НОМЕНКЛАТУРЫ
-- ============================================================

DO $$
DECLARE
    tmc_uid UUID;
    ready_uid UUID;
BEGIN
    SELECT uid INTO tmc_uid FROM spr_type_material WHERE type_name = 'ТМЦ';
    SELECT uid INTO ready_uid FROM spr_type_material WHERE type_name = 'Готовая деталь';

    INSERT INTO spr_type_purpose (uid, type_name, type_material_uid) VALUES
        (gen_random_uuid(), 'Металлообрабатывающий инструмент', tmc_uid),
        (gen_random_uuid(), 'Слесарный инструмент', tmc_uid),
        (gen_random_uuid(), 'Оснастка', tmc_uid),
        (gen_random_uuid(), 'Готовые детали', ready_uid)
    ON CONFLICT DO NOTHING;
END $$;

-- ============================================================
-- 36. СИДЫ: ВИДЫ НОМЕНКЛАТУРЫ
-- ============================================================

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

    INSERT INTO spr_type_product (uid, type_name, type_purpose_uid) VALUES
        (gen_random_uuid(), 'Сверло', metal_uid),
        (gen_random_uuid(), 'Фреза', metal_uid),
        (gen_random_uuid(), 'Резец', metal_uid),
        (gen_random_uuid(), 'Метчик', metal_uid),
        (gen_random_uuid(), 'Молоток', slesar_uid),
        (gen_random_uuid(), 'Отвертка', slesar_uid),
        (gen_random_uuid(), 'Ключ гаечный', slesar_uid),
        (gen_random_uuid(), 'Тиски', osnastka_uid),
        (gen_random_uuid(), 'Патрон', osnastka_uid),
        (gen_random_uuid(), 'Кондуктор', osnastka_uid),
        (gen_random_uuid(), 'Вал', ready_uid),
        (gen_random_uuid(), 'Втулка', ready_uid),
        (gen_random_uuid(), 'Корпус', ready_uid),
        (gen_random_uuid(), 'Крышка', ready_uid)
    ON CONFLICT DO NOTHING;
END $$;

-- ============================================================
-- 37. СИДЫ: ВИДЫ ХАРАКТЕРИСТИК
-- ============================================================

DO $$
DECLARE
    geom_uid UUID;
    phys_uid UUID;
    expl_uid UUID;
BEGIN
    SELECT uid INTO geom_uid FROM spr_attribute_group WHERE name = 'Геометрические';
    SELECT uid INTO phys_uid FROM spr_attribute_group WHERE name = 'Физические';
    SELECT uid INTO expl_uid FROM spr_attribute_group WHERE name = 'Эксплуатационные';

    INSERT INTO spr_type_attributes (uid, name, designation, group_uid) VALUES
        (gen_random_uuid(), 'Длина', 'L', geom_uid),
        (gen_random_uuid(), 'Ширина', 'W', geom_uid),
        (gen_random_uuid(), 'Глубина', 'D', geom_uid),
        (gen_random_uuid(), 'Высота', 'H', geom_uid),
        (gen_random_uuid(), 'Масса', 'M', phys_uid),
        (gen_random_uuid(), 'Срок эксплуатации', 'T', expl_uid),
        (gen_random_uuid(), 'Стандарт исполнения', 'ГОСТ/DIN', expl_uid),
        (gen_random_uuid(), 'Покрытие', 'Покр.', expl_uid),
        (gen_random_uuid(), 'Тип хвостовика', 'Хвост.', expl_uid),
        (gen_random_uuid(), 'Глубина сверления', 'Глуб.', expl_uid),
        (gen_random_uuid(), 'Угол заточки', 'Уг.зат.', geom_uid),
        (gen_random_uuid(), 'Тип охлаждения', 'Охл.', expl_uid),
        (gen_random_uuid(), 'Материал инструмента', 'Матер.', phys_uid),
        (gen_random_uuid(), 'Назначение', 'Назн.', expl_uid),
        (gen_random_uuid(), 'Группа обрабатываемых материалов', 'Гр.обр.', expl_uid),
        (gen_random_uuid(), 'Особенность инструмента', 'Особ.', expl_uid)
    ON CONFLICT DO NOTHING;
END $$;

-- ============================================================
-- 38. СИДЫ: ГРУППА МАТЕРИАЛОВ
-- ============================================================

INSERT INTO reg_group_material (uid, group_name, parent_group, group_code) VALUES
    (gen_random_uuid(), 'Номенклатура', NULL, 0)
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    root_uid UUID;
BEGIN
    SELECT uid INTO root_uid FROM reg_group_material WHERE group_code = 0;
    
    INSERT INTO reg_group_material (uid, group_name, parent_group, group_code) VALUES
        (gen_random_uuid(), 'Сверла твердосплавные', root_uid, 1),
        (gen_random_uuid(), 'Фрезы', root_uid, 2),
        (gen_random_uuid(), 'Резцы', root_uid, 3),
        (gen_random_uuid(), 'Метчики', root_uid, 4),
        (gen_random_uuid(), 'Слесарный инструмент', root_uid, 5),
        (gen_random_uuid(), 'Оснастка', root_uid, 6),
        (gen_random_uuid(), 'Готовые детали', root_uid, 7)
    ON CONFLICT DO NOTHING;
END $$;

-- ============================================================
-- 39. СИДЫ: ПРОИЗВОДИТЕЛИ НОМЕНКЛАТУРЫ
-- ============================================================

INSERT INTO spr_manufacturer (uid, name, description, code, country_uid, direction_uid, address, email, website, phone) VALUES
    (gen_random_uuid(), 'New Century', 'New Century Drill', 5001, (SELECT uid FROM spr_country WHERE name = 'Китай'), (SELECT uid FROM spr_production_direction WHERE name = 'Металлообработка'), 'Китай, г. Шанхай', 'info@newcentury.cn', 'www.newcentury.cn', '+86 21 5555 6666'),
    (gen_random_uuid(), 'ООО "СтанкоДеталь"', 'Производство оснастки', 5002, (SELECT uid FROM spr_country WHERE name = 'Россия'), (SELECT uid FROM spr_production_direction WHERE name = 'Производство оснастки'), '125212, г. Москва', 'info@stankodetal.ru', 'www.stankodetal.ru', '+7 (495) 111-22-33'),
    (gen_random_uuid(), 'АО "ПромТех"', 'Промышленное оборудование', 5003, (SELECT uid FROM spr_country WHERE name = 'Россия'), (SELECT uid FROM spr_production_direction WHERE name = 'Электрооборудование'), '620014, г. Екатеринбург', 'info@promtex.ru', 'www.promtex.ru', '+7 (343) 222-33-44'),
    (gen_random_uuid(), 'ИП Иванов', 'Метизы', 5004, (SELECT uid FROM spr_country WHERE name = 'Беларусь'), (SELECT uid FROM spr_production_direction WHERE name = 'Метизы'), '220030, г. Минск', 'ivanov@metiz.by', 'www.ivanov-metiz.by', '+375 (17) 333-44-55'),
    (gen_random_uuid(), 'ООО "СмазТех"', 'Смазочные материалы', 5005, (SELECT uid FROM spr_country WHERE name = 'Россия'), (SELECT uid FROM spr_production_direction WHERE name = 'Смазочные материалы'), '105264, г. Москва', 'info@smaztech.ru', 'www.smaztech.ru', '+7 (495) 444-55-66'),
    (gen_random_uuid(), 'АО "ЭлектроПром"', 'Электрооборудование', 5006, (SELECT uid FROM spr_country WHERE name = 'Россия'), (SELECT uid FROM spr_production_direction WHERE name = 'Электрооборудование'), '603000, г. Нижний Новгород', 'info@electroprom.ru', 'www.electroprom.ru', '+7 (831) 555-66-77')
ON CONFLICT DO NOTHING;

INSERT INTO spr_manufacturer_images (uid, manufacturer_uid, file_path, original_name, sort_order)
SELECT gen_random_uuid(), uid, uid::text || '_logo.png', name || '_logo.png', 0 FROM spr_manufacturer WHERE code IN (5001, 5002, 5003);

-- ============================================================
-- 40. СИДЫ: БРЕНДЫ
-- ============================================================

DO $$
DECLARE
    nc_uid UUID;
    stanko_uid UUID;
    promtex_uid UUID;
    electroprom_uid UUID;
BEGIN
    SELECT uid INTO nc_uid FROM spr_manufacturer WHERE name = 'New Century';
    SELECT uid INTO stanko_uid FROM spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"';
    SELECT uid INTO promtex_uid FROM spr_manufacturer WHERE name = 'АО "ПромТех"';
    SELECT uid INTO electroprom_uid FROM spr_manufacturer WHERE name = 'АО "ЭлектроПром"';

    INSERT INTO spr_brand (uid, name, description, manufacturer_uid) VALUES
        (gen_random_uuid(), 'New Century', 'New Century Drill', nc_uid),
        (gen_random_uuid(), 'SKF', 'Подшипники SKF', stanko_uid),
        (gen_random_uuid(), 'FAG', 'Подшипники FAG', stanko_uid),
        (gen_random_uuid(), 'Gates', 'Ремни Gates', promtex_uid),
        (gen_random_uuid(), 'Mobil', 'Смазки Mobil', promtex_uid),
        (gen_random_uuid(), 'Shell', 'Масла Shell', electroprom_uid)
    ON CONFLICT DO NOTHING;
END $$;

-- ============================================================
-- 41. СИДЫ: МОДЕЛИ БРЕНДОВ
-- ============================================================

DO $$
DECLARE
    nc_brand_uid UUID;
    skf_uid UUID;
    fag_uid UUID;
    gates_uid UUID;
    mobil_uid UUID;
    nc_manufacturer_uid UUID;
BEGIN
    SELECT uid INTO nc_brand_uid FROM spr_brand WHERE name = 'New Century';
    SELECT uid INTO skf_uid FROM spr_brand WHERE name = 'SKF';
    SELECT uid INTO fag_uid FROM spr_brand WHERE name = 'FAG';
    SELECT uid INTO gates_uid FROM spr_brand WHERE name = 'Gates';
    SELECT uid INTO mobil_uid FROM spr_brand WHERE name = 'Mobil';
    SELECT uid INTO nc_manufacturer_uid FROM spr_manufacturer WHERE name = 'New Century';

    INSERT INTO spr_model_of_brand (uid, name, description, brand, manufacturer_uid) VALUES
        (gen_random_uuid(), 'DH224', 'Серия DH224 5XD', nc_brand_uid, nc_manufacturer_uid),
        (gen_random_uuid(), '6204-2RS', 'Шариковый радиальный', skf_uid, (SELECT uid FROM spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"')),
        (gen_random_uuid(), '6205-C3', 'Шариковый радиальный', fag_uid, (SELECT uid FROM spr_manufacturer WHERE name = 'ООО "СтанкоДеталь"')),
        (gen_random_uuid(), 'A-1000', 'Ремень клиновой', gates_uid, (SELECT uid FROM spr_manufacturer WHERE name = 'АО "ПромТех"')),
        (gen_random_uuid(), 'Mobilux EP2', 'Смазка литиевая', mobil_uid, (SELECT uid FROM spr_manufacturer WHERE name = 'АО "ПромТех"'))
    ON CONFLICT DO NOTHING;
END $$;

-- ============================================================
-- 42. СИДЫ: ТИПЫ ОПИСАНИЙ ПОСТАВЩИКОВ
-- ============================================================

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

-- ============================================================
-- 43. СИДЫ: ПОСТАВЩИКИ
-- ============================================================

DO $$
DECLARE
    v_country_rus UUID;
    v_country_chn UUID;
    v_country_blr UUID;
    
    v_desc_producer UUID;
    v_desc_distributor UUID;
    v_desc_wholesale UUID;
    v_desc_dealer UUID;
    v_desc_importer UUID;
    
    v_code INTEGER;
    v_supplier_uid UUID;
    v_logo_uid UUID;
BEGIN
    SELECT uid INTO v_country_rus FROM spr_country WHERE name = 'Россия';
    SELECT uid INTO v_country_chn FROM spr_country WHERE name = 'Китай';
    SELECT uid INTO v_country_blr FROM spr_country WHERE name = 'Беларусь';
    
    SELECT uid INTO v_desc_producer FROM spr_supplier_description_types WHERE name = 'Производитель';
    SELECT uid INTO v_desc_distributor FROM spr_supplier_description_types WHERE name = 'Официальный дистрибьютор';
    SELECT uid INTO v_desc_wholesale FROM spr_supplier_description_types WHERE name = 'Оптовый поставщик';
    SELECT uid INTO v_desc_dealer FROM spr_supplier_description_types WHERE name = 'Дилер';
    SELECT uid INTO v_desc_importer FROM spr_supplier_description_types WHERE name = 'Импортер';
    
    SELECT COALESCE(MAX(code), 0) INTO v_code FROM spr_suppliers;
    
    -- Поставщик 1
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (uid, code, name, country_uid, address, short_description_uid, description, email, website, phone, inn, ogrn, kpp, contact_person, contact_position, contact_phone, director, director_position, bank_name, bik, correspondent_account, settlement_account)
    VALUES (v_supplier_uid, v_code, 'ООО "ПромСнаб"', v_country_rus, '125212, г. Москва, ул. Адмирала Макарова, д. 10, стр. 1, офис 45', v_desc_producer, 'Ведущий российский производитель и поставщик промышленного оборудования.', 'info@promsnab.ru', 'www.promsnab.ru', '+7 (495) 123-45-67', '7712345678', '1027700123456', '771201001', 'Петров Сергей Владимирович', 'Руководитель отдела продаж', '+7 (495) 123-45-68', 'Кузнецов Алексей Николаевич', 'Генеральный директор', 'ПАО "Сбербанк"', '044525225', '30101810400000000225', '40702810900000000123');
    v_logo_uid := gen_random_uuid();
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at) VALUES (v_logo_uid, v_supplier_uid, v_logo_uid::text || '.svg', 'PromSnab_logo.svg', 0, NOW());
    
    -- Поставщик 2
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (uid, code, name, country_uid, address, short_description_uid, description, email, website, phone, inn, ogrn, kpp, contact_person, contact_position, contact_phone, director, director_position, bank_name, bik, correspondent_account, settlement_account)
    VALUES (v_supplier_uid, v_code, 'АО "ТехКомплект"', v_country_rus, '620014, г. Екатеринбург, ул. Малышева, д. 51, офис 302', v_desc_distributor, 'Официальный дистрибьютор ведущих мировых производителей металлорежущего инструмента.', 'sales@techkomplekt.ru', 'www.techkomplekt.ru', '+7 (343) 234-56-78', '6671234567', '1036600123456', '667101001', 'Смирнова Елена Александровна', 'Ведущий менеджер', '+7 (343) 234-56-79', 'Морозов Дмитрий Игоревич', 'Генеральный директор', 'АО "Альфа-Банк"', '044525593', '30101810200000000593', '40702810300000000456');
    v_logo_uid := gen_random_uuid();
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at) VALUES (v_logo_uid, v_supplier_uid, v_logo_uid::text || '.svg', 'TechKomplekt_logo.svg', 0, NOW());
    
    -- Поставщик 3
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (uid, code, name, country_uid, address, short_description_uid, description, email, website, phone, inn, ogrn, kpp, contact_person, contact_position, contact_phone, director, director_position, bank_name, bik, correspondent_account, settlement_account)
    VALUES (v_supplier_uid, v_code, 'ИП Иванов А.А.', v_country_blr, '220030, Республика Беларусь, г. Минск, ул. Интернациональная, д. 15', v_desc_wholesale, 'Индивидуальный предприниматель. Специализация: метизы, крепежные изделия.', 'ivanov@metiz.by', 'www.ivanov-metiz.by', '+375 (17) 345-67-89', '192345678', '304192345600012', '—', 'Иванов Александр Александрович', 'Собственник', '+375 (29) 111-22-33', 'Иванов Александр Александрович', 'Индивидуальный предприниматель', 'ОАО "АСБ Беларусбанк"', '153001795', '30101810200000000795', '40702810900000000789');
    v_logo_uid := gen_random_uuid();
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at) VALUES (v_logo_uid, v_supplier_uid, v_logo_uid::text || '.svg', 'Ivanov_logo.svg', 0, NOW());
    
    -- Поставщик 4
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (uid, code, name, country_uid, address, short_description_uid, description, email, website, phone, inn, ogrn, kpp, contact_person, contact_position, contact_phone, director, director_position, bank_name, bik, correspondent_account, settlement_account)
    VALUES (v_supplier_uid, v_code, 'ООО "МетизТорг"', v_country_rus, '603000, г. Нижний Новгород, ул. Белинского, д. 32, пом. 12', v_desc_dealer, 'Дилерская сеть по продаже металлоизделий и крепежа.', 'info@metiztorg.ru', 'www.metiztorg.ru', '+7 (831) 456-78-90', '5261234567', '1035200123456', '526101001', 'Козлов Павел Сергеевич', 'Менеджер по работе с клиентами', '+7 (831) 456-78-91', 'Новикова Ольга Владимировна', 'Исполнительный директор', 'ПАО "ВТБ"', '044525187', '30101810200000000187', '40702810400000001012');
    v_logo_uid := gen_random_uuid();
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at) VALUES (v_logo_uid, v_supplier_uid, v_logo_uid::text || '.svg', 'MetizTorg_logo.svg', 0, NOW());
    
    -- Поставщик 5
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (uid, code, name, country_uid, address, short_description_uid, description, email, website, phone, inn, ogrn, kpp, contact_person, contact_position, contact_phone, director, director_position, bank_name, bik, correspondent_account, settlement_account)
    VALUES (v_supplier_uid, v_code, 'ЗАО "ИнструментСервис"', v_country_chn, '430000, Китай, г. Шанхай, Pudong New Area', v_desc_importer, 'Прямой импортер высокоточного режущего инструмента из Китая.', 'order@instrumentservice.pro', 'www.instrumentservice.pro', '+86 (21) 1234-5678', '9901234567', '1039900123456', '990101001', 'Чжан Вэй', 'Руководитель отдела ВЭД', '+86 (21) 1234-5679', 'Ли Цзянь', 'Генеральный директор', 'Bank of China, Shanghai Branch', 'BKCHCNBJ300', '30101810200000000300', '40702810900000001314');
    v_logo_uid := gen_random_uuid();
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at) VALUES (v_logo_uid, v_supplier_uid, v_logo_uid::text || '.svg', 'InstrumentService_logo.svg', 0, NOW());
    
    -- Поставщик 6
    v_code := v_code + 1;
    v_supplier_uid := gen_random_uuid();
    INSERT INTO spr_suppliers (uid, code, name, country_uid, address, short_description_uid, description, email, website, phone, inn, ogrn, kpp, contact_person, contact_position, contact_phone, director, director_position, bank_name, bik, correspondent_account, settlement_account)
    VALUES (v_supplier_uid, v_code, 'ООО "ЗАДЕЛ"', v_country_rus, '105264, г. Москва, ул. Верхняя Первомайская, д. 47, стр. 3', v_desc_producer, 'Российский производитель технологической оснастки и заделов.', 'info@zadel.pro', 'www.zadel.pro', '+7 (495) 987-65-43', '7719876543', '1027700987654', '771901001', 'Григорьев Андрей Павлович', 'Начальник отдела сбыта', '+7 (495) 987-65-44', 'Соколов Михаил Леонидович', 'Генеральный директор', 'ПАО "Сбербанк"', '044525225', '30101810400000000225', '40702810900000005678');
    v_logo_uid := gen_random_uuid();
    INSERT INTO spr_supplier_images (uid, supplier_uid, file_path, original_name, sort_order, created_at) VALUES (v_logo_uid, v_supplier_uid, v_logo_uid::text || '.svg', 'ZADEL_logo.svg', 0, NOW());
    
END $$;

-- ============================================================
-- 44. СИДЫ: БРЕНДЫ ПОСТАВЩИКОВ
-- ============================================================

DO $$
DECLARE
    v_promsnab_uid UUID;
    v_techkomplekt_uid UUID;
    v_instrumentservice_uid UUID;
    v_zadel_uid UUID;
BEGIN
    SELECT uid INTO v_promsnab_uid FROM spr_suppliers WHERE name = 'ООО "ПромСнаб"';
    SELECT uid INTO v_techkomplekt_uid FROM spr_suppliers WHERE name = 'АО "ТехКомплект"';
    SELECT uid INTO v_instrumentservice_uid FROM spr_suppliers WHERE name = 'ЗАО "ИнструментСервис"';
    SELECT uid INTO v_zadel_uid FROM spr_suppliers WHERE name = 'ООО "ЗАДЕЛ"';

    INSERT INTO spr_supplier_brand (uid, name, supplier_uid) VALUES
        (gen_random_uuid(), 'PromSnab', v_promsnab_uid),
        (gen_random_uuid(), 'TechKomplekt', v_techkomplekt_uid),
        (gen_random_uuid(), 'New Century', v_instrumentservice_uid),
        (gen_random_uuid(), 'ZADEL', v_zadel_uid)
    ON CONFLICT (name, supplier_uid) DO NOTHING;
END $$;

-- ============================================================
-- 45. СИДЫ: НОМЕНКЛАТУРА (10 СВЁРЛ)
-- ============================================================

DO $$
DECLARE
    v_tmc_uid UUID;
    v_purpose_uid UUID;
    v_product_uid UUID;
    v_measure_sht_uid UUID;
    v_measure_mm_uid UUID;
    v_measure_deg_uid UUID;
    v_manufacturer_uid UUID;
    v_brand_uid UUID;
    v_model_uid UUID;
    v_country_uid UUID;
    v_group_uid UUID;
    
    v_attr_length_uid UUID;
    v_attr_width_uid UUID;
    v_attr_height_uid UUID;
    v_attr_mass_uid UUID;
    v_attr_standard_uid UUID;
    v_attr_coating_uid UUID;
    v_attr_shank_uid UUID;
    v_attr_depth_uid UUID;
    v_attr_angle_uid UUID;
    v_attr_cooling_uid UUID;
    v_attr_material_uid UUID;
    v_attr_purpose_uid UUID;
    v_attr_material_group_uid UUID;
    v_attr_feature_uid UUID;
    
    v_material_uid UUID;
    v_photo_uid UUID;
    v_blueprint_uid UUID;
    v_qr_uid UUID;
    v_code INTEGER;
    
    v_data TEXT[][] := ARRAY[
        ARRAY['1', '8', 'DH2240100', 'Сверло твердосплавное 5XD с покрытием TiАIN 1X3X8X55'],
        ARRAY['1.1', '12', 'DH2240110', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.1X3X12X55'],
        ARRAY['1.2', '12', 'DH2240120', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.2X3X12X55'],
        ARRAY['1.3', '12', 'DH2240130', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.3X3X12X55'],
        ARRAY['1.4', '12', 'DH2240140', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.4X3X12X55'],
        ARRAY['1.5', '16', 'DH2240150', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.5X3X16X55'],
        ARRAY['1.6', '16', 'DH2240160', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.6X3X16X55'],
        ARRAY['1.7', '16', 'DH2240170', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.7X3X16X55'],
        ARRAY['1.8', '16', 'DH2240180', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.8X3X16X55'],
        ARRAY['1.83', '16', 'DH2240183', 'Сверло твердосплавное 5XD с покрытием TiАIN 1.83X3X16X55']
    ];
    
    v_description TEXT := 'Спиральное сверло из твердого сплава с правым вращением и цилиндрическим хвостовиком без каналов для подачи охлаждающей жидкости.';
    
    v_idx INTEGER;
    v_diameter TEXT;
    v_article TEXT;
    v_name TEXT;
BEGIN
    SELECT uid INTO v_tmc_uid FROM spr_type_material WHERE type_name = 'ТМЦ';
    SELECT uid INTO v_purpose_uid FROM spr_type_purpose WHERE type_name = 'Металлообрабатывающий инструмент';
    SELECT uid INTO v_product_uid FROM spr_type_product WHERE type_name = 'Сверло';
    SELECT uid INTO v_measure_sht_uid FROM spr_measure WHERE name = 'шт';
    SELECT uid INTO v_measure_mm_uid FROM spr_measure WHERE name = 'мм';
    SELECT uid INTO v_measure_deg_uid FROM spr_measure WHERE name = 'град';
    SELECT uid INTO v_manufacturer_uid FROM spr_manufacturer WHERE name = 'New Century';
    SELECT uid INTO v_brand_uid FROM spr_brand WHERE name = 'New Century';
    SELECT uid INTO v_model_uid FROM spr_model_of_brand WHERE name = 'DH224';
    SELECT uid INTO v_country_uid FROM spr_country WHERE name = 'Китай';
    SELECT uid INTO v_group_uid FROM reg_group_material WHERE group_name = 'Сверла твердосплавные';
    
    SELECT uid INTO v_attr_length_uid FROM spr_type_attributes WHERE name = 'Длина';
    SELECT uid INTO v_attr_width_uid FROM spr_type_attributes WHERE name = 'Ширина';
    SELECT uid INTO v_attr_height_uid FROM spr_type_attributes WHERE name = 'Высота';
    SELECT uid INTO v_attr_mass_uid FROM spr_type_attributes WHERE name = 'Масса';
    SELECT uid INTO v_attr_standard_uid FROM spr_type_attributes WHERE name = 'Стандарт исполнения';
    SELECT uid INTO v_attr_coating_uid FROM spr_type_attributes WHERE name = 'Покрытие';
    SELECT uid INTO v_attr_shank_uid FROM spr_type_attributes WHERE name = 'Тип хвостовика';
    SELECT uid INTO v_attr_depth_uid FROM spr_type_attributes WHERE name = 'Глубина сверления';
    SELECT uid INTO v_attr_angle_uid FROM spr_type_attributes WHERE name = 'Угол заточки';
    SELECT uid INTO v_attr_cooling_uid FROM spr_type_attributes WHERE name = 'Тип охлаждения';
    SELECT uid INTO v_attr_material_uid FROM spr_type_attributes WHERE name = 'Материал инструмента';
    SELECT uid INTO v_attr_purpose_uid FROM spr_type_attributes WHERE name = 'Назначение';
    SELECT uid INTO v_attr_material_group_uid FROM spr_type_attributes WHERE name = 'Группа обрабатываемых материалов';
    SELECT uid INTO v_attr_feature_uid FROM spr_type_attributes WHERE name = 'Особенность инструмента';
    
    SELECT COALESCE(MAX(code_material), 0) INTO v_code FROM spr_material;
    
    FOR v_idx IN 1..10 LOOP
        v_diameter := v_data[v_idx][1];
        v_article := v_data[v_idx][3];
        v_name := v_data[v_idx][4];
        
        v_code := v_code + 1;
        v_material_uid := gen_random_uuid();
        
        INSERT INTO spr_material (uid, code_material, name_material, article, description, group_material, type_main, type_purpose, type_product, manufacturer, brand, model_of_brand, country, measure, usage, resharpen, waste_material, recycle_material, syncronized_mother_system, syncronized_supplier)
        VALUES (v_material_uid, v_code, v_name, v_article, v_description, v_group_uid, v_tmc_uid, v_purpose_uid, v_product_uid, v_manufacturer_uid, v_brand_uid, v_model_uid, v_country_uid, v_measure_sht_uid, true, false, false, false, true, true);
        
        INSERT INTO reg_attributes (uid, name, meaning, measure_uid, material_uid) VALUES
            (gen_random_uuid(), v_attr_length_uid, '55', v_measure_mm_uid, v_material_uid),
            (gen_random_uuid(), v_attr_width_uid, v_diameter, v_measure_mm_uid, v_material_uid),
            (gen_random_uuid(), v_attr_height_uid, '3', v_measure_mm_uid, v_material_uid),
            (gen_random_uuid(), v_attr_mass_uid, '0', v_measure_mm_uid, v_material_uid),
            (gen_random_uuid(), v_attr_standard_uid, 'DIN6539', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_coating_uid, 'TiAlN', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_shank_uid, 'HA-Цилиндр', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_depth_uid, '5xD', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_angle_uid, '140', v_measure_deg_uid, v_material_uid),
            (gen_random_uuid(), v_attr_cooling_uid, 'Внешнее', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_material_uid, 'HM-Твердый сплав', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_purpose_uid, 'Универсальные', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_material_group_uid, 'P-стали; M-нержавеющие стали; K-чугун; H-Твердые закаленные материалы', NULL, v_material_uid),
            (gen_random_uuid(), v_attr_feature_uid, 'Удлиненное (от 5 до 10хD)', NULL, v_material_uid);
        
        v_photo_uid := gen_random_uuid();
        INSERT INTO spr_material_images (uid, material_uid, file_path, original_name, sort_order, created_at) VALUES (v_photo_uid, v_material_uid, v_photo_uid::text || '.png', v_article || '_photo.png', 0, NOW());
        
        v_blueprint_uid := gen_random_uuid();
        INSERT INTO spr_material_blueprints (uid, material_uid, file_path, original_name, created_at) VALUES (v_blueprint_uid, v_material_uid, v_blueprint_uid::text || '.png', v_article || '_blueprint.png', NOW());
        
        v_qr_uid := gen_random_uuid();
        INSERT INTO spr_material_codes (uid, material_uid, file_path, original_name, code_type, code_value, code_kind, created_at) VALUES (v_qr_uid, v_material_uid, v_qr_uid::text || '.png', v_article || '_qr.png', 'QR_CODE', v_article, 'QR', NOW());
        
    END LOOP;
    
END $$;

-- ============================================================
-- 46. СИДЫ: ЦЕНЫ И ПОСТАВКИ
-- ============================================================

DO $$
DECLARE
    v_material_uid UUID;
    v_supplier_uid UUID;
    v_doc_entrance_uid UUID;
    v_price_uid UUID;
    v_reg_supplier_uid UUID;
    v_idx INTEGER;
    v_price_value DOUBLE PRECISION;
BEGIN
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'ЗАО "ИнструментСервис"';
    
    FOR v_idx IN 1..10 LOOP
        SELECT uid INTO v_material_uid FROM spr_material WHERE code_material = v_idx LIMIT 1;
        
        IF v_material_uid IS NOT NULL THEN
            v_doc_entrance_uid := gen_random_uuid();
            v_price_value := 1500 + (v_idx * 100);
            
            INSERT INTO doc_entrance (uid, price, supplier, entrance_date) VALUES (v_doc_entrance_uid, v_price_value, v_supplier_uid, NOW() - INTERVAL '30 days');
            
            v_price_uid := gen_random_uuid();
            INSERT INTO reg_price (uid, price, price_date, link, doc_entrance_uid) VALUES (v_price_uid, v_price_value, NOW() - INTERVAL '30 days', v_material_uid, v_doc_entrance_uid);
            
            v_reg_supplier_uid := gen_random_uuid();
            INSERT INTO reg_suppliers (uid, material_uid, supplier_uid, supply_date, document_name, file_path, original_name) VALUES (v_reg_supplier_uid, v_material_uid, v_supplier_uid, NOW() - INTERVAL '30 days', 'Накладная ' || v_idx, v_reg_supplier_uid::text || '.pdf', 'Накладная_' || v_idx || '.pdf');
            
            UPDATE spr_material SET price = v_price_uid, suppliers = v_reg_supplier_uid WHERE uid = v_material_uid;
        END IF;
    END LOOP;
END $$;

-- ============================================================
-- 47. СИДЫ: КАТЕГОРИИ ШАБЛОНОВ
-- ============================================================

INSERT INTO template_categories (name) VALUES
    ('Инструментальные'),
    ('Универсальные'),
    ('Специальные'),
    ('Тестовые')
ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 48. СИДЫ: ШАБЛОНЫ ПОПОЛНЕНИЯ
-- ============================================================

DO $$
DECLARE
    v_category_inst_id BIGINT;
    v_config_st100_uid UUID;
    v_config_st200_uid UUID;
    v_template_uid UUID;
    v_counter BIGINT;
    v_cell_uid UUID;
    v_material_uid UUID;
    v_cell_idx INTEGER;
BEGIN
    SELECT id INTO v_category_inst_id FROM template_categories WHERE name = 'Инструментальные';
    SELECT uid INTO v_config_st100_uid FROM station_configurations WHERE name = 'Конфигурация СТ-100 Стандарт';
    SELECT uid INTO v_config_st200_uid FROM station_configurations WHERE name = 'Конфигурация СТ-200 Стандарт';
    
    SELECT COALESCE(MAX(number), 0) INTO v_counter FROM doc_pattern;
    
    v_counter := v_counter + 1;
    v_template_uid := gen_random_uuid();
    INSERT INTO doc_pattern (uid, name_pattern, number, category_id, configuration_uid, total_cells, filled_cells, free_cells) VALUES (v_template_uid, 'Шаблон СТ-100 №1', v_counter, v_category_inst_id, v_config_st100_uid, 24, 10, 14);
    
    FOR v_cell_idx IN 1..10 LOOP
        SELECT uid INTO v_material_uid FROM spr_material WHERE code_material = v_cell_idx LIMIT 1;
        
        IF v_material_uid IS NOT NULL THEN
            v_cell_uid := gen_random_uuid();
            INSERT INTO reg_cells (uid, doc_pattern_uid, number_cell, column_number, drum_number, name_material, quantity, type_main, purpose_material, max_quantity)
            VALUES (v_cell_uid, v_template_uid, v_cell_idx, CASE WHEN v_cell_idx <= 8 THEN v_cell_idx ELSE v_cell_idx - 8 END, CASE WHEN v_cell_idx <= 8 THEN 1 ELSE 2 END, v_material_uid, 5 + v_cell_idx, (SELECT type_main FROM spr_material WHERE uid = v_material_uid), 'Основное назначение', 20 + v_cell_idx);
        END IF;
    END LOOP;
    
    v_counter := v_counter + 1;
    v_template_uid := gen_random_uuid();
    INSERT INTO doc_pattern (uid, name_pattern, number, category_id, configuration_uid, total_cells, filled_cells, free_cells) VALUES (v_template_uid, 'Шаблон СТ-200 №1', v_counter, v_category_inst_id, v_config_st200_uid, 48, 10, 38);
    
    FOR v_cell_idx IN 1..10 LOOP
        SELECT uid INTO v_material_uid FROM spr_material WHERE code_material = v_cell_idx LIMIT 1;
        
        IF v_material_uid IS NOT NULL THEN
            v_cell_uid := gen_random_uuid();
            INSERT INTO reg_cells (uid, doc_pattern_uid, number_cell, column_number, drum_number, name_material, quantity, type_main, purpose_material, max_quantity)
            VALUES (v_cell_uid, v_template_uid, v_cell_idx, CASE WHEN v_cell_idx <= 8 THEN v_cell_idx ELSE v_cell_idx - 8 END, CASE WHEN v_cell_idx <= 8 THEN 1 ELSE 2 END, v_material_uid, 8 + v_cell_idx, (SELECT type_main FROM spr_material WHERE uid = v_material_uid), 'Универсальное назначение', 30 + v_cell_idx);
        END IF;
    END LOOP;
    
END $$;

-- ============================================================
-- 49. СИДЫ: СТАНЦИИ
-- ============================================================

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
    v_model_1001_uid UUID;
    v_model_1002_uid UUID;
    v_model_2001_uid UUID;
    v_model_3001_uid UUID;
    v_config_st100_uid UUID;
    v_config_st200_uid UUID;
    v_template1_uid UUID;
    v_template2_uid UUID;
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

    SELECT uid INTO v_model_1001_uid FROM station_models WHERE code = 1001;
    SELECT uid INTO v_model_1002_uid FROM station_models WHERE code = 1002;
    SELECT uid INTO v_model_2001_uid FROM station_models WHERE code = 2001;
    SELECT uid INTO v_model_3001_uid FROM station_models WHERE code = 3001;
    
    SELECT uid INTO v_config_st100_uid FROM station_configurations WHERE name = 'Конфигурация СТ-100 Стандарт';
    SELECT uid INTO v_config_st200_uid FROM station_configurations WHERE name = 'Конфигурация СТ-200 Стандарт';
    
    SELECT uid INTO v_template1_uid FROM doc_pattern WHERE name_pattern = 'Шаблон СТ-100 №1';
    SELECT uid INTO v_template2_uid FROM doc_pattern WHERE name_pattern = 'Шаблон СТ-200 №1';

    SELECT COALESCE(MAX(code), 0) INTO v_counter FROM stations;

    INSERT INTO stations (uid, name, code, description, production_date, serial_number, model_id, configuration_uid, holding_id, enterprise_id, workshop_id, section_id, status, total_cells, filled_cells, template_nomenclature_count, remaining_nomenclature_count, max_ready_parts, ready_parts_count, parent_uid, has_error, is_tmc, is_sgd, is_ok, is_additional_module, has_additional_module, ip_address, network_port, active_template_uid)
    VALUES
        ('ST-001', 'Инструментальная станция №1', v_counter + 1, 'Основная инструментальная станция', DATE '2023-06-15', 'SN-001-2023', v_model_1001_uid, v_config_st100_uid, hold_north_id, ent1_id, ws1_id, secA_id, 'WORKING', 24, 18, 15, 9, 100, 67, NULL, false, true, false, true, false, false, '192.168.1.101', 8080, v_template1_uid),
        ('ST-002', 'Инструментальная станция №2', v_counter + 2, 'Вторая инструментальная станция', DATE '2023-08-20', 'SN-002-2023', v_model_1001_uid, v_config_st100_uid, hold_north_id, ent1_id, ws1_id, secB_id, 'WORKING', 32, 32, 20, 12, 150, 89, NULL, false, true, false, true, false, false, '192.168.1.102', 8080, NULL),
        ('ST-003', 'Универсальная станция №1', v_counter + 3, 'Универсальная станция для ТМЦ и СГД', DATE '2024-01-10', 'SN-003-2024', v_model_1002_uid, v_config_st200_uid, hold_north_id, ent2_id, ws3_id, secE_id, 'MINIMAL_STOCK', 48, 40, 35, 5, 200, 180, NULL, false, true, true, true, false, false, '192.168.2.101', 8081, v_template2_uid),
        ('ST-004', 'Универсальная станция №2', v_counter + 4, 'Универсальная станция', DATE '2024-03-05', 'SN-004-2024', v_model_1002_uid, v_config_st200_uid, hold_north_id, ent2_id, ws4_id, secF_id, 'CRITICAL_STOCK', 40, 38, 30, 2, 180, 175, NULL, true, true, false, false, false, false, '192.168.2.102', 8081, NULL),
        ('ST-005', 'Дополнительный модуль 1', v_counter + 5, 'Дополнительный модуль для СТ-001', DATE '2024-06-01', 'SN-005-2024', v_model_3001_uid, NULL, hold_north_id, ent1_id, ws1_id, secA_id, 'OFFLINE', 12, 0, 0, 0, 0, 0, 'ST-001', true, false, false, false, true, false, NULL, NULL, NULL),
        ('ST-006', 'Дополнительный модуль 2', v_counter + 6, 'Дополнительный модуль для СТ-001', DATE '2024-06-15', 'SN-006-2024', v_model_3001_uid, NULL, hold_north_id, ent1_id, ws1_id, secA_id, 'WORKING', 8, 6, 5, 3, 40, 25, 'ST-001', false, false, false, false, true, false, '192.168.1.103', 8082, NULL),
        ('ST-007', 'Универсальная станция №3', v_counter + 7, 'Универсальная станция', DATE '2024-08-12', 'SN-007-2024', v_model_1002_uid, v_config_st200_uid, hold_south_id, ent3_id, ws5_id, secG_id, 'WORKING', 56, 42, 40, 25, 250, 210, NULL, false, true, true, true, false, false, '192.168.3.101', 8080, NULL),
        ('ST-008', 'Инструментальная станция №3', v_counter + 8, 'Инструментальная станция', DATE '2024-09-20', 'SN-008-2024', v_model_1001_uid, v_config_st100_uid, hold_south_id, ent3_id, ws5_id, secG_id, 'WORKING', 28, 28, 22, 15, 120, 95, NULL, false, true, false, false, false, false, '192.168.3.102', 8080, NULL),
        ('ST-009', 'Дополнительный модуль 3', v_counter + 9, 'Дополнительный модуль для СТ-003', DATE '2024-10-01', 'SN-009-2024', v_model_3001_uid, NULL, hold_north_id, ent2_id, ws3_id, secE_id, 'MINIMAL_STOCK', 6, 6, 5, 1, 30, 28, 'ST-003', false, false, false, false, true, false, NULL, NULL, NULL),
        ('ST-010', 'Постамат ПМ-50', v_counter + 10, 'Постамат для выдачи готовых деталей', DATE '2024-11-15', 'SN-010-2024', v_model_2001_uid, NULL, hold_south_id, ent4_id, ws6_id, secH_id, 'WORKING', 50, 45, 42, 8, 200, 178, NULL, false, false, true, false, false, false, '192.168.4.101', 8083, NULL)
    ON CONFLICT (uid) DO NOTHING;
    
    UPDATE stations SET has_additional_module = true WHERE uid = 'ST-001';
    UPDATE stations SET has_additional_module = true WHERE uid = 'ST-003';
    
END $$;

-- ============================================================
-- 50. ДОКУМЕНТЫ И СОБЫТИЯ СТАНЦИЙ
-- ============================================================

DO $$
DECLARE
    v_station_uid VARCHAR(50);
    v_doc_uid UUID;
    v_doc_name VARCHAR(500);
BEGIN
    FOR v_station_uid IN SELECT uid FROM stations WHERE uid IN ('ST-001', 'ST-002', 'ST-003') LOOP
        v_doc_uid := gen_random_uuid();
        v_doc_name := 'Паспорт станции ' || v_station_uid;
        INSERT INTO station_documents (uid, station_uid, document_name, file_path, original_name, created_at) VALUES (v_doc_uid, v_station_uid, v_doc_name, v_doc_uid::text || '.pdf', v_doc_name || '.pdf', NOW());
    END LOOP;
END $$;

INSERT INTO station_event_log (uid, station_uid, event_type, event_description, field_name, old_value, new_value, author, source, created_at) VALUES
    (gen_random_uuid(), 'ST-001', 'CREATE', 'Станция создана', NULL, NULL, NULL, 'admin', 'Система', NOW() - INTERVAL '30 days'),
    (gen_random_uuid(), 'ST-001', 'UPDATE', 'Обновлен статус', 'status', 'OFFLINE', 'WORKING', 'admin', 'Через карточку', NOW() - INTERVAL '10 days'),
    (gen_random_uuid(), 'ST-002', 'CREATE', 'Станция создана', NULL, NULL, NULL, 'admin', 'Система', NOW() - INTERVAL '25 days'),
    (gen_random_uuid(), 'ST-003', 'CREATE', 'Станция создана', NULL, NULL, NULL, 'admin', 'Система', NOW() - INTERVAL '20 days'),
    (gen_random_uuid(), 'ST-004', 'ERROR', 'Критический остаток', 'status', 'MINIMAL_STOCK', 'CRITICAL_STOCK', 'system', 'Автоматически', NOW() - INTERVAL '2 days')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 51. СИДЫ: РЕЙТИНГИ ПОСТАВЩИКОВ
-- ============================================================

DO $$
DECLARE
    v_supplier_uid UUID;
BEGIN
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'ООО "ПромСнаб"';
    INSERT INTO reg_supplier_ratings (uid, supplier_uid, rating, comment, author, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 5, 'Отличный поставщик, всегда вовремя', 'admin', NOW() - INTERVAL '15 days'),
        (gen_random_uuid(), v_supplier_uid, 4, 'Хорошее качество продукции', 'operator', NOW() - INTERVAL '10 days');
    
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'АО "ТехКомплект"';
    INSERT INTO reg_supplier_ratings (uid, supplier_uid, rating, comment, author, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 5, 'Профессиональный подход', 'admin', NOW() - INTERVAL '8 days');
    
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'ЗАО "ИнструментСервис"';
    INSERT INTO reg_supplier_ratings (uid, supplier_uid, rating, comment, author, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 4, 'Хорошие цены, доставка 2 недели', 'admin', NOW() - INTERVAL '5 days');
END $$;

-- ============================================================
-- 52. СИДЫ: ИНТЕГРАЦИИ ПОСТАВЩИКОВ
-- ============================================================

DO $$
DECLARE
    v_supplier_uid UUID;
BEGIN
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'ООО "ПромСнаб"';
    INSERT INTO reg_supplier_integration (uid, supplier_uid, event, exchange_type, direction, protocol, target_system, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 'Объект синхронизирован', 'API', 'OUT', 'REST', '1C:ERP', NOW() - INTERVAL '20 days');
    
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'ЗАО "ИнструментСервис"';
    INSERT INTO reg_supplier_integration (uid, supplier_uid, event, exchange_type, direction, protocol, target_system, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 'Объект синхронизирован', 'FILE', 'IN', 'XML', 'AWMS', NOW() - INTERVAL '15 days');
END $$;

-- ============================================================
-- 53. СИДЫ: СОБЫТИЯ ПОСТАВЩИКОВ
-- ============================================================

DO $$
DECLARE
    v_supplier_uid UUID;
BEGIN
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'ООО "ПромСнаб"';
    INSERT INTO reg_supplier_event_log (uid, supplier_uid, event_type, event_description, field_name, old_value, new_value, author, source, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 'CREATE', 'Поставщик создан', NULL, NULL, NULL, 'admin', 'Система', NOW() - INTERVAL '30 days'),
        (gen_random_uuid(), v_supplier_uid, 'UPDATE', 'Обновлен телефон', 'phone', '+7 (495) 000-00-00', '+7 (495) 123-45-67', 'admin', 'Через карточку', NOW() - INTERVAL '10 days');
    
    SELECT uid INTO v_supplier_uid FROM spr_suppliers WHERE name = 'АО "ТехКомплект"';
    INSERT INTO reg_supplier_event_log (uid, supplier_uid, event_type, event_description, field_name, old_value, new_value, author, source, created_at) VALUES
        (gen_random_uuid(), v_supplier_uid, 'CREATE', 'Поставщик создан', NULL, NULL, NULL, 'admin', 'Система', NOW() - INTERVAL '25 days');
END $$;

-- ============================================================
-- 54. СИДЫ: РЕЙТИНГИ НОМЕНКЛАТУРЫ
-- ============================================================

DO $$
DECLARE
    v_material_uid UUID;
    v_idx INTEGER;
BEGIN
    FOR v_idx IN 1..10 LOOP
        SELECT uid INTO v_material_uid FROM spr_material WHERE code_material = v_idx LIMIT 1;
        
        IF v_material_uid IS NOT NULL THEN
            INSERT INTO reg_rating (uid, material_uid, rating, comment, author, created_at) VALUES (gen_random_uuid(), v_material_uid, 3 + (v_idx % 3), 'Хорошее качество, рекомендую', 'admin', NOW() - (v_idx || ' days')::INTERVAL);
        END IF;
    END LOOP;
END $$;

-- ============================================================
-- 55. СИДЫ: ИНТЕГРАЦИИ НОМЕНКЛАТУРЫ
-- ============================================================

DO $$
DECLARE
    v_material_uid UUID;
    v_idx INTEGER;
BEGIN
    FOR v_idx IN 1..10 LOOP
        SELECT uid INTO v_material_uid FROM spr_material WHERE code_material = v_idx LIMIT 1;
        
        IF v_material_uid IS NOT NULL THEN
            INSERT INTO reg_integration (uid, material_uid, event, exchange_type, direction, protocol, target_system, created_at) VALUES (gen_random_uuid(), v_material_uid, 'Объект синхронизирован', 'API', 'OUT', 'REST', '1C:ERP', NOW() - (v_idx || ' days')::INTERVAL);
        END IF;
    END LOOP;
END $$;

-- ============================================================
-- 56. СИДЫ: СОБЫТИЯ НОМЕНКЛАТУРЫ
-- ============================================================

DO $$
DECLARE
    v_material_uid UUID;
    v_idx INTEGER;
BEGIN
    FOR v_idx IN 1..10 LOOP
        SELECT uid INTO v_material_uid FROM spr_material WHERE code_material = v_idx LIMIT 1;
        
        IF v_material_uid IS NOT NULL THEN
            INSERT INTO reg_event_log (uid, material_uid, event_type, event_description, field_name, old_value, new_value, author, source, created_at) VALUES (gen_random_uuid(), v_material_uid, 'CREATE', 'Номенклатура создана', NULL, NULL, NULL, 'admin', 'Система', NOW() - (v_idx || ' days')::INTERVAL);
        END IF;
    END LOOP;
END $$;

-- ============================================================
-- 57. СИДЫ: АНАЛОГИ
-- ============================================================

DO $$
DECLARE
    v_mat1_uid UUID;
    v_mat2_uid UUID;
BEGIN
    SELECT uid INTO v_mat1_uid FROM spr_material WHERE code_material = 1 LIMIT 1;
    SELECT uid INTO v_mat2_uid FROM spr_material WHERE code_material = 2 LIMIT 1;
    
    IF v_mat1_uid IS NOT NULL AND v_mat2_uid IS NOT NULL THEN
        INSERT INTO reg_analog (uid, material_uid, analog_material_uid, compatibility_percent, created_at) VALUES (gen_random_uuid(), v_mat1_uid, v_mat2_uid, 95, NOW());
    END IF;
    
    SELECT uid INTO v_mat1_uid FROM spr_material WHERE code_material = 3 LIMIT 1;
    SELECT uid INTO v_mat2_uid FROM spr_material WHERE code_material = 4 LIMIT 1;
    
    IF v_mat1_uid IS NOT NULL AND v_mat2_uid IS NOT NULL THEN
        INSERT INTO reg_analog (uid, material_uid, analog_material_uid, compatibility_percent, created_at) VALUES (gen_random_uuid(), v_mat1_uid, v_mat2_uid, 90, NOW());
    END IF;
END $$;

-- ============================================================
-- 58. СИДЫ: НАСТРОЙКИ КОЛОНОК
-- ============================================================

INSERT INTO user_station_column_settings (user_id, columns_json, filters_json, sort_json)
SELECT 1, '[{"key":"name","label":"Название","visible":true},{"key":"status","label":"Статус","visible":true},{"key":"code","label":"Код","visible":true}]'::jsonb, '{}'::jsonb, '{"key":"name","direction":"asc"}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM user_station_column_settings WHERE user_id = 1);

INSERT INTO user_nomenclature_column_settings (user_id, columns_json, filters_json, sort_json, current_path_json)
SELECT 1, '[{"key":"name_material","label":"Название","visible":true},{"key":"article","label":"Артикул","visible":true},{"key":"code_material","label":"Код","visible":true}]'::jsonb, '{}'::jsonb, '{"key":"code_material","direction":"asc"}'::jsonb, '[]'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM user_nomenclature_column_settings WHERE user_id = 1);

-- ============================================================
-- 59. СИДЫ: ТЕСТОВЫЕ ДОКУМЕНТЫ
-- ============================================================

INSERT INTO test_documents (user_id, title, field2, field3, completed) VALUES
    (1, 'Тестовый документ 1', 'Поле 2 значение 1', 'Поле 3 значение 1', false),
    (1, 'Тестовый документ 2', 'Поле 2 значение 2', 'Поле 3 значение 2', true),
    (1, 'Тестовый документ 3', 'Поле 2 значение 3', 'Поле 3 значение 3', false)
ON CONFLICT DO NOTHING;

COMMIT;