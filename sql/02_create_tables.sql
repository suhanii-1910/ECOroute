-- ECOroute: Create Tables (Final 18-Table Schema v3)
-- Run after 01_create_database.sql
-- Authoritative source: EcoRoute_Relational_Schema_v3_Implementation_Corrected.docx

USE ecoroute_db;

-- ============================================================
-- 1. ZONE
-- ============================================================
CREATE TABLE IF NOT EXISTS ZONE (
    zone_id   BIGINT UNSIGNED AUTO_INCREMENT,
    zone_name VARCHAR(100)  NOT NULL,
    description TEXT,
    created_date DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_zone PRIMARY KEY (zone_id),
    CONSTRAINT uq_zone_name UNIQUE (zone_name)
) ENGINE=InnoDB;

-- ============================================================
-- 2. WASTE_CATEGORY
-- ============================================================
CREATE TABLE IF NOT EXISTS WASTE_CATEGORY (
    category_id   BIGINT UNSIGNED AUTO_INCREMENT,
    category_name VARCHAR(100) NOT NULL,
    hazard_level  VARCHAR(20)  NOT NULL DEFAULT 'LOW',
    description   TEXT,
    CONSTRAINT pk_waste_category PRIMARY KEY (category_id),
    CONSTRAINT uq_category_name UNIQUE (category_name),
    CONSTRAINT chk_hazard_level CHECK (hazard_level IN ('LOW','MEDIUM','HIGH','BIOMEDICAL'))
) ENGINE=InnoDB;

-- ============================================================
-- 3. USER  (disjoint specialisation: GENERATOR has generator_id)
-- ============================================================
CREATE TABLE IF NOT EXISTS `USER` (
    user_id       BIGINT UNSIGNED AUTO_INCREMENT,
    username      VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(120) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    generator_id  BIGINT UNSIGNED,          -- populated iff role = 'GENERATOR'
    created_date  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user        PRIMARY KEY (user_id),
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT uq_user_email    UNIQUE (email),
    CONSTRAINT chk_user_role  CHECK (role IN ('GENERATOR','ADMIN','OPERATOR','DRIVER'))
) ENGINE=InnoDB;

