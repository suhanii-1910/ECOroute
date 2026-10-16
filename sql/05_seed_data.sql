-- ECOroute: Seed Data
-- Representative data only — no plaintext passwords in history.
-- Password hashes below are PBKDF2-SHA256 of the string shown in comments.
-- Generate real hashes via PasswordHasher.hash() before production use.

USE ecoroute_db;

-- ============================================================
-- ZONES
-- ============================================================
INSERT IGNORE INTO ZONE (zone_name, description) VALUES
    ('North Zone',  'Northern residential and commercial area'),
    ('South Zone',  'Southern industrial belt'),
    ('East Zone',   'Eastern mixed-use corridor'),
    ('West Zone',   'Western hospital and institutional cluster'),
    ('Central Zone','City centre — high density');

-- ============================================================
-- WASTE CATEGORIES
-- ============================================================
INSERT IGNORE INTO WASTE_CATEGORY (category_name, hazard_level, description) VALUES
    ('General Waste',      'LOW',       'Everyday non-hazardous rubbish'),
    ('Organic Waste',      'LOW',       'Food scraps and garden waste'),
    ('Recyclable Waste',   'LOW',       'Paper, plastic, glass, metal'),
    ('Electronic Waste',   'MEDIUM',    'Discarded electronics'),
    ('Chemical Waste',     'HIGH',      'Industrial chemicals and solvents'),
    ('Biomedical Waste',   'BIOMEDICAL','Clinical and biomedical waste');

-- ============================================================
-- WASTE GENERATORS
-- ============================================================
INSERT IGNORE INTO WASTE_GENERATOR
    (generator_name, generator_type, address, zone_id, contact_person, phone, email, latitude, longitude)
VALUES
    ('City General Hospital',   'HOSPITAL',       '1 Hospital Road, North',    1, 'Dr. Patel',  '1000000001', 'hospital@city.gov',   28.7041, 77.1025),
    ('Green Valley Society',    'HOUSING_SOCIETY','12 Green Avenue, South',    2, 'Mr. Sharma', '1000000002', 'gvs@society.org',     28.6741, 77.2273),
    ('Prestige Industries Ltd', 'FACTORY',        '5 Industrial Area, East',   3, 'Ms. Rao',    '1000000003', 'prestige@factory.com',28.6448, 77.2167),
    ('Sunrise Hotel',           'HOTEL',          '88 MG Road, Central',       5, 'Mr. Mehta',  '1000000004', 'gm@sunrise.hotel',    28.6517, 77.2219),
    ('Delhi Public School',     'SCHOOL',         '7 Sector-12, West',         4, 'Principal',  '1000000005', 'dps@school.edu',      28.6692, 77.1543);

-- ============================================================
-- HOSPITAL subtype (generator_id = 1)
-- ============================================================
INSERT IGNORE INTO HOSPITAL (generator_id, license_number, biomedical_auth_number) VALUES
    (1, 'LIC-HOSP-2020-001', 'BIO-AUTH-2020-001');

-- ============================================================
-- HOUSING_SOCIETY subtype (generator_id = 2)
-- ============================================================
INSERT IGNORE INTO HOUSING_SOCIETY (generator_id, registration_number) VALUES
    (2, 'REG-HS-2021-002');

-- ============================================================
-- FACTORY subtype (generator_id = 3)
-- ============================================================
INSERT IGNORE INTO FACTORY (generator_id, pollution_consent_no) VALUES
    (3, 'PCN-FAC-2022-003');

-- ============================================================
-- USERS  (passwords must be re-hashed before production)
-- ============================================================
-- admin / Admin@123  (placeholder hash — replace before production)
INSERT IGNORE INTO `USER` (username, password_hash, email, full_name, role) VALUES
    ('admin',
     'pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
     'admin@ecoroute.local', 'System Administrator', 'ADMIN');

-- operator1 / Op@123
INSERT IGNORE INTO `USER` (username, password_hash, email, full_name, role) VALUES
    ('operator1',
     'pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
     'operator1@ecoroute.local', 'Meena Operator', 'OPERATOR');

-- driver1 / Dr@123
INSERT IGNORE INTO `USER` (username, password_hash, email, full_name, role) VALUES
    ('driver1',
     'pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
     'driver1@ecoroute.local', 'Ravi Driver', 'DRIVER');

-- hospital_gen / Gen@123  (attached to generator_id = 1)
INSERT IGNORE INTO `USER` (username, password_hash, email, full_name, role, generator_id) VALUES
    ('hospital_gen',
     'pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
     'gen@hospital.city.gov', 'Hospital Generator User', 'GENERATOR', 1);

