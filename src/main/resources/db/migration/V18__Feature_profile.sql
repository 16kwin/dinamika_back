-- V18__Feature_profile.sql
-- Дискретное отключение функций: журнал профиля (только добавление, HMAC-цепочка), журнал доступа,
-- переключатели администратора внутри потолка, признаки учётных записей, зашифрованные пароли камер.
-- Потолок задаётся файлом config/awms-features.properties, в БД его нет. Данные модулей не трогаются:
-- миграция только добавляет таблицы. Номер V18 закреплён за проектом выключателей, миграции ГПП — с V19.
-- Сущности JPA добавляют пакеты-владельцы: журналы — B6, переключатели — B7, признаки учётных записей — B10,
-- пароли камер — B11.

-- Журнал профиля: что включено на установке и кто это менял
CREATE TABLE IF NOT EXISTS sys_feature_log (
    id            BIGSERIAL     PRIMARY KEY,
    occurred_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- STARTUP, CEILING_CHANGED, SWITCH, TAMPER, PURGE, RETENTION, CREDENTIALS_PURGED, USER_CREATED,
    -- REPORT_VIEWED, REPORT_PRINTED, REPORT_EXPORTED, PROBE_RUN, DENIED_SUMMARY, OUTBOUND_BLOCKED.
    -- Перечень закрыт сознательно: новый тип события профиля — осознанная миграция
    event_type    VARCHAR(32)   NOT NULL,
    feature_key   VARCHAR(64),
    old_value     VARCHAR(255),
    new_value     VARCHAR(255),
    preset        VARCHAR(32),
    fingerprint   VARCHAR(64)   NOT NULL,   -- отпечаток потолка в момент записи
    ticket        VARCHAR(500),             -- awms.features.ticket
    reason        VARCHAR(1000),            -- причина переключения или удаления
    actor         VARCHAR(255)  NOT NULL,   -- логин или учётная запись службы ОС
    actor_role    VARCHAR(64),
    client_ip     VARCHAR(64),
    host_name     VARCHAR(255),
    app_version   VARCHAR(64),
    config_sha256 VARCHAR(64),              -- SHA-256 файла потолка
    details       TEXT,                     -- JSON: разница профилей, каскад, сводка отказов
    prev_hash     VARCHAR(64),
    row_hash      VARCHAR(64)   NOT NULL,   -- HMAC-SHA256(awms.journal.hmac-key, prev_hash || канонический JSON строки)
    key_id        VARCHAR(16)   NOT NULL,   -- идентификатор ключа HMAC; none — цепочка SHA-256 без ключа (до B10)
    CONSTRAINT sys_feature_log_type_chk CHECK (event_type IN ('STARTUP', 'CEILING_CHANGED', 'SWITCH', 'TAMPER',
        'PURGE', 'RETENTION', 'CREDENTIALS_PURGED', 'USER_CREATED', 'REPORT_VIEWED', 'REPORT_PRINTED',
        'REPORT_EXPORTED', 'PROBE_RUN', 'DENIED_SUMMARY', 'OUTBOUND_BLOCKED'))
);
CREATE INDEX IF NOT EXISTS ix_sys_feature_log_time ON sys_feature_log (occurred_at DESC);
-- Последний STARTUP и события TAMPER, PURGE, RETENTION для отчёта
CREATE INDEX IF NOT EXISTS ix_sys_feature_log_type ON sys_feature_log (event_type, id DESC);

