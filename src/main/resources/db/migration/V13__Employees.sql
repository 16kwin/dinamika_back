-- V13__Employees.sql
-- Справочник сотрудников: spr_employees + employee_event_log + user_employee_column_settings

BEGIN;

-- ============================================================
-- 1. СПРАВОЧНИК СОТРУДНИКОВ
-- ============================================================

CREATE TABLE IF NOT EXISTS spr_employees (
    uid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ФИО
    first_name      VARCHAR(255),
    last_name       VARCHAR(255),
    middle_name     VARCHAR(255),

    -- Профессия: SECTION_HEAD / WORKSHOP_HEAD / PRODUCTION_HEAD
    profession      VARCHAR(50) NOT NULL,

    -- Привязка к объекту (заполнено ровно одно поле в зависимости от profession)
    enterprise_id   BIGINT REFERENCES enterprises(id) ON DELETE SET NULL,
    workshop_id     BIGINT REFERENCES workshops(id)   ON DELETE SET NULL,
    section_id      BIGINT REFERENCES sections(id)    ON DELETE SET NULL,

    -- Служебные
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_spr_employees_profession    ON spr_employees(profession);
CREATE INDEX IF NOT EXISTS idx_spr_employees_enterprise    ON spr_employees(enterprise_id);
CREATE INDEX IF NOT EXISTS idx_spr_employees_workshop      ON spr_employees(workshop_id);
CREATE INDEX IF NOT EXISTS idx_spr_employees_section       ON spr_employees(section_id);
CREATE INDEX IF NOT EXISTS idx_spr_employees_last_name     ON spr_employees(last_name);

-- ============================================================
-- 2. ЖУРНАЛ СОБЫТИЙ
-- ============================================================

CREATE TABLE IF NOT EXISTS employee_event_log (
    uid                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_uid        UUID REFERENCES spr_employees(uid) ON DELETE SET NULL,
    event_type          VARCHAR(50) NOT NULL,
    event_description   TEXT NOT NULL,
    field_name          VARCHAR(255),
    old_value           TEXT,
    new_value           TEXT,
    author              VARCHAR(255),
    source              VARCHAR(100) DEFAULT 'Через карточку',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_employee_event_log_employee
    ON employee_event_log(employee_uid);
CREATE INDEX IF NOT EXISTS idx_employee_event_log_created
    ON employee_event_log(created_at DESC);

-- ============================================================
-- 3. НАСТРОЙКИ КОЛОНОК / ФИЛЬТРОВ / СОРТИРОВКИ
-- ============================================================

CREATE TABLE IF NOT EXISTS user_employee_column_settings (
    id              BIGSERIAL PRIMARY KEY,
    user_id         INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    columns_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    filters_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    sort_json       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_employee_settings_user
    ON user_employee_column_settings(user_id);

COMMIT;