-- ECOroute: Create Database
-- Run once as root / DBA. Creates the schema and a dedicated application user.
-- Usage: mysql -u root -p < sql/01_create_database.sql

CREATE DATABASE IF NOT EXISTS ecoroute_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Application user (no plaintext password in history — set via env or prompt)
-- CREATE USER IF NOT EXISTS 'ecoroute_app'@'localhost' IDENTIFIED BY '<CHANGE_ME>';
-- GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON ecoroute_db.* TO 'ecoroute_app'@'localhost';
-- FLUSH PRIVILEGES;

USE ecoroute_db;
