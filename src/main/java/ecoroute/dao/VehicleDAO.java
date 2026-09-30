package ecoroute.dao;

import ecoroute.model.Vehicle;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class VehicleDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT vehicle_id, vehicle_number, capacity_kg, status, assigned_zone_id, created_date FROM `VEHICLE`";
    public VehicleDAO(Connection connection) { this.connection = connection; }
    private static Vehicle map(ResultSet r) throws SQLException {
        return new Vehicle(
                Jdbc.nullableLong(r, "vehicle_id"),
                r.getString("vehicle_number"),
                r.getBigDecimal("capacity_kg"),
                r.getString("status"),
                Jdbc.nullableLong(r, "assigned_zone_id"),
                r.getObject("created_date", java.time.LocalDateTime.class));
    }

    public Optional<Vehicle> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE vehicle_id = ?", VehicleDAO::map, id);
    }

    public Optional<Vehicle> lockById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE vehicle_id = ? FOR UPDATE", VehicleDAO::map, id);
    }

    public List<Vehicle> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY vehicle_id", VehicleDAO::map);
    }

    public long insert(Vehicle value) {
        if (value == null || value.getVehicleId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `VEHICLE` (vehicle_number, capacity_kg, status, assigned_zone_id, created_date) VALUES (?, ?, ?, ?, ?)",
                value.getVehicleNumber(),
                value.getCapacityKg(),
                value.getStatus(),
                value.getAssignedZoneId(),
                value.getCreatedDate());
    }

    public List<Vehicle> findAvailable() {
        return Jdbc.list(connection, SELECT + " WHERE status = ? ORDER BY vehicle_id", VehicleDAO::map, ecoroute.db.DatabaseContract.AVAILABLE);
    }

    public void updateHomeZone(long id, Long zoneId) {
        Jdbc.change(connection, "UPDATE `VEHICLE` SET assigned_zone_id = ? WHERE vehicle_id = ?", zoneId, id);
    }

    public void updateStatus(long id, String expected, String next) {
        Jdbc.change(connection, "UPDATE `VEHICLE` SET status = ? WHERE vehicle_id = ? AND status = ?", next, id, expected);
    }
}