-- Журнал доступа: использование функций с персональными и выгружаемыми данными (раздел 10.2 проекта).
-- Перечень типов — в коде (enum AccessAudit.Event), без CHECK: новый тип события не требует миграции
CREATE TABLE IF NOT EXISTS sys_access_log (
    id           BIGSERIAL     PRIMARY KEY,
    occurred_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type   VARCHAR(32)   NOT NULL,   -- VIDEO_VIEWED, VIDEO_LIVE_OPENED, VIDEO_DOWNLOADED, CAMERA_CHANGED, EXPORT, ...
    feature_key  VARCHAR(64)   NOT NULL,
    actor        VARCHAR(255)  NOT NULL,
    actor_role   VARCHAR(64),
    client_ip    VARCHAR(64),
    object_ref   VARCHAR(255),             -- id записи, файла или камеры; заголовок выгрузки
    details      TEXT                      -- JSON: размер, число строк, хост исходящего запроса; без паролей и самих данных
);
CREATE INDEX IF NOT EXISTS ix_sys_access_log_time ON sys_access_log (occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_sys_access_log_type ON sys_access_log (event_type, occurred_at DESC);

-- Оба журнала только для добавления: UPDATE, DELETE и TRUNCATE отклоняются. Суперпользователь БД триггер снять
-- может — правку строк журнала профиля выявляет HMAC-цепочка, удаление хвоста — сверка головы цепочки с файловым
-- логом AWMS.SECURITY и с напечатанным отчётом
CREATE OR REPLACE FUNCTION sys_append_only() RETURNS trigger LANGUAGE plpgsql AS
$$ BEGIN RAISE EXCEPTION '%: журнал только для добавления', TG_TABLE_NAME; END $$;
DROP TRIGGER IF EXISTS trg_sys_feature_log_no_change ON sys_feature_log;
CREATE TRIGGER trg_sys_feature_log_no_change BEFORE UPDATE OR DELETE ON sys_feature_log
    FOR EACH ROW EXECUTE PROCEDURE sys_append_only();
DROP TRIGGER IF EXISTS trg_sys_feature_log_no_truncate ON sys_feature_log;
CREATE TRIGGER trg_sys_feature_log_no_truncate BEFORE TRUNCATE ON sys_feature_log
    FOR EACH STATEMENT EXECUTE PROCEDURE sys_append_only();
DROP TRIGGER IF EXISTS trg_sys_access_log_no_change ON sys_access_log;
CREATE TRIGGER trg_sys_access_log_no_change BEFORE UPDATE OR DELETE ON sys_access_log
    FOR EACH ROW EXECUTE PROCEDURE sys_append_only();
DROP TRIGGER IF EXISTS trg_sys_access_log_no_truncate ON sys_access_log;
CREATE TRIGGER trg_sys_access_log_no_truncate BEFORE TRUNCATE ON sys_access_log
    FOR EACH STATEMENT EXECUTE PROCEDURE sys_append_only();

-- Переключатели администратора (этап 2). Нет строки — «вкл». Сузить можно только то, что разрешил потолок.
-- Таблица — рабочая копия: при загрузке сверяется с последними событиями SWITCH журнала (раздел 7 проекта)
CREATE TABLE IF NOT EXISTS sys_feature_switch (
    feature_key VARCHAR(64)  PRIMARY KEY,
    enabled     BOOLEAN      NOT NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by  VARCHAR(255) NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- Роль «Администратор ИБ»: профиль безопасности и переключатели, остальное — только чтение. Без префикса ROLE_, как V14
INSERT INTO roles (name, description) VALUES
    ('SECURITY', 'Администратор ИБ')
ON CONFLICT (name) DO NOTHING;

-- Признаки учётных записей (B10): обязательная смена пароля. Таблица users не меняется
CREATE TABLE IF NOT EXISTS sys_user_security (
    user_id              INTEGER      PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at  TIMESTAMP,
    created_by           VARCHAR(255)            -- учётная запись ОС, запустившая create-user
);
-- Засеянный admin с неизменённым паролем из V1 (V1__Init_database.sql, засев пользователя admin) обязан сменить
-- пароль при следующем входе. Действует после пакета B10; до него строка ничего не меняет
INSERT INTO sys_user_security (user_id, must_change_password)
SELECT id, TRUE FROM users
WHERE username = 'admin' AND password = '$2y$12$DuBHFqPN/liLWOOpazYz7eStzx9bIaUFQCQP1W52Vxy3JI5BxTEua'
ON CONFLICT (user_id) DO NOTHING;

-- Зашифрованные пароли камер (B11): AES-GCM, ключ awms.video.credentials-key вне БД.
-- Таблица video_camera не меняется; пароль из rtsp_url переносит сюда B11 при первом старте с ключом
CREATE TABLE IF NOT EXISTS video_camera_secret (
    camera_id    BIGINT       PRIMARY KEY REFERENCES video_camera (id) ON DELETE CASCADE,
    password_enc TEXT         NOT NULL,       -- base64(iv || шифртекст || тег)
    key_id       VARCHAR(16)  NOT NULL,
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Журналы — только добавление, строки переключателей не удаляются: права сверх этого снимаются у всех, кроме
-- владельца таблиц. Нужно, если рабочей роли выданы права по умолчанию (deploy/db/roles.sql, ALTER DEFAULT
-- PRIVILEGES): иначе до повторного запуска roles.sql она получила бы на новые таблицы полный DML. Владелец
-- (роль Flyway) и суперпользователь не затрагиваются; без выданных прав (стенд под postgres) блок ничего не делает
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT DISTINCT c.relname, a.grantee
        FROM pg_class c, aclexplode(c.relacl) a
        WHERE c.oid IN ('sys_feature_log'::regclass, 'sys_access_log'::regclass, 'sys_feature_switch'::regclass)
          AND a.grantee <> c.relowner
    LOOP
        EXECUTE format('REVOKE %s ON %I FROM %s',
                       CASE WHEN r.relname = 'sys_feature_switch' THEN 'DELETE, TRUNCATE'
                            ELSE 'UPDATE, DELETE, TRUNCATE' END,
                       r.relname,
                       CASE WHEN r.grantee = 0 THEN 'PUBLIC' ELSE quote_ident(pg_get_userbyid(r.grantee)) END);
    END LOOP;
END $$;
