package ecoroute.dao;

import ecoroute.model.DisposalRecord;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class DisposalRecordDAO {
    private final Connection connection;
    private static final String SELECT =
            "SELECT disposal_id, route_id, facility_id, disposal_date, gross_weight_kg, tare_weight_kg, " +
            "net_disposed_weight_kg, receipt_number, status, notes FROM DISPOSAL_RECORD";
    public DisposalRecordDAO(Connection connection) { this.connection = connection; }

    private static DisposalRecord map(ResultSet r) throws SQLException {
        return new DisposalRecord(
                Jdbc.nullableLong(r, "disposal_id"),
                Jdbc.nullableLong(r, "route_id"),
                Jdbc.nullableLong(r, "facility_id"),
                r.getObject("disposal_date", java.time.LocalDateTime.class),
                r.getBigDecimal("gross_weight_kg"),
                r.getBigDecimal("tare_weight_kg"),
                r.getBigDecimal("net_disposed_weight_kg"),
                r.getString("receipt_number"),
                r.getString("status"),
                r.getString("notes"));
    }

    public Optional<DisposalRecord> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE disposal_id = ?", DisposalRecordDAO::map, id);
    }

    public List<DisposalRecord> findByRoute(long routeId) {
        return Jdbc.list(connection, SELECT + " WHERE route_id = ? ORDER BY disposal_id ASC", DisposalRecordDAO::map, routeId);
    }

    public Optional<DisposalRecord> findLatestByRoute(long routeId) {
        List<DisposalRecord> list = Jdbc.list(connection, SELECT + " WHERE route_id = ? ORDER BY disposal_id DESC", DisposalRecordDAO::map, routeId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<DisposalRecord> findByFacility(long facilityId) {
        return Jdbc.list(connection, SELECT + " WHERE facility_id = ? ORDER BY disposal_date DESC",
                DisposalRecordDAO::map, facilityId);
    }

    public List<DisposalRecord> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY disposal_date DESC", DisposalRecordDAO::map);
    }

    public long insert(DisposalRecord value) {
        if (value == null || value.getDisposalId() != null) {
            throw new ecoroute.exception.InvalidRequestException("Insert requires a new record with a null generated ID.");
        }
        return Jdbc.insert(
                connection,
                "INSERT INTO DISPOSAL_RECORD (route_id, facility_id, disposal_date, gross_weight_kg, tare_weight_kg, net_disposed_weight_kg, receipt_number, status, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                value.getRouteId(),
                value.getFacilityId(),
                value.getDisposalDate(),
                value.getGrossWeightKg(),
                value.getTareWeightKg(),
                value.getNetDisposedWeightKg(),
                value.getReceiptNumber(),
                value.getStatus(),
                value.getNotes());
    }
}
