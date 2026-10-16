-- H2 (MySQL mode) fixture for unit/smoke tests.
-- Matches the final 18-table schema. No plaintext passwords.

CREATE TABLE IF NOT EXISTS ZONE (
  zone_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  zone_name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  created_date DATETIME NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS WASTE_CATEGORY (
  category_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_name VARCHAR(100) NOT NULL UNIQUE,
  hazard_level  VARCHAR(20)  NOT NULL DEFAULT 'LOW',
  description   TEXT,
  CHECK (hazard_level IN ('LOW','MEDIUM','HIGH','BIOMEDICAL'))
);

CREATE TABLE IF NOT EXISTS WASTE_GENERATOR (
  generator_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  generator_name VARCHAR(120) NOT NULL,
  generator_type VARCHAR(40)  NOT NULL,
  address        TEXT         NOT NULL,
  zone_id        BIGINT       NOT NULL,
  contact_person VARCHAR(120),
  phone          VARCHAR(20),
  email          VARCHAR(120),
  latitude       DOUBLE,
  longitude      DOUBLE,
  is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
  created_date   DATETIME     NOT NULL DEFAULT NOW(),
  FOREIGN KEY (zone_id) REFERENCES ZONE(zone_id),
  CHECK (generator_type IN ('HOSPITAL','HOUSING_SOCIETY','FACTORY','HOTEL','SCHOOL','RESTAURANT','OFFICE','OTHER'))
);

CREATE TABLE IF NOT EXISTS `USER` (
  user_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(80)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email         VARCHAR(120),
  full_name     VARCHAR(120),
  role          VARCHAR(20)  NOT NULL,
  generator_id  BIGINT,
  is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
  created_date  DATETIME     NOT NULL DEFAULT NOW(),
  FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id),
  CHECK (role IN ('GENERATOR','ADMIN','OPERATOR','DRIVER'))
);

CREATE TABLE IF NOT EXISTS VEHICLE (
  vehicle_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  vehicle_number   VARCHAR(30)   NOT NULL UNIQUE,
  vehicle_type     VARCHAR(60),
  capacity_kg      DECIMAL(10,2) NOT NULL,
  assigned_zone_id BIGINT,
  status           VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
  FOREIGN KEY (assigned_zone_id) REFERENCES ZONE(zone_id),
  CHECK (status IN ('AVAILABLE','IN_ROUTE','MAINTENANCE'))
);

