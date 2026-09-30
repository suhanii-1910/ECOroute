package ecoroute.dao;

import ecoroute.model.RouteStop;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class RouteStopDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT stop_id, route_id, request_id, stop_sequence, status, arrival_time, completion_time FROM `ROUTE_STOP`";
    public RouteStopDAO(Connection connection) { this.connection = connection; }
    private static RouteStop map(ResultSet r) throws SQLException {
        return new RouteStop(
                Jdbc.nullableLong(r, "stop_id"),
                Jdbc.nullableLong(r, "route_id"),
                Jdbc.nullableLong(r, "request_id"),
                r.getInt("stop_sequence"),
                r.getString("status"),
                r.getObject("arrival_time", java.time.LocalDateTime.class),
                r.getObject("completion_time", java.time.LocalDateTime.class));
    }

    public Optional<RouteStop> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE stop_id = ?", RouteStopDAO::map, id);
    }

    public Optional<RouteStop> lockById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE stop_id = ? FOR UPDATE", RouteStopDAO::map, id);
    }

    public long insert(RouteStop value) {
        if (value == null || value.getStopId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `ROUTE_STOP` (route_id, request_id, stop_sequence, status, arrival_time, completion_time) VALUES (?, ?, ?, ?, ?, ?)",
                value.getRouteId(),
                value.getRequestId(),
                value.getStopSequence(),
                value.getStatus(),
                value.getArrivalTime(),
                value.getCompletionTime());
    }

    public void updateStopStatus(long id, String expected, String next) {
        Jdbc.change(connection, "UPDATE `ROUTE_STOP` SET status = ? WHERE stop_id = ? AND status = ?", next, id, expected);
    }

    public List<RouteStop> findByRouteOrdered(long id) {
        return Jdbc.list(connection, SELECT + " WHERE route_id = ? ORDER BY stop_sequence", RouteStopDAO::map, id);
    }

    public Optional<RouteStop> findByRequest(long requestId) {
        return Jdbc.one(connection, SELECT + " WHERE request_id = ?", RouteStopDAO::map, requestId);
    }

    public void updateActualTiming(long id, java.time.LocalDateTime arrival, java.time.LocalDateTime completion) {
        Jdbc.change(
                connection,
                "UPDATE `ROUTE_STOP` SET arrival_time = ?, completion_time = ? WHERE stop_id = ?",
                arrival,
                completion,
                id);
    }
}
