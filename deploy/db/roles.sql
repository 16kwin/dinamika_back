-- roles.sql — роли PostgreSQL для AWMS «ДИНАМИКА»: владелец схемы (Flyway) и рабочая роль приложения.
-- Обязательный шаг установки (пакет D1, раздел 9.4 проекта). Без него приложение работает под суперпользователем,
-- и журналы профиля и доступа, а также переключатели функций можно молча править и удалять.
--
--   awms_owner — владелец схемы public и всех таблиц. Под ним Flyway применяет миграции при старте
--                (spring.flyway.user). Суперпользователем не является.
--   awms_app   — рабочая роль приложения (spring.datasource.username): SELECT/INSERT/UPDATE/DELETE на таблицы,
--                без TRUNCATE и без права создавать объекты. Журналы sys_feature_log и sys_access_log —
--                только INSERT и SELECT; переключатели sys_feature_switch — без DELETE.
--
-- Запуск — администратор СУБД под суперпользователем, в базе приложения:
--   psql -U postgres -d dinamika -v ON_ERROR_STOP=1 -f roles.sql
-- Скрипт можно запускать повторно. Запускают его дважды:
--   1) до первого запуска новой службы — роли, схема, права по умолчанию для будущих таблиц;
--   2) после первого запуска (и после каждого обновления с новыми миграциями) — права на созданные таблицы,
--      ограничения журналов и переключателей.
-- Пароли в файл не пишутся. После первого запуска скрипта задать их в psql (пароль вводится с клавиатуры):
--   \password awms_owner
--   \password awms_app
-- и записать в config/awms-secrets.properties (spring.flyway.*, spring.datasource.*).
--
-- База должна принадлежать только AWMS: скрипт передаёт awms_owner схему public и все объекты в ней.
-- Другие имена ролей — заменить awms_owner / awms_app по всему файлу.

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------------------------------------------
-- 1. Роли: вход разрешён, без суперпользователя, без создания баз и ролей, без репликации
-- ---------------------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'awms_owner') THEN
        CREATE ROLE awms_owner LOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'awms_app') THEN
        CREATE ROLE awms_app LOGIN;
    END IF;
    -- Рабочая роль не должна получать права владельца через членство
    IF EXISTS (SELECT 1 FROM pg_auth_members m
               WHERE m.roleid = 'awms_owner'::regrole AND m.member = 'awms_app'::regrole) THEN
        REVOKE awms_owner FROM awms_app;
    END IF;
END $$;

-- Роли могли существовать раньше: снимаем лишние атрибуты при каждом запуске
ALTER ROLE awms_owner NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS LOGIN;
ALTER ROLE awms_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS LOGIN;

-- ---------------------------------------------------------------------------------------------------------
-- 2. Подключение к базе и схема public
-- ---------------------------------------------------------------------------------------------------------
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO awms_owner, awms_app', current_database());
END $$;

-- Схема принадлежит владельцу: Flyway создаёт в ней таблицы. Создавать объекты больше никто не может
ALTER SCHEMA public OWNER TO awms_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO awms_app;

