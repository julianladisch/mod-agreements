select 1;

CREATE USER folio_admin WITH PASSWORD 'folio_admin';
CREATE USER folio WITH PASSWORD 'folio';
DROP DATABASE if exists okapi_modules_test;
DROP DATABASE if exists okapi_modules;
DROP DATABASE if exists olfdev;
DROP DATABASE if exists olftest;
CREATE DATABASE olftest;
CREATE DATABASE okapi_modules_test;
CREATE DATABASE okapi_modules;
CREATE DATABASE olfdev;
GRANT ALL PRIVILEGES ON DATABASE okapi_modules_test to folio_admin;
GRANT ALL PRIVILEGES ON DATABASE okapi_modules to folio_admin;
GRANT ALL PRIVILEGES ON DATABASE olfdev to folio;
GRANT ALL PRIVILEGES ON DATABASE olftest to folio;

ALTER USER folio CREATEDB;
ALTER USER folio CREATEROLE;
ALTER USER folio WITH SUPERUSER;
ALTER USER folio_admin CREATEDB;
ALTER USER folio_admin CREATEROLE;
ALTER USER folio_admin WITH SUPERUSER;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