-- ============================================================
-- VEHICLES
-- ============================================================
INSERT IGNORE INTO VEHICLE (vehicle_number, vehicle_type, capacity_kg, assigned_zone_id, status) VALUES
    ('DL-01-GA-0001', 'Compactor Truck',  2000.00, 1, 'AVAILABLE'),
    ('DL-02-GA-0002', 'Tipper Truck',     3000.00, 2, 'AVAILABLE'),
    ('DL-03-GA-0003', 'Mini Van',         1000.00, 3, 'AVAILABLE'),
    ('DL-04-GA-0004', 'Hazmat Truck',     1500.00, 4, 'AVAILABLE'),
    ('DL-05-GA-0005', 'Compactor Truck',  2500.00, 5, 'MAINTENANCE');

-- ============================================================
-- DISPOSAL FACILITIES
-- ============================================================
INSERT IGNORE INTO DISPOSAL_FACILITY
    (facility_name, facility_type, address, latitude, longitude, capacity_tonnes_per_day, status)
VALUES
    ('North Recycling Plant',         'RECYCLING_PLANT',         '10 Industrial Rd North', 28.7200, 77.0900,  50.00, 'ACTIVE'),
    ('South Processing Plant',        'PROCESSING_PLANT',        '30 MIDC South',          28.6200, 77.3000,  80.00, 'ACTIVE'),
    ('East Hazardous Disposal Site',  'HAZARDOUS_DISPOSAL_SITE', '15 Chemical Zone East',  28.6100, 77.4000,  30.00, 'ACTIVE'),
    ('West Landfill',                 'LANDFILL',                '99 Outskirts West',       28.6800, 76.9500, 200.00, 'ACTIVE');

-- Facility subtypes
INSERT IGNORE INTO RECYCLING_PLANT (facility_id, recycling_license_no) VALUES (1, 'RPLAN-LIC-2019-001');
INSERT IGNORE INTO PROCESSING_PLANT (facility_id, processing_type)    VALUES (2, 'Organic Composting');
INSERT IGNORE INTO HAZARDOUS_DISPOSAL_SITE (facility_id, environmental_clearance_no) VALUES (3, 'ENV-CLR-2021-003');

-- ============================================================
-- FACILITY_ACCEPTED_WASTE
-- ============================================================
-- North Recycling Plant: General, Organic, Recyclable, E-Waste
INSERT IGNORE INTO FACILITY_ACCEPTED_WASTE (facility_id, category_id, processing_rate_per_kg) VALUES
    (1, 1, 1.50),(1, 2, 1.00),(1, 3, 2.00),(1, 4, 3.50);
-- South Processing Plant: General, Organic
INSERT IGNORE INTO FACILITY_ACCEPTED_WASTE (facility_id, category_id, processing_rate_per_kg) VALUES
    (2, 1, 1.20),(2, 2, 0.80);
-- East Hazardous: Chemical, Biomedical
INSERT IGNORE INTO FACILITY_ACCEPTED_WASTE (facility_id, category_id, processing_rate_per_kg) VALUES
    (3, 5, 8.00),(3, 6, 12.00);
-- West Landfill: everything at low rate
INSERT IGNORE INTO FACILITY_ACCEPTED_WASTE (facility_id, category_id, processing_rate_per_kg) VALUES
    (4, 1, 0.50),(4, 2, 0.50),(4, 3, 0.50);

-- ============================================================
-- PICKUP REQUESTS (sample)
-- ============================================================
INSERT IGNORE INTO PICKUP_REQUEST (generator_id, preferred_pickup_date, status, notes) VALUES
    (1, CURDATE() + INTERVAL 1 DAY, 'PENDING', 'Regular weekly collection'),
    (2, CURDATE() + INTERVAL 1 DAY, 'PENDING', NULL),
    (3, CURDATE() + INTERVAL 2 DAY, 'PENDING', 'Large batch — factory clearance'),
    (4, CURDATE() + INTERVAL 1 DAY, 'PENDING', NULL),
    (5, CURDATE() + INTERVAL 3 DAY, 'PENDING', NULL);

-- REQUEST_WASTE line items
INSERT IGNORE INTO REQUEST_WASTE (request_id, category_id, estimated_quantity_kg) VALUES
    (1, 6, 120.00),  -- Hospital: biomedical
    (1, 1, 50.00),   -- Hospital: general
    (2, 1, 200.00),  -- Society: general
    (2, 2, 80.00),   -- Society: organic
    (3, 5, 300.00),  -- Factory: chemical
    (4, 1, 60.00),   -- Hotel: general
    (4, 2, 40.00),   -- Hotel: organic
    (5, 1, 90.00),   -- School: general
    (5, 3, 30.00);   -- School: recyclable
