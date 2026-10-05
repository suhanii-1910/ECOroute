package ecoroute.dao;

import ecoroute.model.DisposalSite;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class DisposalSiteDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT site_id, site_name, site_type, address, latitude, longitude, capacity_kg, status FROM `DISPOSAL_SITE`";
    public DisposalSiteDAO(Connection connection) { this.connection = connection; }

    private static DisposalSite map(ResultSet r) throws SQLException {
        return new DisposalSite(Jdbc.nullableLong(r, "site_id"), r.getString("site_name"),
                r.getString("site_type"), r.getString("address"), r.getBigDecimal("latitude"),
                r.getBigDecimal("longitude"), r.getBigDecimal("capacity_kg"), r.getString("status"));
    }

    public Optional<DisposalSite> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE site_id = ?", DisposalSiteDAO::map, id);
    }

    public List<DisposalSite> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY site_id", DisposalSiteDAO::map);
    }

    /** Exact status filter; the frozen schema does not define an availability vocabulary. */
    public List<DisposalSite> findByStatus(String status) {
        return Jdbc.list(connection, SELECT + " WHERE status = ? ORDER BY site_id", DisposalSiteDAO::map, status);
    }
}
