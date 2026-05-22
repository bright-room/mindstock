-- V20260523000001__append_only_role.sql
--
-- Append-only enforcement: define a role that has only SELECT and INSERT
-- on every domain table. The application connects as this role at
-- runtime. Migrations / admin tasks use a different (more privileged)
-- role.
--
-- Uses current_schema() so this migration works correctly in both the
-- production "public" schema and the per-test random schemas that
-- Testcontainers creates.

DO $$
DECLARE
    sch text := current_schema();
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'mindstock_app') THEN
        CREATE ROLE mindstock_app NOINHERIT NOLOGIN;
    END IF;
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO mindstock_app', sch);
    EXECUTE format('GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA %I TO mindstock_app', sch);
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT ON TABLES TO mindstock_app', sch);
END$$;
