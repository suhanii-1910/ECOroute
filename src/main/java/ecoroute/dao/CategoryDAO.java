package ecoroute.dao;

import ecoroute.model.WasteCategory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class CategoryDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT category_id, category_name, description, hazard_level FROM `WASTE_CATEGORY`";
    public CategoryDAO(Connection connection) { this.connection = connection; }
    private static WasteCategory map(ResultSet r) throws SQLException {
        return new WasteCategory(
                Jdbc.nullableLong(r, "category_id"),
                r.getString("category_name"),
                r.getString("description"),
                r.getString("hazard_level"));
    }

    public Optional<WasteCategory> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE category_id = ?", CategoryDAO::map, id);
    }

    public List<WasteCategory> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY category_id", CategoryDAO::map);
    }

    public long insert(WasteCategory value) {
        if (value == null || value.getCategoryId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO `WASTE_CATEGORY` (category_name, description, hazard_level) VALUES (?, ?, ?)",
                value.getCategoryName(),
                value.getDescription(),
                value.getHazardLevel());
    }

    public void update(WasteCategory value) {
        Jdbc.change(
                connection,
                "UPDATE `WASTE_CATEGORY` SET category_name = ?, description = ?, hazard_level = ? WHERE category_id = ?",
                value.getCategoryName(),
                value.getDescription(),
                value.getHazardLevel(),
                value.getCategoryId());
    }

    public void delete(long id) {
        Jdbc.change(connection, "DELETE FROM `WASTE_CATEGORY` WHERE category_id = ?", id);
    }
}
