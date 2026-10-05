package ecoroute.service;

import ecoroute.dao.VehicleDAO;
import ecoroute.db.Database;
import ecoroute.model.Vehicle;
import ecoroute.exception.InvalidRequestException;
import ecoroute.exception.VehicleUnavailableException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static ecoroute.db.DatabaseContract.*;

public final class VehicleService {
    private final Database database;
    public VehicleService(Database database) { this.database = database; }
    public List<Vehicle> findAll(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new VehicleDAO(c).findAll(); });
    }
    public List<Vehicle> findAvailable(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new VehicleDAO(c).findAvailable(); });
    }
    public Optional<Vehicle> findById(UserSession session, long id) {
        return database.read(c -> { Access.staff(c, session); return new VehicleDAO(c).findById(id); });
    }
    public long create(UserSession session, String number, java.math.BigDecimal capacity, Long homeZone) {
        Validation.text(number, "Vehicle number"); Validation.vehicleCapacity(capacity);
        if (homeZone != null) Validation.id(homeZone, "Home zone");
        return database.transaction(c -> {
            Access.admin(c, session);
            return new VehicleDAO(c).insert(new Vehicle(null, number, capacity, AVAILABLE, homeZone, LocalDateTime.now()));
        });
    }
    public void updateStatus(UserSession session, long id, String next) {
        Validation.require(AVAILABLE.equals(next) || MAINTENANCE.equals(next), "Only available/maintenance changes are manual.");
        database.transaction(c -> {
            Access.admin(c, session);
            VehicleDAO dao = new VehicleDAO(c);
            Vehicle vehicle = dao.lockById(id).orElseThrow(() -> new InvalidRequestException("Vehicle does not exist."));
            if (IN_USE.equals(vehicle.getStatus())) throw new VehicleUnavailableException("Vehicle has an unfinished route.");
            Validation.require(AVAILABLE.equals(vehicle.getStatus()) || MAINTENANCE.equals(vehicle.getStatus()), "Unknown vehicle state.");
            Validation.require(!next.equals(vehicle.getStatus()), "Vehicle is already in this state.");
            dao.updateStatus(id, vehicle.getStatus(), next);
            return null;
        });
    }
    public void updateHomeZone(UserSession session, long id, Long zoneId) {
        if (zoneId != null) Validation.id(zoneId, "Home zone");
        database.transaction(c -> {
            Access.admin(c, session);
            VehicleDAO dao = new VehicleDAO(c);
            Vehicle vehicle = dao.lockById(id).orElseThrow(() -> new InvalidRequestException("Vehicle does not exist."));
            if (IN_USE.equals(vehicle.getStatus())) throw new VehicleUnavailableException("Cannot change the home zone during a route.");
            dao.updateHomeZone(id, zoneId);
            return null;
        });
    }
}
