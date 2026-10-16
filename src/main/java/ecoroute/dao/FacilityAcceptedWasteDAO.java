package ecoroute.dao;

import ecoroute.model.FacilityAcceptedWaste;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class FacilityAcceptedWasteDAO {
    private final Connection connection;
    private static final String SELECT =
            "SELECT facility_id, category_id, processing_rate_per_kg FROM FACILITY_ACCEPTED_WASTE";
    public FacilityAcceptedWasteDAO(Connection connection) { this.connection = connection; }

    private static FacilityAcceptedWaste map(ResultSet r) throws SQLException {
        return new FacilityAcceptedWaste(
                Jdbc.nullableLong(r, "facility_id"),
                Jdbc.nullableLong(r, "category_id"),
                r.getBigDecimal("processing_rate_per_kg"));
    }

    public List<FacilityAcceptedWaste> findByFacility(long facilityId) {
        return Jdbc.list(connection, SELECT + " WHERE facility_id = ? ORDER BY category_id",
                FacilityAcceptedWasteDAO::map, facilityId);
    }

    public void insert(FacilityAcceptedWaste value) {
        Jdbc.change(connection,
                "INSERT INTO FACILITY_ACCEPTED_WASTE (facility_id, category_id, processing_rate_per_kg) VALUES (?,?,?)",
                value.getFacilityId(), value.getCategoryId(), value.getProcessingRatePerKg());
    }

    public void delete(long facilityId, long categoryId) {
        Jdbc.change(connection,
                "DELETE FROM FACILITY_ACCEPTED_WASTE WHERE facility_id = ? AND category_id = ?",
                facilityId, categoryId);
    }
}
