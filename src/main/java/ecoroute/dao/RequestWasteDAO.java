package ecoroute.dao;

import ecoroute.model.RequestWaste;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class RequestWasteDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT request_id, category_id, estimated_quantity, actual_quantity FROM `REQUEST_WASTE`";
    public RequestWasteDAO(Connection connection) { this.connection = connection; }
    private static RequestWaste map(ResultSet r) throws SQLException {
        return new RequestWaste(
                Jdbc.nullableLong(r, "request_id"),
                Jdbc.nullableLong(r, "category_id"),
                r.getBigDecimal("estimated_quantity"),
                r.getBigDecimal("actual_quantity"));
    }

    public void insertLine(RequestWaste value) {
        Jdbc.change(
                connection,
                "INSERT INTO `REQUEST_WASTE` (request_id, category_id, estimated_quantity, actual_quantity) VALUES (?, ?, ?, ?)",
                value.getRequestId(),
                value.getCategoryId(),
                value.getEstimatedQuantity(),
                value.getActualQuantity());
    }

    public List<RequestWaste> findByRequest(long id) {
        return Jdbc.list(connection, SELECT + " WHERE request_id = ? ORDER BY category_id", RequestWasteDAO::map, id);
    }

    public void updateActualQuantity(long requestId, long categoryId, java.math.BigDecimal actual) {
        Jdbc.change(
                connection,
                "UPDATE `REQUEST_WASTE` SET actual_quantity = ? WHERE request_id = ? AND category_id = ?",
                actual,
                requestId,
                categoryId);
    }
}
