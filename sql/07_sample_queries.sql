-- ECOroute: Sample Queries / Reporting Query Library
-- These are reference queries; they are not executed during setup.

USE ecoroute_db;

-- 1. Pending requests per zone (for operator planning dashboard)
SELECT z.zone_name,
       COUNT(pr.request_id)          AS pending_requests,
       SUM(rw.estimated_quantity_kg) AS total_estimated_kg
FROM   V_PICKUP_REQUEST_ZONE pr
JOIN   ZONE z ON z.zone_id = pr.zone_id
JOIN   REQUEST_WASTE rw ON rw.request_id = pr.request_id
WHERE  pr.status = 'PENDING'
GROUP  BY z.zone_id, z.zone_name
ORDER  BY total_estimated_kg DESC;

-- 2. Vehicle utilisation: total weight per route for a given date
SELECT v.vehicle_number, vrw.route_date, vrw.status,
       vrw.total_estimated_kg, vrw.total_actual_kg,
       v.capacity_kg,
       ROUND(vrw.total_actual_kg / v.capacity_kg * 100, 1) AS utilisation_pct
FROM   V_ROUTE_WEIGHT vrw
JOIN   VEHICLE v ON v.vehicle_id = vrw.vehicle_id
WHERE  vrw.route_date = CURDATE()
ORDER  BY utilisation_pct DESC;

-- 3. Disposal throughput per facility, last 30 days
SELECT df.facility_name, df.facility_type,
       COUNT(dr.disposal_id)              AS disposal_count,
       SUM(dr.net_disposed_weight_kg)     AS total_net_kg,
       df.capacity_tonnes_per_day * 1000  AS daily_capacity_kg
FROM   DISPOSAL_FACILITY df
JOIN   DISPOSAL_RECORD dr ON dr.facility_id = df.facility_id
WHERE  dr.disposal_date >= NOW() - INTERVAL 30 DAY
  AND  dr.status = 'VERIFIED'
GROUP  BY df.facility_id, df.facility_name, df.facility_type, df.capacity_tonnes_per_day;

-- 4. Waste category breakdown for a given route
SELECT wc.category_name, wc.hazard_level,
       SUM(rw.estimated_quantity_kg) AS est_kg,
       SUM(rw.actual_quantity_kg)    AS act_kg
FROM   ROUTE_STOP rs
JOIN   REQUEST_WASTE rw ON rw.request_id = rs.request_id
JOIN   WASTE_CATEGORY wc ON wc.category_id = rw.category_id
WHERE  rs.route_id = ?   -- parameterised in application
GROUP  BY wc.category_id, wc.category_name, wc.hazard_level;

-- 5. Generator request history
SELECT pr.request_id, pr.request_date, pr.preferred_pickup_date, pr.status,
       SUM(rw.estimated_quantity_kg) AS total_est_kg
FROM   PICKUP_REQUEST pr
JOIN   REQUEST_WASTE rw ON rw.request_id = pr.request_id
WHERE  pr.generator_id = ?
GROUP  BY pr.request_id, pr.request_date, pr.preferred_pickup_date, pr.status
ORDER  BY pr.request_date DESC;
