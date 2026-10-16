-- ECOroute: Create Views
-- Run after 02_create_tables.sql

USE ecoroute_db;

-- V_ROUTE_WEIGHT: total estimated and actual weight per route, across all stops
CREATE OR REPLACE VIEW V_ROUTE_WEIGHT AS
SELECT
    r.route_id,
    r.vehicle_id,
    r.zone_id,
    r.route_date,
    r.status,
    SUM(rw.estimated_quantity_kg) AS total_estimated_kg,
    SUM(rw.actual_quantity_kg)    AS total_actual_kg
FROM ROUTE r
JOIN ROUTE_STOP rs ON rs.route_id = r.route_id
JOIN REQUEST_WASTE rw ON rw.request_id = rs.request_id
GROUP BY r.route_id, r.vehicle_id, r.zone_id, r.route_date, r.status;

-- V_PICKUP_REQUEST_ZONE: derive zone from the generator attached to the request
CREATE OR REPLACE VIEW V_PICKUP_REQUEST_ZONE AS
SELECT
    pr.request_id,
    pr.generator_id,
    wg.zone_id,
    z.zone_name,
    pr.request_date,
    pr.preferred_pickup_date,
    pr.status,
    wg.latitude,
    wg.longitude
FROM PICKUP_REQUEST pr
JOIN WASTE_GENERATOR wg ON wg.generator_id = pr.generator_id
JOIN ZONE z             ON z.zone_id = wg.zone_id;
