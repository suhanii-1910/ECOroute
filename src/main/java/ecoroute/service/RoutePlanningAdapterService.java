package ecoroute.service;

import ecoroute.db.Database;
import ecoroute.exception.DatabaseOperationException;
import ecoroute.planning.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service adapter that connects database persistence with Member 3's pure
 * planning algorithms (ZoneGrouper, VehicleAssignment, RouteStrategy, RoutePlanningService).
 *
 * Fetches pending requests with generator coordinates and summed waste quantities,
 * fetches available vehicles not currently committed to active routes, calls
 * RoutePlanningService.planDay(...) or planZone(...), and provides transactional
 * persistence via RoutePersistenceService.
 */
public final class RoutePlanningAdapterService {

    private final Database database;
    private final RoutePlanningService planningService;
    private final RoutePersistenceService persistenceService;

    public RoutePlanningAdapterService(Database database) {
        this(database, new RoutePlanningService(), new RoutePersistenceService(database));
    }

    public RoutePlanningAdapterService(Database database,
                                       RoutePlanningService planningService,
                                       RoutePersistenceService persistenceService) {
        this.database = database;
        this.planningService = planningService;
        this.persistenceService = persistenceService;
    }

    /**
     * Previews a route plan for a given day and optional zone without modifying database state.
     */
    public DayPlan previewPlan(UserSession session, LocalDate routeDate, Long zoneId) {
        Validation.require(routeDate != null, "Route date is required.");
        return database.read(c -> {
            Access.operatorOrAdmin(c, session);
            List<RequestView> requests = fetchPendingRequests(c, zoneId);
            List<VehicleView> vehicles = fetchAvailableVehicles(c, zoneId);

            if (zoneId != null) {
                return planningService.planZone(zoneId, requests, vehicles, routeDate);
            } else {
                return planningService.planDay(requests, vehicles, routeDate);
            }
        });
    }

    /**
     * Computes a route plan and persists all feasible routes atomically.
     * Returns the list of generated route IDs.
     */
    public List<Long> planAndSave(UserSession session, LocalDate routeDate, Long zoneId) {
        DayPlan plan = previewPlan(session, routeDate, zoneId);
        return savePlan(session, plan);
    }

    /**
     * Persists all routes contained in an already computed DayPlan.
     */
    public List<Long> savePlan(UserSession session, DayPlan plan) {
        Validation.require(plan != null, "DayPlan cannot be null.");
        List<Long> createdRouteIds = new ArrayList<>();
        for (RoutePlan route : plan.getRoutes()) {
            List<RoutePersistenceService.PlannedStop> stops = new ArrayList<>();
            for (StopPlan stop : route.getStops()) {
                stops.add(new RoutePersistenceService.PlannedStop(stop.getRequestId(), stop.getStopSequence()));
            }
            long routeId = persistenceService.save(
                    session,
                    route.getVehicleId(),
                    route.getZoneId(),
                    plan.getRouteDate(),
                    stops
            );
            createdRouteIds.add(routeId);
        }
        return createdRouteIds;
    }

    private List<RequestView> fetchPendingRequests(Connection connection, Long zoneId) {
        StringBuilder sql = new StringBuilder(
                "SELECT pr.request_id, pr.generator_id, wg.generator_name, wg.zone_id, " +
                "       wg.latitude, wg.longitude, pr.status, pr.request_date, pr.preferred_pickup_date, " +
                "       COALESCE(SUM(rw.estimated_quantity_kg), 0) AS total_kg " +
                "FROM PICKUP_REQUEST pr " +
                "JOIN WASTE_GENERATOR wg ON wg.generator_id = pr.generator_id " +
                "LEFT JOIN REQUEST_WASTE rw ON rw.request_id = pr.request_id " +
                "WHERE pr.status = 'PENDING' AND wg.is_active = TRUE "
        );
        if (zoneId != null) {
            sql.append("AND wg.zone_id = ? ");
        }
        sql.append("GROUP BY pr.request_id, pr.generator_id, wg.generator_name, wg.zone_id, " +
                   "         wg.latitude, wg.longitude, pr.status, pr.request_date, pr.preferred_pickup_date " +
                   "ORDER BY pr.request_id");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            if (zoneId != null) {
                ps.setLong(1, zoneId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<RequestView> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapRequestView(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DatabaseOperationException("Failed to fetch pending requests for planning", e);
        }
    }

    private static RequestView mapRequestView(ResultSet r) throws SQLException {
        Double lat = r.getObject("latitude") != null ? r.getDouble("latitude") : null;
        Double lon = r.getObject("longitude") != null ? r.getDouble("longitude") : null;
        java.sql.Timestamp reqTs = r.getTimestamp("request_date");
        LocalDate reqDate = reqTs != null ? reqTs.toLocalDateTime().toLocalDate() : LocalDate.now();
        java.sql.Date prefDate = r.getDate("preferred_pickup_date");
        LocalDate preferredPickupDate = prefDate != null ? prefDate.toLocalDate() : reqDate;
        BigDecimal totalKg = r.getBigDecimal("total_kg");

        return new RequestView(
                r.getLong("request_id"),
                r.getLong("generator_id"),
                r.getString("generator_name"),
                r.getLong("zone_id"),
                lat,
                lon,
                totalKg != null ? totalKg : BigDecimal.ZERO,
                r.getString("status"),
                reqDate,
                preferredPickupDate
        );
    }

    private List<VehicleView> fetchAvailableVehicles(Connection connection, Long zoneId) {
        StringBuilder sql = new StringBuilder(
                "SELECT v.vehicle_id, v.vehicle_number, v.capacity_kg, v.status, v.assigned_zone_id " +
                "FROM VEHICLE v " +
                "WHERE v.status = 'AVAILABLE' " +
                "  AND NOT EXISTS ( " +
                "      SELECT 1 FROM `ROUTE` r " +
                "      WHERE r.vehicle_id = v.vehicle_id AND r.status IN ('PLANNED', 'IN_PROGRESS') " +
                "  ) "
        );
        if (zoneId != null) {
            sql.append("AND (v.assigned_zone_id IS NULL OR v.assigned_zone_id = ?) ");
        }
        sql.append("ORDER BY v.capacity_kg DESC, v.vehicle_id ASC");

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            if (zoneId != null) {
                ps.setLong(1, zoneId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<VehicleView> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapVehicleView(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DatabaseOperationException("Failed to fetch available vehicles for planning", e);
        }
    }

    private static VehicleView mapVehicleView(ResultSet r) throws SQLException {
        Long homeZone = r.getObject("assigned_zone_id") != null ? r.getLong("assigned_zone_id") : null;
        return new VehicleView(
                r.getLong("vehicle_id"),
                r.getString("vehicle_number"),
                r.getBigDecimal("capacity_kg"),
                r.getString("status"),
                homeZone
        );
    }
}
