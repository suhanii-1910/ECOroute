-- ECOroute: Stored Procedures and Functions
-- Run after 03_create_views.sql

USE ecoroute_db;
DELIMITER $$

-- ============================================================
-- sp_record_disposal
-- Validates that the facility is ACTIVE, accepts every waste
-- category present on the route, has sufficient daily capacity,
-- then inserts a DISPOSAL_RECORD and marks the ROUTE as COMPLETED.
--
-- All capacity arithmetic is in kg (capacity_tonnes_per_day × 1000).
-- Uses SELECT … FOR UPDATE on DISPOSAL_FACILITY to serialize
-- concurrent calls for the same facility.
-- ============================================================
DROP PROCEDURE IF EXISTS sp_record_disposal$$
CREATE PROCEDURE sp_record_disposal(
    IN  p_route_id       BIGINT UNSIGNED,
    IN  p_facility_id    BIGINT UNSIGNED,
    IN  p_gross_weight   DECIMAL(10,2),
    IN  p_tare_weight    DECIMAL(10,2),
    IN  p_receipt_number VARCHAR(80)
)
BEGIN
    DECLARE v_facility_status  VARCHAR(20);
    DECLARE v_capacity_kg      DECIMAL(14,2);
    DECLARE v_used_kg          DECIMAL(14,2);
    DECLARE v_route_status     VARCHAR(30);
    DECLARE v_applicable_cats  INT;
    DECLARE v_remaining_cats   INT;

    -- Validate weights
    IF p_gross_weight < p_tare_weight THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'gross_weight_kg must be >= tare_weight_kg';
    END IF;

    -- Lock facility row for this transaction to prevent double-booking
    SELECT status, capacity_tonnes_per_day * 1000
    INTO   v_facility_status, v_capacity_kg
    FROM   DISPOSAL_FACILITY
    WHERE  facility_id = p_facility_id
    FOR UPDATE;

    IF v_facility_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Disposal facility not found';
    END IF;

    IF v_facility_status <> 'ACTIVE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Disposal facility is not ACTIVE';
    END IF;

    -- Validate route exists and is in a dispatchable state
    SELECT status INTO v_route_status FROM ROUTE WHERE route_id = p_route_id FOR UPDATE;
    IF v_route_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Route not found';
    END IF;
    IF v_route_status NOT IN ('IN_PROGRESS','DISPATCHED_TO_DISPOSAL') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Route is not in a dispatchable state';
    END IF;

    -- Check facility accepts at least one remaining (un-disposed) category for this route
    SELECT COUNT(DISTINCT rw.category_id)
    INTO   v_applicable_cats
    FROM   ROUTE_STOP rs
    JOIN   REQUEST_WASTE rw            ON rw.request_id = rs.request_id
    JOIN   FACILITY_ACCEPTED_WASTE faw ON faw.facility_id = p_facility_id
                                      AND faw.category_id = rw.category_id
    WHERE  rs.route_id = p_route_id
      AND  rw.category_id NOT IN (
          SELECT DISTINCT faw_prev.category_id
          FROM   DISPOSAL_RECORD dr
          JOIN   FACILITY_ACCEPTED_WASTE faw_prev ON faw_prev.facility_id = dr.facility_id
          WHERE  dr.route_id = p_route_id
            AND  dr.status = 'VERIFIED'
      );

    IF v_applicable_cats = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Facility does not accept any remaining waste categories on this route';
    END IF;

    -- Capacity check: sum existing disposal records for today at this facility
    SELECT COALESCE(SUM(net_disposed_weight_kg), 0)
    INTO   v_used_kg
    FROM   DISPOSAL_RECORD
    WHERE  facility_id = p_facility_id
      AND  DATE(disposal_date) = CURDATE()
      AND  status <> 'REJECTED';

    IF v_used_kg + (p_gross_weight - p_tare_weight) > v_capacity_kg THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Disposal facility daily capacity exceeded';
    END IF;

    -- Insert the disposal record
    INSERT INTO DISPOSAL_RECORD
        (route_id, facility_id, disposal_date, gross_weight_kg, tare_weight_kg, receipt_number, status)
    VALUES
        (p_route_id, p_facility_id, NOW(), p_gross_weight, p_tare_weight, p_receipt_number, 'VERIFIED');

    -- Check if all categories on the route are now disposed
    SELECT COUNT(DISTINCT rw.category_id)
    INTO   v_remaining_cats
    FROM   ROUTE_STOP rs
    JOIN   REQUEST_WASTE rw ON rw.request_id = rs.request_id
    WHERE  rs.route_id = p_route_id
      AND  rw.category_id NOT IN (
          SELECT DISTINCT faw_done.category_id
          FROM   DISPOSAL_RECORD dr
          JOIN   FACILITY_ACCEPTED_WASTE faw_done ON faw_done.facility_id = dr.facility_id
          WHERE  dr.route_id = p_route_id
            AND  dr.status = 'VERIFIED'
      );

    -- If no undisposed categories remain on this route, mark route as COMPLETED
    IF v_remaining_cats = 0 THEN
        UPDATE ROUTE SET status = 'COMPLETED' WHERE route_id = p_route_id;
    END IF;
END$$

-- ============================================================
-- fn_route_total_weight: convenience scalar function
-- ============================================================
DROP FUNCTION IF EXISTS fn_route_total_weight$$
CREATE FUNCTION fn_route_total_weight(p_route_id BIGINT UNSIGNED)
RETURNS DECIMAL(14,2)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_total DECIMAL(14,2);
    SELECT COALESCE(SUM(rw.actual_quantity_kg), SUM(rw.estimated_quantity_kg), 0)
    INTO   v_total
    FROM   ROUTE_STOP rs
    JOIN   REQUEST_WASTE rw ON rw.request_id = rs.request_id
    WHERE  rs.route_id = p_route_id;
    RETURN v_total;
END$$

DELIMITER ;
