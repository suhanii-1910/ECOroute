package ecoroute.dao;

import ecoroute.model.PickupRequest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class RequestDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT request_id, generator_id, request_date, preferred_pickup_date, status, completion_date, remarks FROM `PICKUP_REQUEST`";
    public RequestDAO(Connection connection) { this.connection = connection; }
    private static PickupRequest map(ResultSet r) throws SQLException {
        return new PickupRequest(
                Jdbc.nullableLong(r, "request_id"),
                Jdbc.nullableLong(r, "generator_id"),
                r.getObject("request_date", java.time.LocalDateTime.class),
                r.getObject("preferred_pickup_date", java.time.LocalDate.class),
                r.getString("status"),
                r.getObject("completion_date", java.time.LocalDateTime.class),
                r.getString("remarks"));
    }

    public Optional<PickupRequest> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE request_id = ?", RequestDAO::map, id);
    }

    public Optional<PickupRequest> lockById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE request_id = ? FOR UPDATE", RequestDAO::map, id);
    }

    public long insert(PickupRequest value) {
        if (value == null || value.getRequestId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        if (value.getPreferredPickupDate() == null) {
            throw new ecoroute.exception.InvalidRequestException("Preferred pickup date is required.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `PICKUP_REQUEST` (generator_id, request_date, preferred_pickup_date, status, completion_date, remarks) VALUES (?, ?, ?, ?, ?, ?)",
                value.getGeneratorId(),
                value.getRequestDate(),
                value.getPreferredPickupDate(),
                value.getStatus(),
                value.getCompletionDate(),
                value.getRemarks());
    }

    public void updateStatus(long id, String expected, String next) {
        Jdbc.change(connection, "UPDATE `PICKUP_REQUEST` SET status = ? WHERE request_id = ? AND status = ?", next, id, expected);
    }

    public List<PickupRequest> findPending() {
        return Jdbc.list(connection, SELECT + " WHERE status = ? ORDER BY request_id", RequestDAO::map, ecoroute.db.DatabaseContract.PENDING);
    }

    public List<PickupRequest> findByGenerator(long id) {
        return Jdbc.list(connection, SELECT + " WHERE generator_id = ? ORDER BY request_id", RequestDAO::map, id);
    }

    public void cancel(long id) {
        updateStatus(id, ecoroute.db.DatabaseContract.PENDING, ecoroute.db.DatabaseContract.CANCELLED);
    }

    public void complete(long id, java.time.LocalDateTime completedAt) {
        Jdbc.change(
                connection,
                "UPDATE `PICKUP_REQUEST` SET status = ?, completion_date = ? WHERE request_id = ? AND status = ?",
                ecoroute.db.DatabaseContract.COMPLETED,
                completedAt,
                id,
                ecoroute.db.DatabaseContract.ASSIGNED);
    }
}
