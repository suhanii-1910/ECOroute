package ecoroute.dao;

import ecoroute.model.Zone;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class ZoneDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT zone_id, zone_name, description FROM `ZONE`";
    public ZoneDAO(Connection connection) { this.connection = connection; }
    private static Zone map(ResultSet r) throws SQLException {
        return new Zone(Jdbc.nullableLong(r, "zone_id"), r.getString("zone_name"), r.getString("description"));
    }

    public Optional<Zone> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE zone_id = ?", ZoneDAO::map, id);
    }

    public List<Zone> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY zone_id", ZoneDAO::map);
    }

    public long insert(Zone value) {
        if (value == null || value.getZoneId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `ZONE` (zone_name, description) VALUES (?, ?)",
                value.getZoneName(),
                value.getDescription());
    }

    public void update(Zone value) {
        Jdbc.change(
                connection,
                "UPDATE `ZONE` SET zone_name = ?, description = ? WHERE zone_id = ?",
                value.getZoneName(),
                value.getDescription(),
                value.getZoneId());
    }
}
