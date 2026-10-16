package ecoroute.dao;

import ecoroute.model.DisposalFacility;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class DisposalFacilityDAO {
    private final Connection connection;
    private static final String SELECT =
            "SELECT facility_id, facility_name, facility_type, address, latitude, longitude, " +
            "capacity_tonnes_per_day, status, contact_info FROM DISPOSAL_FACILITY";
    public DisposalFacilityDAO(Connection connection) { this.connection = connection; }

    private static DisposalFacility map(ResultSet r) throws SQLException {
        return new DisposalFacility(
                Jdbc.nullableLong(r, "facility_id"),
                r.getString("facility_name"),
                r.getString("facility_type"),
                r.getString("address"),
                r.getObject("latitude", Double.class),
                r.getObject("longitude", Double.class),
                r.getBigDecimal("capacity_tonnes_per_day"),
                r.getString("status"),
                r.getString("contact_info"));
    }

    public Optional<DisposalFacility> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE facility_id = ?", DisposalFacilityDAO::map, id);
    }

    public Optional<DisposalFacility> lockById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE facility_id = ? FOR UPDATE", DisposalFacilityDAO::map, id);
    }

    public List<DisposalFacility> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY facility_id", DisposalFacilityDAO::map);
    }

    public List<DisposalFacility> findByStatus(String status) {
        return Jdbc.list(connection, SELECT + " WHERE status = ? ORDER BY facility_id", DisposalFacilityDAO::map, status);
    }

    /** Returns active facilities that accept every category in the given set. */
    public List<DisposalFacility> findSuitableForCategories(java.util.Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return findByStatus("ACTIVE");
        int n = categoryIds.size();
        String placeholders = String.join(",", java.util.Collections.nCopies(n, "?"));
        String sql = SELECT +
                " WHERE status = 'ACTIVE' AND facility_id IN (" +
                "  SELECT facility_id FROM FACILITY_ACCEPTED_WASTE" +
                "  WHERE category_id IN (" + placeholders + ")" +
                "  GROUP BY facility_id HAVING COUNT(DISTINCT category_id) = ?" +
                ") ORDER BY facility_id";
        Object[] params = new Object[n + 1];
        int i = 0;
        for (long id : categoryIds) params[i++] = id;
        params[i] = n;
        return Jdbc.list(connection, sql, DisposalFacilityDAO::map, params);
    }

    /** Returns active facilities that accept at least one of the given categories. */
    public List<DisposalFacility> findAcceptingAnyCategory(java.util.Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return findByStatus("ACTIVE");
        int n = categoryIds.size();
        String placeholders = String.join(",", java.util.Collections.nCopies(n, "?"));
        String sql = SELECT +
                " WHERE status = 'ACTIVE' AND facility_id IN (" +
                "  SELECT DISTINCT facility_id FROM FACILITY_ACCEPTED_WASTE" +
                "  WHERE category_id IN (" + placeholders + ")" +
                ") ORDER BY facility_id";
        Object[] params = new Object[n];
        int i = 0;
        for (long id : categoryIds) params[i++] = id;
        return Jdbc.list(connection, sql, DisposalFacilityDAO::map, params);
    }

    public long insert(DisposalFacility f) {
        return Jdbc.insert(connection,
                "INSERT INTO DISPOSAL_FACILITY (facility_name, facility_type, address, latitude, longitude, " +
                "capacity_tonnes_per_day, status, contact_info) VALUES (?,?,?,?,?,?,?,?)",
                f.getFacilityName(), f.getFacilityType(), f.getAddress(), f.getLatitude(), f.getLongitude(),
                f.getCapacityTonnesPerDay(), f.getStatus(), f.getContactInfo());
    }

    public void update(DisposalFacility f) {
        Jdbc.change(connection,
                "UPDATE DISPOSAL_FACILITY SET facility_name=?, facility_type=?, address=?, latitude=?, longitude=?, " +
                "capacity_tonnes_per_day=?, status=?, contact_info=? WHERE facility_id=?",
                f.getFacilityName(), f.getFacilityType(), f.getAddress(), f.getLatitude(), f.getLongitude(),
                f.getCapacityTonnesPerDay(), f.getStatus(), f.getContactInfo(), f.getFacilityId());
    }
}