CREATE TABLE IF NOT EXISTS PICKUP_REQUEST (
  request_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  generator_id          BIGINT   NOT NULL,
  request_date          DATETIME NOT NULL DEFAULT NOW(),
  preferred_pickup_date DATE NOT NULL,
  status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  notes                 TEXT,
  completion_date       DATETIME,
  FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id),
  CHECK (status IN ('PENDING','ASSIGNED','IN_ROUTE','COMPLETED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS REQUEST_WASTE (
  request_waste_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id            BIGINT        NOT NULL,
  category_id           BIGINT        NOT NULL,
  estimated_quantity_kg DECIMAL(12,2) NOT NULL,
  actual_quantity_kg    DECIMAL(12,2),
  UNIQUE (request_id, category_id),
  FOREIGN KEY (request_id)  REFERENCES PICKUP_REQUEST(request_id) ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES WASTE_CATEGORY(category_id)
);

CREATE TABLE IF NOT EXISTS ROUTE (
  route_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  vehicle_id   BIGINT  NOT NULL,
  zone_id      BIGINT  NOT NULL,
  route_date   DATE    NOT NULL,
  status       VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
  created_date DATETIME   NOT NULL DEFAULT NOW(),
  FOREIGN KEY (vehicle_id) REFERENCES VEHICLE(vehicle_id),
  FOREIGN KEY (zone_id)    REFERENCES ZONE(zone_id),
  CHECK (status IN ('PLANNED','IN_PROGRESS','DISPATCHED_TO_DISPOSAL','COMPLETED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS ROUTE_STOP (
  stop_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_id      BIGINT  NOT NULL,
  request_id    BIGINT  NOT NULL UNIQUE,
  stop_sequence INT     NOT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  arrival_time    DATETIME,
  completion_time DATETIME,
  notes           TEXT,
  UNIQUE (route_id, stop_sequence),
  FOREIGN KEY (route_id)   REFERENCES ROUTE(route_id) ON DELETE CASCADE,
  FOREIGN KEY (request_id) REFERENCES PICKUP_REQUEST(request_id),
  CHECK (status IN ('PENDING','COMPLETED','SKIPPED'))
);

CREATE TABLE IF NOT EXISTS DISPOSAL_FACILITY (
  facility_id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  facility_name           VARCHAR(120)  NOT NULL,
  facility_type           VARCHAR(40)   NOT NULL,
  address                 TEXT          NOT NULL,
  latitude                DOUBLE        NOT NULL,
  longitude               DOUBLE        NOT NULL,
  capacity_tonnes_per_day DECIMAL(10,2) NOT NULL,
  status                  VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
  contact_info            VARCHAR(255),
  CHECK (facility_type IN ('RECYCLING_PLANT','PROCESSING_PLANT','HAZARDOUS_DISPOSAL_SITE','LANDFILL')),
  CHECK (status IN ('ACTIVE','INACTIVE','MAINTENANCE'))
);

CREATE TABLE IF NOT EXISTS FACILITY_ACCEPTED_WASTE (
  facility_id           BIGINT        NOT NULL,
  category_id           BIGINT        NOT NULL,
  processing_rate_per_kg DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (facility_id, category_id),
  FOREIGN KEY (facility_id) REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE,
  FOREIGN KEY (category_id) REFERENCES WASTE_CATEGORY(category_id)
);

-- H2 does not support GENERATED ALWAYS AS STORED, so net is a plain column for tests
CREATE TABLE IF NOT EXISTS DISPOSAL_RECORD (
  disposal_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  route_id               BIGINT        NOT NULL,
  facility_id            BIGINT        NOT NULL,
  disposal_date          DATETIME      NOT NULL DEFAULT NOW(),
  gross_weight_kg        DECIMAL(10,2) NOT NULL,
  tare_weight_kg         DECIMAL(10,2) NOT NULL,
  net_disposed_weight_kg DECIMAL(10,2),
  receipt_number         VARCHAR(80) UNIQUE,
  status                 VARCHAR(20)   NOT NULL DEFAULT 'VERIFIED',
  notes                  TEXT,
  FOREIGN KEY (route_id)    REFERENCES ROUTE(route_id),
  FOREIGN KEY (facility_id) REFERENCES DISPOSAL_FACILITY(facility_id),
  CHECK (status IN ('VERIFIED','DISCREPANCY','REJECTED'))
);

CREATE TABLE IF NOT EXISTS HOSPITAL (
  generator_id            BIGINT PRIMARY KEY,
  license_number          VARCHAR(80),
  biomedical_auth_number  VARCHAR(80),
  FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS HOUSING_SOCIETY (
  generator_id        BIGINT PRIMARY KEY,
  registration_number VARCHAR(80),
  FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS FACTORY (
  generator_id         BIGINT PRIMARY KEY,
  pollution_consent_no VARCHAR(80),
  FOREIGN KEY (generator_id) REFERENCES WASTE_GENERATOR(generator_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS RECYCLING_PLANT (
  facility_id          BIGINT PRIMARY KEY,
  recycling_license_no VARCHAR(80) NOT NULL,
  FOREIGN KEY (facility_id) REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS PROCESSING_PLANT (
  facility_id     BIGINT PRIMARY KEY,
  processing_type VARCHAR(80),
  FOREIGN KEY (facility_id) REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS HAZARDOUS_DISPOSAL_SITE (
  facility_id                BIGINT PRIMARY KEY,
  environmental_clearance_no VARCHAR(80) NOT NULL,
  FOREIGN KEY (facility_id) REFERENCES DISPOSAL_FACILITY(facility_id) ON DELETE CASCADE
);
