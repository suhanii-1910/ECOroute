package ecoroute.dao;

import ecoroute.model.WasteGenerator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class GeneratorDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT generator_id, name, generator_type, contact_person, phone, email, address, latitude, longitude, zone_id, is_active, created_date FROM `WASTE_GENERATOR`";
    public GeneratorDAO(Connection connection) { this.connection = connection; }
    private static WasteGenerator map(ResultSet r) throws SQLException {
        return new WasteGenerator(
                Jdbc.nullableLong(r, "generator_id"),
                r.getString("name"),
                r.getString("generator_type"),
                r.getString("contact_person"),
                r.getString("phone"),
                r.getString("email"),
                r.getString("address"),
                r.getBigDecimal("latitude"),
                r.getBigDecimal("longitude"),
                Jdbc.nullableLong(r, "zone_id"),
                r.getBoolean("is_active"),
                r.getObject("created_date", java.time.LocalDateTime.class));
    }

    public Optional<WasteGenerator> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE generator_id = ?", GeneratorDAO::map, id);
    }

    public Optional<WasteGenerator> lockById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE generator_id = ? FOR UPDATE", GeneratorDAO::map, id);
    }

    public List<WasteGenerator> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY generator_id", GeneratorDAO::map);
    }

    public long insert(WasteGenerator value) {
        if (value == null || value.getGeneratorId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `WASTE_GENERATOR` (name, generator_type, contact_person, phone, email, address, latitude, longitude, zone_id, is_active, created_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                value.getName(),
                value.getGeneratorType(),
                value.getContactPerson(),
                value.getPhone(),
                value.getEmail(),
                value.getAddress(),
                value.getLatitude(),
                value.getLongitude(),
                value.getZoneId(),
                value.isActive(),
                value.getCreatedDate());
    }

    public void update(WasteGenerator value) {
        Jdbc.change(
                connection,
                "UPDATE `WASTE_GENERATOR` SET name = ?, contact_person = ?, phone = ?, email = ?, address = ?, latitude = ?, longitude = ?, zone_id = ? WHERE generator_id = ?",
                value.getName(),
                value.getContactPerson(),
                value.getPhone(),
                value.getEmail(),
                value.getAddress(),
                value.getLatitude(),
                value.getLongitude(),
                value.getZoneId(),
                value.getGeneratorId());
    }

    public List<WasteGenerator> findByZone(long id) {
        return Jdbc.list(connection, SELECT + " WHERE zone_id = ? ORDER BY generator_id", GeneratorDAO::map, id);
    }

    public void setActive(long id, boolean active) {
        Jdbc.change(connection, "UPDATE `WASTE_GENERATOR` SET is_active = ? WHERE generator_id = ?", active, id);
    }
}