-- ---------------------------------------------------------------------------------------------------------
-- 3. Уже существующие объекты (база, где Flyway раньше работал под postgres) — владельцу схемы.
--    Последовательности столбцов SERIAL переходят вместе с таблицей; объекты расширений не трогаются
-- ---------------------------------------------------------------------------------------------------------
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT c.relname, c.relkind
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
          AND c.relowner <> 'awms_owner'::regrole
          AND NOT EXISTS (SELECT 1 FROM pg_depend d
                          WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                            AND (d.deptype = 'e' OR (c.relkind = 'S' AND d.deptype IN ('a', 'i'))))
    LOOP
        EXECUTE format('ALTER %s public.%I OWNER TO awms_owner',
                       CASE r.relkind
                           WHEN 'v' THEN 'VIEW'
                           WHEN 'm' THEN 'MATERIALIZED VIEW'
                           WHEN 'S' THEN 'SEQUENCE'
                           WHEN 'f' THEN 'FOREIGN TABLE'
                           ELSE 'TABLE'
                       END,
                       r.relname);
    END LOOP;

    -- Функции (например, sys_append_only журналов)
    FOR r IN
        SELECT p.oid::regprocedure AS signature
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.proowner <> 'awms_owner'::regrole
          AND NOT EXISTS (SELECT 1 FROM pg_depend d
                          WHERE d.classid = 'pg_proc'::regclass AND d.objid = p.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER ROUTINE %s OWNER TO awms_owner', r.signature);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------------------------------------------
-- 4. Права рабочей роли на существующие таблицы и последовательности: только DML
-- ---------------------------------------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO awms_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO awms_app;

-- История миграций — дело Flyway: рабочей роли только чтение
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM awms_app;
    END IF;
END $$;

-- ---------------------------------------------------------------------------------------------------------
-- 5. Права по умолчанию: таблицы будущих миграций (их создаёт awms_owner) сразу доступны рабочей роли
-- ---------------------------------------------------------------------------------------------------------
ALTER DEFAULT PRIVILEGES FOR ROLE awms_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO awms_app;
ALTER DEFAULT PRIVILEGES FOR ROLE awms_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO awms_app;

-- ---------------------------------------------------------------------------------------------------------
-- 6. Журналы — только добавление и чтение; строки переключателей не удаляются.
--    Таблицы появляются с миграцией V18 (после первого запуска такой версии — запустить скрипт ещё раз).
--    Стоит после пункта 4: тот выдал DML на все таблицы, здесь лишнее снимается
-- ---------------------------------------------------------------------------------------------------------
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['sys_feature_log', 'sys_access_log'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            EXECUTE format('REVOKE ALL ON public.%I FROM PUBLIC', t);
            EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON public.%I FROM awms_app', t);
            EXECUTE format('GRANT SELECT, INSERT ON public.%I TO awms_app', t);
        END IF;
    END LOOP;
    IF to_regclass('public.sys_feature_switch') IS NOT NULL THEN
        REVOKE ALL ON public.sys_feature_switch FROM PUBLIC;
        REVOKE DELETE, TRUNCATE, REFERENCES, TRIGGER ON public.sys_feature_switch FROM awms_app;
        GRANT SELECT, INSERT, UPDATE ON public.sys_feature_switch TO awms_app;
    END IF;
END $$;

-- Необязательно: запретить подключение к базе всем, кому оно не выдано явно (проверить, не нужен ли доступ
-- мониторингу или резервному копированию под другими ролями)
-- REVOKE CONNECT, TEMPORARY ON DATABASE dinamika FROM PUBLIC;

-- ---------------------------------------------------------------------------------------------------------
-- 7. Итог: атрибуты ролей и права на журналы. Ожидается: super = f, пароль задан = t (после \password),
--    у awms_app на журналах delete = f, update = f
-- ---------------------------------------------------------------------------------------------------------
SELECT a.rolname                     AS "роль",
       a.rolsuper                    AS "super",
       a.rolcreaterole               AS "createrole",
       a.rolcreatedb                 AS "createdb",
       a.rolpassword IS NOT NULL     AS "пароль задан"
FROM pg_authid a
WHERE a.rolname IN ('awms_owner', 'awms_app')
ORDER BY a.rolname;

SELECT t.name                                                              AS "таблица",
       has_table_privilege('awms_app', 'public.' || t.name, 'INSERT')      AS "insert",
       has_table_privilege('awms_app', 'public.' || t.name, 'UPDATE')      AS "update",
       has_table_privilege('awms_app', 'public.' || t.name, 'DELETE')      AS "delete",
       has_table_privilege('awms_app', 'public.' || t.name, 'TRUNCATE')    AS "truncate"
FROM unnest(ARRAY['sys_feature_log', 'sys_access_log', 'sys_feature_switch']) AS t(name)
WHERE to_regclass('public.' || t.name) IS NOT NULL;

-- Проверка вручную под рабочей ролью (строки не удаляются: WHERE false; права проверяются раньше):
--   psql -U awms_app -d dinamika -c "DELETE FROM sys_feature_log WHERE false"
--   ожидается: ERROR: permission denied for table sys_feature_log
