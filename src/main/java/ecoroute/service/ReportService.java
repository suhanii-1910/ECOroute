package ecoroute.service;

import ecoroute.dao.ReportDAO;
import ecoroute.db.Database;
import java.time.LocalDate;
import java.util.List;

public final class ReportService {
    private final Database database;
    public ReportService(Database database) { this.database = database; }
    private static void dates(LocalDate from, LocalDate to) {
        Validation.require(from != null && to != null && !from.isAfter(to), "Use a date interval [from, to] where from <= to.");
    }
    public List<ReportDAO.CollectionVolume> collectionVolume(UserSession session, LocalDate from, LocalDate to) {
        dates(from, to); return database.read(c -> { Access.staff(c, session); return new ReportDAO(c).collectionVolume(from, to); });
    }
    public List<ReportDAO.RequestStatus> requestStatuses(UserSession session, LocalDate from, LocalDate to) {
        dates(from, to); return database.read(c -> { Access.staff(c, session); return new ReportDAO(c).requestStatuses(from, to); });
    }
    public List<ReportDAO.RouteWeight> routeWeights(UserSession session, LocalDate from, LocalDate to) {
        dates(from, to); return database.read(c -> { Access.staff(c, session); return new ReportDAO(c).routeWeights(from, to); });
    }
    public List<ReportDAO.VehicleUtilization> vehicleUtilization(UserSession session, LocalDate from, LocalDate to) {
        dates(from, to); return database.read(c -> { Access.staff(c, session); return new ReportDAO(c).vehicleUtilization(from, to); });
    }
    public List<ReportDAO.QuantityComparison> estimatedVersusActual(UserSession session, LocalDate from, LocalDate to) {
        dates(from, to); return database.read(c -> { Access.staff(c, session); return new ReportDAO(c).estimatedVersusActual(from, to); });
    }
}
