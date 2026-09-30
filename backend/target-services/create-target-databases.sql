-- Systivex Phase 2B: target-service database bootstrap (LOCAL DEVELOPMENT ONLY).
--
-- Creates the three service-owned databases and their application users:
--
--   order_db      owned by order_app
--   payment_db    owned by payment_app
--   inventory_db  owned by inventory_app
--
-- CREDENTIALS ARE LOCAL-ONLY AND MUST NEVER BE COMMITTED.
-- This file contains no passwords: supply each one on the psql command line
-- (see below). Pick three distinct passwords and keep them in your local
-- environment (e.g. ORDER_DB_PASSWORD / PAYMENT_DB_PASSWORD /
-- INVENTORY_DB_PASSWORD) or in the git-ignored local application.properties
-- of each service. Never write them into a tracked file.
--
-- Run as a PostgreSQL superuser (e.g. postgres) with -f file mode (psql
-- variable substitution does not apply to -c strings in some builds;
-- always run this file with -f). PowerShell example:
--
--   $env:PGPASSWORD = "<your local postgres superuser password, never committed>"
--   & psql -h localhost -U postgres `
--     -v order_pw="<choose a local order_app password>" `
--     -v payment_pw="<choose a local payment_app password>" `
--     -v inventory_pw="<choose a local inventory_app password>" `
--     -f create-target-databases.sql
--   Remove-Item Env:\PGPASSWORD
--
-- Safety notes:
-- - This script only creates roles/databases that do not exist yet and
--   refreshes the passwords of the three application roles. It never touches
--   the control-plane database (systivex) and issues no destructive commands
--   (no DROP, no TRUNCATE, no DELETE).
-- - If a database already exists it is left untouched.

-- ---- Roles (create when missing; passwords are set below, outside the
-- ---- DO bodies, because psql does not substitute variables inside
-- ---- dollar-quoted blocks) ----

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'order_app') THEN
        CREATE ROLE order_app LOGIN;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_app') THEN
        CREATE ROLE payment_app LOGIN;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'inventory_app') THEN
        CREATE ROLE inventory_app LOGIN;
    END IF;
END
$$;

ALTER ROLE order_app WITH LOGIN PASSWORD :'order_pw';
ALTER ROLE payment_app WITH LOGIN PASSWORD :'payment_pw';
ALTER ROLE inventory_app WITH LOGIN PASSWORD :'inventory_pw';

-- ---- Databases (created only when missing; existing ones are untouched) ----

SELECT 'CREATE DATABASE order_db OWNER order_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'order_db')\gexec

SELECT 'CREATE DATABASE payment_db OWNER payment_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'payment_db')\gexec

SELECT 'CREATE DATABASE inventory_db OWNER inventory_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'inventory_db')\gexec
