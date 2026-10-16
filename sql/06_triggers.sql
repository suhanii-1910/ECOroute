-- ECOroute: Triggers
-- Enforces disjoint specialisation constraints for generator and facility subtypes.
-- Run after 05_seed_data.sql.

USE ecoroute_db;
DELIMITER $$

-- ============================================================
-- Generator subtype guards
-- A HOSPITAL row may only reference a generator with type='HOSPITAL'
-- ============================================================
DROP TRIGGER IF EXISTS trg_hospital_check_type$$
CREATE TRIGGER trg_hospital_check_type
BEFORE INSERT ON HOSPITAL
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(40);
    SELECT generator_type INTO v_type FROM WASTE_GENERATOR WHERE generator_id = NEW.generator_id;
    IF v_type <> 'HOSPITAL' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'HOSPITAL row must reference a HOSPITAL generator';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_housing_check_type$$
CREATE TRIGGER trg_housing_check_type
BEFORE INSERT ON HOUSING_SOCIETY
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(40);
    SELECT generator_type INTO v_type FROM WASTE_GENERATOR WHERE generator_id = NEW.generator_id;
    IF v_type <> 'HOUSING_SOCIETY' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'HOUSING_SOCIETY row must reference a HOUSING_SOCIETY generator';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_factory_check_type$$
CREATE TRIGGER trg_factory_check_type
BEFORE INSERT ON FACTORY
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(40);
    SELECT generator_type INTO v_type FROM WASTE_GENERATOR WHERE generator_id = NEW.generator_id;
    IF v_type <> 'FACTORY' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'FACTORY row must reference a FACTORY generator';
    END IF;
END$$

-- Prevent changing generator_type once a subtype row exists
DROP TRIGGER IF EXISTS trg_generator_type_immutable$$
CREATE TRIGGER trg_generator_type_immutable
BEFORE UPDATE ON WASTE_GENERATOR
FOR EACH ROW
BEGIN
    DECLARE v_count INT;
    IF NEW.generator_type <> OLD.generator_type THEN
        SELECT COUNT(*) INTO v_count
        FROM HOSPITAL WHERE generator_id = OLD.generator_id;
        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot change generator_type: HOSPITAL subtype row exists';
        END IF;

        SELECT COUNT(*) INTO v_count
        FROM HOUSING_SOCIETY WHERE generator_id = OLD.generator_id;
        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot change generator_type: HOUSING_SOCIETY subtype row exists';
        END IF;

        SELECT COUNT(*) INTO v_count
        FROM FACTORY WHERE generator_id = OLD.generator_id;
        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot change generator_type: FACTORY subtype row exists';
        END IF;
    END IF;
END$$

-- ============================================================
-- Facility subtype guards
-- ============================================================
DROP TRIGGER IF EXISTS trg_recycling_check_type$$
CREATE TRIGGER trg_recycling_check_type
BEFORE INSERT ON RECYCLING_PLANT
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(40);
    SELECT facility_type INTO v_type FROM DISPOSAL_FACILITY WHERE facility_id = NEW.facility_id;
    IF v_type <> 'RECYCLING_PLANT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RECYCLING_PLANT row must reference a RECYCLING_PLANT facility';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_processing_check_type$$
CREATE TRIGGER trg_processing_check_type
BEFORE INSERT ON PROCESSING_PLANT
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(40);
    SELECT facility_type INTO v_type FROM DISPOSAL_FACILITY WHERE facility_id = NEW.facility_id;
    IF v_type <> 'PROCESSING_PLANT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'PROCESSING_PLANT row must reference a PROCESSING_PLANT facility';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_hazardous_check_type$$
CREATE TRIGGER trg_hazardous_check_type
BEFORE INSERT ON HAZARDOUS_DISPOSAL_SITE
FOR EACH ROW
BEGIN
    DECLARE v_type VARCHAR(40);
    SELECT facility_type INTO v_type FROM DISPOSAL_FACILITY WHERE facility_id = NEW.facility_id;
    IF v_type <> 'HAZARDOUS_DISPOSAL_SITE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'HAZARDOUS_DISPOSAL_SITE row must reference a HAZARDOUS_DISPOSAL_SITE facility';
    END IF;
END$$

-- Prevent changing facility_type once a subtype row exists
DROP TRIGGER IF EXISTS trg_facility_type_immutable$$
CREATE TRIGGER trg_facility_type_immutable
BEFORE UPDATE ON DISPOSAL_FACILITY
FOR EACH ROW
BEGIN
    DECLARE v_count INT;
    IF NEW.facility_type <> OLD.facility_type THEN
        SELECT COUNT(*) INTO v_count FROM RECYCLING_PLANT WHERE facility_id = OLD.facility_id;
        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot change facility_type: RECYCLING_PLANT subtype row exists';
        END IF;

        SELECT COUNT(*) INTO v_count FROM PROCESSING_PLANT WHERE facility_id = OLD.facility_id;
        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot change facility_type: PROCESSING_PLANT subtype row exists';
        END IF;

        SELECT COUNT(*) INTO v_count FROM HAZARDOUS_DISPOSAL_SITE WHERE facility_id = OLD.facility_id;
        IF v_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Cannot change facility_type: HAZARDOUS_DISPOSAL_SITE subtype row exists';
        END IF;
    END IF;
END$$

-- ============================================================
-- USER role/generator consistency
-- GENERATOR user must have generator_id; non-GENERATOR must not
-- ============================================================
DROP TRIGGER IF EXISTS trg_user_generator_check$$
CREATE TRIGGER trg_user_generator_check
BEFORE INSERT ON `USER`
FOR EACH ROW
BEGIN
    IF NEW.role = 'GENERATOR' AND NEW.generator_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'GENERATOR user must have a generator_id';
    END IF;
    IF NEW.role <> 'GENERATOR' AND NEW.generator_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Non-GENERATOR user must not have a generator_id';
    END IF;
END$$

DROP TRIGGER IF EXISTS trg_user_generator_check_upd$$
CREATE TRIGGER trg_user_generator_check_upd
BEFORE UPDATE ON `USER`
FOR EACH ROW
BEGIN
    IF NEW.role = 'GENERATOR' AND NEW.generator_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'GENERATOR user must have a generator_id';
    END IF;
    IF NEW.role <> 'GENERATOR' AND NEW.generator_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Non-GENERATOR user must not have a generator_id';
    END IF;
END$$

DELIMITER ;