-- ============================================================
-- 4. WASTE_GENERATOR  (generator entity; also specialised below)
-- ============================================================
CREATE TABLE IF NOT EXISTS WASTE_GENERATOR (
    generator_id    BIGINT UNSIGNED AUTO_INCREMENT,
    generator_name  VARCHAR(120) NOT NULL,
    generator_type  VARCHAR(40)  NOT NULL,
    address         TEXT         NOT NULL,
    zone_id         BIGINT UNSIGNED NOT NULL,
    contact_person  VARCHAR(120),
    phone           VARCHAR(20),
    email           VARCHAR(120),
    latitude        DOUBLE,
    longitude       DOUBLE,
    created_date    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_generator    PRIMARY KEY (generator_id),
    CONSTRAINT fk_gen_zone     FOREIGN KEY (zone_id)
        REFERENCES ZONE(zone_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_gen_type    CHECK (generator_type IN (
        'HOSPITAL','HOUSING_SOCIETY','FACTORY','HOTEL','SCHOOL','RESTAURANT','OFFICE','OTHER'))
) ENGINE=InnoDB;

-- Back-reference FK from USER to WASTE_GENERATOR
ALTER TABLE `USER`
    ADD CONSTRAINT fk_user_generator
        FOREIGN KEY (generator_id)
        REFERENCES WASTE_GENERATOR(generator_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- ============================================================
-- 5. VEHICLE
-- ============================================================
CREATE TABLE IF NOT EXISTS VEHICLE (
    vehicle_id       BIGINT UNSIGNED AUTO_INCREMENT,
    vehicle_number   VARCHAR(30)  NOT NULL,
    vehicle_type     VARCHAR(60),
    capacity_kg      DECIMAL(10,2) NOT NULL,
    assigned_zone_id BIGINT UNSIGNED,
    status           VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT pk_vehicle    PRIMARY KEY (vehicle_id),
    CONSTRAINT uq_vehicle_no UNIQUE (vehicle_number),
    CONSTRAINT fk_vehicle_zone FOREIGN KEY (assigned_zone_id)
        REFERENCES ZONE(zone_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_vehicle_status CHECK (status IN ('AVAILABLE','IN_ROUTE','MAINTENANCE'))
) ENGINE=InnoDB;

-- ============================================================
-- 6. PICKUP_REQUEST
-- ============================================================
CREATE TABLE IF NOT EXISTS PICKUP_REQUEST (
    request_id            BIGINT UNSIGNED AUTO_INCREMENT,
    generator_id          BIGINT UNSIGNED NOT NULL,
    request_date          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    preferred_pickup_date DATE,
    status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    notes                 TEXT,
    CONSTRAINT pk_pickup_request  PRIMARY KEY (request_id),
    CONSTRAINT fk_req_generator   FOREIGN KEY (generator_id)
        REFERENCES WASTE_GENERATOR(generator_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_req_status     CHECK (status IN ('PENDING','ASSIGNED','IN_ROUTE','COMPLETED','CANCELLED'))
) ENGINE=InnoDB;

-- ============================================================
-- 7. REQUEST_WASTE  (line items of a pickup request)
-- ============================================================
CREATE TABLE IF NOT EXISTS REQUEST_WASTE (
    request_waste_id         BIGINT UNSIGNED AUTO_INCREMENT,
    request_id               BIGINT UNSIGNED NOT NULL,
    category_id              BIGINT UNSIGNED NOT NULL,
    estimated_quantity_kg    DECIMAL(12,2)   NOT NULL,
    actual_quantity_kg       DECIMAL(12,2),
    CONSTRAINT pk_request_waste PRIMARY KEY (request_waste_id),
    CONSTRAINT fk_rw_request    FOREIGN KEY (request_id)
        REFERENCES PICKUP_REQUEST(request_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_rw_category   FOREIGN KEY (category_id)
        REFERENCES WASTE_CATEGORY(category_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 8. ROUTE
-- ============================================================
CREATE TABLE IF NOT EXISTS ROUTE (
    route_id     BIGINT UNSIGNED AUTO_INCREMENT,
    vehicle_id   BIGINT UNSIGNED NOT NULL,
    zone_id      BIGINT UNSIGNED NOT NULL,
    route_date   DATE            NOT NULL,
    status       VARCHAR(30)     NOT NULL DEFAULT 'PLANNED',
    created_date DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_route     PRIMARY KEY (route_id),
    CONSTRAINT fk_route_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_route_zone    FOREIGN KEY (zone_id)
        REFERENCES ZONE(zone_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_route_status CHECK (
        status IN ('PLANNED','IN_PROGRESS','DISPATCHED_TO_DISPOSAL','COMPLETED','CANCELLED'))
) ENGINE=InnoDB;

-- ============================================================
-- 9. ROUTE_STOP
-- ============================================================
CREATE TABLE IF NOT EXISTS ROUTE_STOP (
    stop_id       BIGINT UNSIGNED AUTO_INCREMENT,
    route_id      BIGINT UNSIGNED NOT NULL,
    request_id    BIGINT UNSIGNED NOT NULL,
    stop_sequence INT             NOT NULL,
    status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    notes         TEXT,
    CONSTRAINT pk_route_stop  PRIMARY KEY (stop_id),
    CONSTRAINT uq_stop_request  UNIQUE (request_id),
    CONSTRAINT uq_stop_sequence UNIQUE (route_id, stop_sequence),
    CONSTRAINT fk_stop_route    FOREIGN KEY (route_id)
        REFERENCES ROUTE(route_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_stop_request  FOREIGN KEY (request_id)
        REFERENCES PICKUP_REQUEST(request_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_stop_status  CHECK (status IN ('PENDING','COMPLETED','SKIPPED'))
) ENGINE=InnoDB;

-- ============================================================
-- 10. DISPOSAL_FACILITY  (replaces obsolete DISPOSAL_SITE)
-- ============================================================
CREATE TABLE IF NOT EXISTS DISPOSAL_FACILITY (
    facility_id             BIGINT UNSIGNED AUTO_INCREMENT,
    facility_name           VARCHAR(120) NOT NULL,
    facility_type           VARCHAR(40)  NOT NULL,
    address                 TEXT         NOT NULL,
    latitude                DOUBLE       NOT NULL,
    longitude               DOUBLE       NOT NULL,
    capacity_tonnes_per_day DECIMAL(10,2) NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    contact_info            VARCHAR(255),
    CONSTRAINT pk_disposal_facility PRIMARY KEY (facility_id),
    CONSTRAINT chk_facility_type CHECK (facility_type IN (
        'RECYCLING_PLANT','PROCESSING_PLANT','HAZARDOUS_DISPOSAL_SITE','LANDFILL')),
    CONSTRAINT chk_facility_status CHECK (status IN ('ACTIVE','INACTIVE','MAINTENANCE'))
) ENGINE=InnoDB;

-- ============================================================
-- 11. FACILITY_ACCEPTED_WASTE
-- ============================================================
CREATE TABLE IF NOT EXISTS FACILITY_ACCEPTED_WASTE (
    facility_id          BIGINT UNSIGNED NOT NULL,
    category_id          BIGINT UNSIGNED NOT NULL,
    processing_rate_per_kg DECIMAL(8,2)  NOT NULL DEFAULT 0.00,
    CONSTRAINT pk_faw PRIMARY KEY (facility_id, category_id),
    CONSTRAINT fk_faw_facility  FOREIGN KEY (facility_id)
        REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_faw_category  FOREIGN KEY (category_id)
        REFERENCES WASTE_CATEGORY(category_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 12. DISPOSAL_RECORD
-- ============================================================
CREATE TABLE IF NOT EXISTS DISPOSAL_RECORD (
    disposal_id              BIGINT UNSIGNED AUTO_INCREMENT,
    route_id                 BIGINT UNSIGNED NOT NULL,
    facility_id              BIGINT UNSIGNED NOT NULL,
    disposal_date            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gross_weight_kg          DECIMAL(10,2)   NOT NULL,
    tare_weight_kg           DECIMAL(10,2)   NOT NULL,
    net_disposed_weight_kg   DECIMAL(10,2)   GENERATED ALWAYS AS (gross_weight_kg - tare_weight_kg) STORED,
    receipt_number           VARCHAR(80),
    status                   VARCHAR(20)     NOT NULL DEFAULT 'VERIFIED',
    notes                    TEXT,
    CONSTRAINT pk_disposal_record  PRIMARY KEY (disposal_id),
    CONSTRAINT uq_receipt_number   UNIQUE (receipt_number),
    CONSTRAINT fk_dr_route         FOREIGN KEY (route_id)
        REFERENCES ROUTE(route_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_dr_facility      FOREIGN KEY (facility_id)
        REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_dr_status       CHECK (status IN ('VERIFIED','DISCREPANCY','REJECTED')),
    CONSTRAINT chk_gross_gte_tare  CHECK (gross_weight_kg >= tare_weight_kg)
) ENGINE=InnoDB;

-- ============================================================
-- 13-15. GENERATOR SUBTYPES  (disjoint partial specialisation)
-- ============================================================
CREATE TABLE IF NOT EXISTS HOSPITAL (
    generator_id         BIGINT UNSIGNED NOT NULL,
    license_number       VARCHAR(80)  NOT NULL,
    biomedical_auth_number VARCHAR(80) NOT NULL,
    CONSTRAINT pk_hospital    PRIMARY KEY (generator_id),
    CONSTRAINT fk_hosp_gen    FOREIGN KEY (generator_id)
        REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS HOUSING_SOCIETY (
    generator_id         BIGINT UNSIGNED NOT NULL,
    registration_number  VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_housing   PRIMARY KEY (generator_id),
    CONSTRAINT fk_hs_gen    FOREIGN KEY (generator_id)
        REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS FACTORY (
    generator_id          BIGINT UNSIGNED NOT NULL,
    pollution_consent_no  VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_factory   PRIMARY KEY (generator_id),
    CONSTRAINT fk_fac_gen   FOREIGN KEY (generator_id)
        REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- 16-18. FACILITY SUBTYPES  (disjoint partial specialisation)
-- ============================================================
CREATE TABLE IF NOT EXISTS RECYCLING_PLANT (
    facility_id          BIGINT UNSIGNED NOT NULL,
    recycling_license_no VARCHAR(80)  NOT NULL,
    CONSTRAINT pk_rp     PRIMARY KEY (facility_id),
    CONSTRAINT fk_rp_fac FOREIGN KEY (facility_id)
        REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS PROCESSING_PLANT (
    facility_id          BIGINT UNSIGNED NOT NULL,
    processing_type      VARCHAR(80),
    CONSTRAINT pk_pp     PRIMARY KEY (facility_id),
    CONSTRAINT fk_pp_fac FOREIGN KEY (facility_id)
        REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS HAZARDOUS_DISPOSAL_SITE (
    facility_id                 BIGINT UNSIGNED NOT NULL,
    environmental_clearance_no  VARCHAR(80) NOT NULL,
    CONSTRAINT pk_hds     PRIMARY KEY (facility_id),
    CONSTRAINT fk_hds_fac FOREIGN KEY (facility_id)
        REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;
