package ecoroute.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

/** Quantities are kg. Date intervals include from and exclude to. SQL aggregates each waste line once. */
public final class ReportDAO {
    public record CollectionVolume(long categoryId, String categoryName, BigDecimal actualKg, long measuredLines) {}
    public record RequestStatus(String status, long requestCount) {}
    public record RouteWeight(long routeId, BigDecimal estimatedKg, BigDecimal actualKg, long measuredLines, long totalLines) {}
    public record VehicleUtilization(long routeId, long vehicleId, BigDecimal capacityKg,
                                     BigDecimal estimatedRatio, BigDecimal actualRatio) {}
    public record QuantityComparison(long categoryId, String categoryName, BigDecimal estimatedKg,
                                     BigDecimal actualKg, long measuredLines, long totalLines) {}
    private final Connection connection;
    public ReportDAO(Connection connection) { this.connection = connection; }

    public List<CollectionVolume> collectionVolume(LocalDate from, LocalDate to) {
        return Jdbc.list(connection, """
                SELECT w.category_id, c.category_name, SUM(w.actual_quantity) AS actual_kg,
                       COUNT(w.actual_quantity) AS measured_lines
                FROM REQUEST_WASTE w
                JOIN PICKUP_REQUEST p ON p.request_id = w.request_id
                JOIN WASTE_CATEGORY c ON c.category_id = w.category_id
                WHERE p.status = ? AND p.completion_date >= ? AND p.completion_date < ?
                GROUP BY w.category_id, c.category_name ORDER BY w.category_id
                """, r -> new CollectionVolume(r.getLong("category_id"), r.getString("category_name"),
                r.getBigDecimal("actual_kg"), r.getLong("measured_lines")),
                ecoroute.db.DatabaseContract.COMPLETED, from.atStartOfDay(), to.atStartOfDay());
    }

    public List<RequestStatus> requestStatuses(LocalDate from, LocalDate to) {
        return Jdbc.list(connection, """
                SELECT status, COUNT(*) AS request_count FROM PICKUP_REQUEST
                WHERE request_date >= ? AND request_date < ? GROUP BY status ORDER BY status
                """, r -> new RequestStatus(r.getString("status"), r.getLong("request_count")),
                from.atStartOfDay(), to.atStartOfDay());
    }

    public List<RouteWeight> routeWeights(LocalDate from, LocalDate to) {
        return Jdbc.list(connection, """
                SELECT r.route_id, COALESCE(SUM(w.estimated_quantity), 0) AS estimated_kg,
                       SUM(w.actual_quantity) AS actual_kg, COUNT(w.actual_quantity) AS measured_lines,
                       COUNT(w.category_id) AS total_lines
                FROM ROUTE r LEFT JOIN ROUTE_STOP s ON s.route_id = r.route_id
                LEFT JOIN REQUEST_WASTE w ON w.request_id = s.request_id
                WHERE r.route_date >= ? AND r.route_date < ?
                GROUP BY r.route_id ORDER BY r.route_id
                """, r -> new RouteWeight(r.getLong("route_id"), r.getBigDecimal("estimated_kg"),
                r.getBigDecimal("actual_kg"), r.getLong("measured_lines"), r.getLong("total_lines")), from, to);
    }

    /** Denominator is one vehicle capacity per route; ratios are not percentages or time utilization. */
    public List<VehicleUtilization> vehicleUtilization(LocalDate from, LocalDate to) {
        return Jdbc.list(connection, """
                SELECT r.route_id, r.vehicle_id, v.capacity_kg,
                       COALESCE(SUM(w.estimated_quantity), 0) / NULLIF(v.capacity_kg, 0) AS estimated_ratio,
                       SUM(w.actual_quantity) / NULLIF(v.capacity_kg, 0) AS actual_ratio
                FROM ROUTE r JOIN VEHICLE v ON v.vehicle_id = r.vehicle_id
                LEFT JOIN ROUTE_STOP s ON s.route_id = r.route_id
                LEFT JOIN REQUEST_WASTE w ON w.request_id = s.request_id
                WHERE r.route_date >= ? AND r.route_date < ?
                GROUP BY r.route_id, r.vehicle_id, v.capacity_kg ORDER BY r.route_id
                """, r -> new VehicleUtilization(r.getLong("route_id"), r.getLong("vehicle_id"), r.getBigDecimal("capacity_kg"),
                r.getBigDecimal("estimated_ratio"), r.getBigDecimal("actual_ratio")), from, to);
    }

    public List<QuantityComparison> estimatedVersusActual(LocalDate from, LocalDate to) {
        return Jdbc.list(connection, """
                SELECT w.category_id, c.category_name, SUM(w.estimated_quantity) AS estimated_kg,
                       SUM(w.actual_quantity) AS actual_kg, COUNT(w.actual_quantity) AS measured_lines,
                       COUNT(*) AS total_lines
                FROM REQUEST_WASTE w JOIN PICKUP_REQUEST p ON p.request_id = w.request_id
                JOIN WASTE_CATEGORY c ON c.category_id = w.category_id
                WHERE p.request_date >= ? AND p.request_date < ?
                GROUP BY w.category_id, c.category_name ORDER BY w.category_id
                """, r -> new QuantityComparison(r.getLong("category_id"), r.getString("category_name"),
                r.getBigDecimal("estimated_kg"), r.getBigDecimal("actual_kg"),
                r.getLong("measured_lines"), r.getLong("total_lines")), from.atStartOfDay(), to.atStartOfDay());
    }
}
