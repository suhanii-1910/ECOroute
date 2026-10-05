package ecoroute.service;

import ecoroute.dao.*;
import ecoroute.db.Database;
import ecoroute.model.*;
import java.util.List;
import java.util.Optional;
import static ecoroute.db.DatabaseContract.*;

/** GUI-facing catalogs and zone/category maintenance; deletion is protected by foreign keys. */
public final class CatalogService {
    private final Database database;
    public CatalogService(Database database) { this.database = database; }
    public List<Zone> zones(UserSession session) {
        return database.read(c -> { Access.current(c, session); return new ZoneDAO(c).findAll(); });
    }
    public List<WasteCategory> categories(UserSession session) {
        return database.read(c -> { Access.current(c, session); return new CategoryDAO(c).findAll(); });
    }
    public List<DisposalSite> disposalSites(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new DisposalSiteDAO(c).findAll(); });
    }
    public Optional<DisposalSite> disposalSite(UserSession session, long siteId) {
        Validation.id(siteId, "Disposal site");
        return database.read(c -> { Access.staff(c, session); return new DisposalSiteDAO(c).findById(siteId); });
    }
    public List<DisposalSite> disposalSitesByStatus(UserSession session, String status) {
        Validation.text(status, "Disposal site status");
        return database.read(c -> { Access.staff(c, session); return new DisposalSiteDAO(c).findByStatus(status); });
    }
    public long saveZone(UserSession session, Zone zone) {
        Validation.require(zone != null, "Zone is required."); Validation.text(zone.getZoneName(), "Zone name");
        return database.transaction(c -> {
            Access.admin(c, session); ZoneDAO dao = new ZoneDAO(c);
            if (zone.getZoneId() == null) return dao.insert(zone);
            Validation.id(zone.getZoneId(), "Zone"); dao.update(zone); return zone.getZoneId();
        });
    }
    public long saveCategory(UserSession session, WasteCategory category) {
        Validation.require(category != null, "Category is required."); Validation.text(category.getCategoryName(), "Category name");
        Validation.require(category.getHazardLevel() != null && HAZARD_LEVELS.contains(category.getHazardLevel()), "Unknown hazard level.");
        return database.transaction(c -> {
            Access.admin(c, session); CategoryDAO dao = new CategoryDAO(c);
            if (category.getCategoryId() == null) return dao.insert(category);
            Validation.id(category.getCategoryId(), "Category"); dao.update(category); return category.getCategoryId();
        });
    }
    public void deleteUnreferencedCategory(UserSession session, long id) {
        database.transaction(c -> { Access.admin(c, session); new CategoryDAO(c).delete(id); return null; });
    }
}
