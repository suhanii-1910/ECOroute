-- FINAL FREEZED STRUCTURE: MEMBER 2 TEST FIXTURE ONLY. Not Member 1 production DDL.
-- AUTO_INCREMENT, delete actions, status/check policies and route sequence uniqueness
-- are retained fixture assumptions pending Member 1 confirmation; see docs/database_contract.md.
-- MySQL 8.0.16+ / InnoDB; H2 MySQL mode is only a local test substitute.
CREATE TABLE ZONE (
 zone_id INT AUTO_INCREMENT PRIMARY KEY,
 zone_name VARCHAR(100) NOT NULL UNIQUE,
 description VARCHAR(255)
) ENGINE=InnoDB;
CREATE TABLE WASTE_GENERATOR (
 generator_id INT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(150) NOT NULL,
 generator_type VARCHAR(50) NOT NULL,
 contact_person VARCHAR(100), phone VARCHAR(20), email VARCHAR(150), address VARCHAR(255) NOT NULL,
 latitude DECIMAL(10,7), longitude DECIMAL(10,7), zone_id INT NOT NULL,
 is_active BOOLEAN NOT NULL DEFAULT TRUE, created_date DATETIME(6) NOT NULL,
 FOREIGN KEY (zone_id) REFERENCES ZONE(zone_id) ON DELETE RESTRICT,
 CHECK (generator_type IN ('HOSPITAL','HOUSING_SOCIETY','FACTORY','HOTEL','SCHOOL','RESTAURANT','OFFICE','OTHER'))
) ENGINE=InnoDB;
CREATE TABLE `USER` (
 user_id INT AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(100) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL,
 role VARCHAR(30) NOT NULL, generator_id INT,
 is_active BOOLEAN NOT NULL DEFAULT TRUE, created_date DATETIME(6) NOT NULL,
 FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE RESTRICT,
 CHECK ((role = 'GENERATOR' AND generator_id IS NOT NULL) OR (role IN ('ADMIN','OPERATOR') AND generator_id IS NULL))
) ENGINE=InnoDB;
-- Disjoint/total membership is verified by authentication and provisioning tests.
-- Cross-table total/disjoint enforcement requires Member 1's SQL/provisioning contract.
CREATE TABLE GENERATOR_USER (
 user_id INT PRIMARY KEY,
 FOREIGN KEY (user_id) REFERENCES `USER`(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE STAFF_USER (
 user_id INT PRIMARY KEY,
 FOREIGN KEY (user_id) REFERENCES `USER`(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE WASTE_CATEGORY (
 category_id INT AUTO_INCREMENT PRIMARY KEY,
 category_name VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(255), hazard_level VARCHAR(30) NOT NULL,
 CHECK (hazard_level IN ('LOW','MEDIUM','HIGH'))
) ENGINE=InnoDB;
CREATE TABLE VEHICLE (
 vehicle_id INT AUTO_INCREMENT PRIMARY KEY,
 vehicle_number VARCHAR(30) NOT NULL UNIQUE, capacity_kg DECIMAL(10,2) NOT NULL,
 status VARCHAR(30) NOT NULL, assigned_zone_id INT, created_date DATETIME(6) NOT NULL,
 FOREIGN KEY (assigned_zone_id) REFERENCES ZONE(zone_id) ON DELETE SET NULL,
 CHECK (capacity_kg > 0), CHECK (status IN ('AVAILABLE','IN_USE','MAINTENANCE'))
) ENGINE=InnoDB;
CREATE TABLE DISPOSAL_SITE (
 site_id INT AUTO_INCREMENT PRIMARY KEY,
 site_name VARCHAR(150) NOT NULL UNIQUE, site_type VARCHAR(50) NOT NULL,
 address VARCHAR(255) NOT NULL, latitude DECIMAL(10,7) NOT NULL, longitude DECIMAL(10,7) NOT NULL,
 capacity_kg DECIMAL(12,2) NOT NULL, status VARCHAR(30) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE ROUTE (
 route_id INT AUTO_INCREMENT PRIMARY KEY,
 vehicle_id INT NOT NULL, zone_id INT NOT NULL, site_id INT, route_date DATE NOT NULL,
 status VARCHAR(30) NOT NULL, created_date DATETIME(6) NOT NULL,
 FOREIGN KEY (site_id) REFERENCES DISPOSAL_SITE(site_id) ON DELETE RESTRICT,
 FOREIGN KEY (vehicle_id) REFERENCES VEHICLE(vehicle_id) ON DELETE RESTRICT,
 FOREIGN KEY (zone_id) REFERENCES ZONE(zone_id) ON DELETE RESTRICT,
 CHECK (status IN ('PLANNED','COMPLETED'))
) ENGINE=InnoDB;
CREATE TABLE PICKUP_REQUEST (
 request_id INT AUTO_INCREMENT PRIMARY KEY, generator_id INT NOT NULL,
 request_date DATETIME(6) NOT NULL, preferred_pickup_date DATE NOT NULL,
 status VARCHAR(30) NOT NULL, completion_date DATETIME(6), remarks VARCHAR(500),
 FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE RESTRICT,
 CHECK (status IN ('PENDING','ASSIGNED','COMPLETED','CANCELLED'))
) ENGINE=InnoDB;
CREATE TABLE ROUTE_STOP (
 stop_id INT AUTO_INCREMENT PRIMARY KEY, route_id INT NOT NULL,
 request_id INT NOT NULL UNIQUE, stop_sequence INT NOT NULL, status VARCHAR(30) NOT NULL,
 arrival_time DATETIME(6), completion_time DATETIME(6),
 UNIQUE (route_id, stop_sequence),
 FOREIGN KEY (route_id) REFERENCES ROUTE(route_id) ON DELETE CASCADE,
 FOREIGN KEY (request_id) REFERENCES PICKUP_REQUEST(request_id) ON DELETE RESTRICT,
 CHECK (stop_sequence > 0), CHECK (status IN ('PENDING','COMPLETED'))
) ENGINE=InnoDB;
CREATE TABLE REQUEST_WASTE (
 request_id INT NOT NULL, category_id INT NOT NULL,
 estimated_quantity DECIMAL(12,2) NOT NULL, actual_quantity DECIMAL(12,2),
 PRIMARY KEY (request_id, category_id),
 FOREIGN KEY (request_id) REFERENCES PICKUP_REQUEST(request_id) ON DELETE CASCADE,
 FOREIGN KEY (category_id) REFERENCES WASTE_CATEGORY(category_id) ON DELETE RESTRICT,
 CHECK (estimated_quantity > 0), CHECK (actual_quantity IS NULL OR actual_quantity >= 0)
) ENGINE=InnoDB;
CREATE TABLE HOSPITAL (
 generator_id INT PRIMARY KEY, license_number VARCHAR(100),
 biomedical_auth_number VARCHAR(100), auth_expiry_date DATE,
 FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE HOUSING_SOCIETY (
 generator_id INT PRIMARY KEY, registration_number VARCHAR(100), number_of_flats INT,
 FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE,
 CHECK (number_of_flats > 0)
) ENGINE=InnoDB;
CREATE TABLE FACTORY (
 generator_id INT PRIMARY KEY, industry_type VARCHAR(100),
 pollution_consent_no VARCHAR(100), consent_expiry_date DATE,
 FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE
) ENGINE=InnoDB;
