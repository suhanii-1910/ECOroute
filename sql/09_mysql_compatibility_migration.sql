-- ============================================================================
-- ECOroute: Non-Destructive Compatibility Migration & Password Hash Init
-- Database: ecoroute_db
-- Purpose:  Aligns existing MySQL schema with Java DAOs and initializes
--           valid PBKDF2-HMAC-SHA256 password hashes for demo accounts.
-- ============================================================================

USE ecoroute_db;

-- ----------------------------------------------------------------------------
-- Part 1: Schema Compatibility Migrations (Non-Destructive & Idempotent)
-- ----------------------------------------------------------------------------

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS ecoroute_apply_migrations()
BEGIN
    -- 1. Add is_active to USER table if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'USER' AND COLUMN_NAME = 'is_active'
    ) THEN
        ALTER TABLE `USER` ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;

    -- 2. Add is_active to WASTE_GENERATOR table if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'WASTE_GENERATOR' AND COLUMN_NAME = 'is_active'
    ) THEN
        ALTER TABLE WASTE_GENERATOR ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
    END IF;

    -- 3. Add completion_date to PICKUP_REQUEST table if missing
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PICKUP_REQUEST' AND COLUMN_NAME = 'completion_date'
    ) THEN
        ALTER TABLE PICKUP_REQUEST ADD COLUMN completion_date DATETIME NULL;
    END IF;
END $$
DELIMITER ;

CALL ecoroute_apply_migrations();
DROP PROCEDURE IF EXISTS ecoroute_apply_migrations;

-- 4. Make email nullable so multiple users without explicit emails do not violate uq_user_email.
--    In MySQL / InnoDB, a UNIQUE constraint permits multiple NULL values, but only one empty string ('').
--    Existing user emails are non-null and remain completely preserved.
--    Ensure full_name defaults to empty string for seamless DAO insertion.
ALTER TABLE `USER`
    MODIFY COLUMN email VARCHAR(120) NULL DEFAULT NULL,
    MODIFY COLUMN full_name VARCHAR(120) NOT NULL DEFAULT '';

-- ----------------------------------------------------------------------------
-- Part 2: Demo Accounts Password Hash Initialization
-- Password:  Password@123
-- Algorithm: PBKDF2WithHmacSHA256 (600,000 iterations, 16-byte salt, 256-bit key)
-- Format:    pbkdf2-sha256$<iterations>$<base64-salt>$<base64-hash>
-- Generated and verified via ecoroute.service.PasswordHasher
-- ----------------------------------------------------------------------------

-- Admin: admin / Password@123
UPDATE `USER`
SET password_hash = 'pbkdf2-sha256$600000$B1pmnr30C0BsQuZljbsUww==$7eL9qG8uk4UgN/S+85Iw5bBu7Td5V0+/8LMuz+vOEl4=',
    is_active = TRUE
WHERE username = 'admin';

-- Operator: operator1 / Password@123
UPDATE `USER`
SET password_hash = 'pbkdf2-sha256$600000$yh58Ku9fdr9u7xoaLS+JgQ==$kKckanEcqSPfmaCQ3xeN4ink1wPj2WqY8V08ji0Q0JY=',
    is_active = TRUE
WHERE username = 'operator1';

-- Driver: driver1 / Password@123
UPDATE `USER`
SET password_hash = 'pbkdf2-sha256$600000$Lg+lGwhd18Kk8gS16QCg2g==$VUvlFT3TKSv3rRro4bBUpv/RWg6zFP19Eq69iVDfJbA=',
    is_active = TRUE
WHERE username = 'driver1';

-- Generator (Hospital): hospital_gen / Password@123
UPDATE `USER`
SET password_hash = 'pbkdf2-sha256$600000$VBYOyp/bz1a5R2gtSXAndQ==$oWu+JG6vNxr/FjG5dNZ3O9CTHGqw8j/BX3fYAvauegM=',
    is_active = TRUE
WHERE username = 'hospital_gen';

-- Generator (Housing Society): society_gen / Password@123
-- Ensure record exists for generator_id = 2, then set active and hash
INSERT IGNORE INTO `USER` (username, password_hash, email, full_name, role, generator_id, is_active)
VALUES ('society_gen',
        'pbkdf2-sha256$600000$jrO+W260owQWvgili/DD4Q==$r27ph4DDL/RbiGbtesyCx2tDDr1nd3+Tznt4zuZLEtw=',
        'society_gen@society.org', 'Green Valley Society Generator', 'GENERATOR', 2, TRUE);

UPDATE `USER`
SET password_hash = 'pbkdf2-sha256$600000$jrO+W260owQWvgili/DD4Q==$r27ph4DDL/RbiGbtesyCx2tDDr1nd3+Tznt4zuZLEtw=',
    is_active = TRUE
WHERE username = 'society_gen';

-- Replace any remaining placeholder hashes
UPDATE `USER`
SET password_hash = 'pbkdf2-sha256$600000$rR+1UoWzEOXCHxvmqRLCmw==$Jyj1Po9znxxJghgBvy+Qq103NT48YKeNGsE0CGnjqU0='
WHERE password_hash LIKE 'pbkdf2-sha256$600000$AAAAAAAA%'
   OR password_hash LIKE 'pbkdf2-sha256$600000$c2FsdHNhbHRzYWx0c2FsdA%'
   OR password_hash LIKE '%placeholder%'
   OR password_hash LIKE '%hashhashhash%';
