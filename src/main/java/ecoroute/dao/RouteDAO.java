package ecoroute.dao;

import ecoroute.model.Route;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class RouteDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT route_id, vehicle_id, zone_id, route_date, status, created_date, site_id FROM `ROUTE`";
    public RouteDAO(Connection connection) { this.connection = connection; }
    private static Route map(ResultSet r) throws SQLException {
        return new Route(
                Jdbc.nullableLong(r, "route_id"),
                Jdbc.nullableLong(r, "vehicle_id"),
                Jdbc.nullableLong(r, "zone_id"),
                r.getObject("route_date", java.time.LocalDate.class),
                r.getString("status"),
                r.getObject("created_date", java.time.LocalDateTime.class),
                Jdbc.nullableLong(r, "site_id"));
    }

    public Optional<Route> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE route_id = ?", RouteDAO::map, id);
    }

    public Optional<Route> lockById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE route_id = ? FOR UPDATE", RouteDAO::map, id);
    }

    public long insert(Route value) {
        if (value == null || value.getRouteId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `ROUTE` (vehicle_id, zone_id, route_date, status, created_date, site_id) VALUES (?, ?, ?, ?, ?, ?)",
                value.getVehicleId(),
                value.getZoneId(),
                value.getRouteDate(),
                value.getStatus(),
                value.getCreatedDate(),
                value.getSiteId());
    }

    public void updateStatus(long id, String expected, String next) {
        Jdbc.change(connection, "UPDATE `ROUTE` SET status = ? WHERE route_id = ? AND status = ?", next, id, expected);
    }

    public List<Route> findByVehicle(long id) {
        return Jdbc.list(connection, SELECT + " WHERE vehicle_id = ? ORDER BY route_date, route_id", RouteDAO::map, id);
    }

    public List<Route> findByZone(long id) {
        return Jdbc.list(connection, SELECT + " WHERE zone_id = ? ORDER BY route_date, route_id", RouteDAO::map, id);
    }
}
