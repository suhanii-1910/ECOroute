package ecoroute.service;

import ecoroute.dao.*;
import ecoroute.db.Database;
import ecoroute.model.*;
import ecoroute.exception.InvalidRequestException;
import ecoroute.exception.VehicleUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static ecoroute.db.DatabaseContract.*;

/** Persists Member 3's already ordered plan. Does not assign vehicles or compute routes. */
public final class RoutePersistenceService {
    public record PlannedStop(long requestId, int sequence) {}
    private final Database database;
    public RoutePersistenceService(Database database) { this.database = database; }

    public long save(UserSession session, long vehicleId, long zoneId, LocalDate date, List<PlannedStop> stops) {
        Validation.id(vehicleId, "Vehicle"); Validation.id(zoneId, "Zone");
        Validation.require(date != null && !date.isBefore(LocalDate.now()), "Route date must be today or later.");
        Validation.require(stops != null && !stops.isEmpty(), "A route needs at least one stop.");
        List<PlannedStop> plan = new ArrayList<>(stops);
        Set<Long> requestIds = new TreeSet<>();
        int previous = 0;
        for (PlannedStop stop : plan) {
            Validation.require(stop != null, "Stop is required.");
            Validation.id(stop.requestId(), "Request");
            Validation.require(stop.sequence() > previous, "Stop sequences must be positive, unique and supplied in ascending order.");
            Validation.require(requestIds.add(stop.requestId()), "A request can appear only once in a route.");
            previous = stop.sequence();
        }
        return database.transaction(c -> {
            Access.staff(c, session);
            VehicleDAO vehicles = new VehicleDAO(c);
            Vehicle vehicle = vehicles.lockById(vehicleId)
                    .orElseThrow(() -> new VehicleUnavailableException("Vehicle does not exist."));
            if (!AVAILABLE.equals(vehicle.getStatus())) throw new VehicleUnavailableException("Vehicle is not available.");
            Validation.quantity(vehicle.getCapacityKg(), true, "Vehicle capacity");
            Validation.require(vehicle.getAssignedZoneId() == null || vehicle.getAssignedZoneId().equals(zoneId),
                    "Route zone differs from the vehicle home zone.");
            Validation.require(new ZoneDAO(c).findById(zoneId).isPresent(), "Zone does not exist.");
            RequestDAO requests = new RequestDAO(c);
            RouteStopDAO routeStops = new RouteStopDAO(c);
            // All assignments lock requests in ID order, regardless of the supplied route order.
            Map<Long, PickupRequest> locked = new HashMap<>();
            for (long id : requestIds) locked.put(id, requests.lockById(id)
                    .orElseThrow(() -> new InvalidRequestException("A requested pickup does not exist.")));
            BigDecimal total = BigDecimal.ZERO;
            for (long id : requestIds) {
                PickupRequest request = locked.get(id);
                Validation.transition(request.getStatus(), PENDING);
                Validation.require(routeStops.findByRequest(id).isEmpty(), "Request already has a route stop.");
                WasteGenerator generator = new GeneratorDAO(c).lockById(request.getGeneratorId())
                        .orElseThrow(() -> new InvalidRequestException("Generator does not exist."));
                Validation.require(generator.isActive(), "Generator is inactive.");
                Validation.require(generator.getZoneId().equals(zoneId), "Request belongs to another zone.");
                Validation.require(!date.isBefore(request.getRequestDate().toLocalDate())
                        && (request.getPreferredPickupDate() == null || !date.isBefore(request.getPreferredPickupDate())),
                        "Route is earlier than the request or preferred date.");
                List<RequestWaste> lines = new RequestWasteDAO(c).findByRequest(id);
                Validation.require(!lines.isEmpty(), "Request has no waste lines.");
                for (RequestWaste line : lines) {
                    Validation.quantity(line.getEstimatedQuantity(), true, "Estimated quantity");
                    Validation.require(line.getActualQuantity() == null, "Pending request already has actual quantities.");
                    total = total.add(line.getEstimatedQuantity());
                }
            }
            Validation.require(total.compareTo(vehicle.getCapacityKg()) <= 0, "Estimated route weight exceeds vehicle capacity.");
            long routeId = new RouteDAO(c).insert(new Route(null, vehicleId, zoneId, date, PLANNED, LocalDateTime.now()));
            for (PlannedStop stop : plan) {
                routeStops.insert(new RouteStop(null, routeId, stop.requestId(), stop.sequence(), PENDING, null, null));
                requests.updateStatus(stop.requestId(), PENDING, ASSIGNED);
            }
            vehicles.updateStatus(vehicleId, AVAILABLE, IN_USE);
            return routeId;
        });
    }

    public Optional<Route> findById(UserSession session, long id) {
        return database.read(c -> { Access.staff(c, session); return new RouteDAO(c).findById(id); });
    }
    public List<Route> findByVehicle(UserSession session, long id) {
        return database.read(c -> { Access.staff(c, session); return new RouteDAO(c).findByVehicle(id); });
    }
    public List<Route> findByZone(UserSession session, long id) {
        return database.read(c -> { Access.staff(c, session); return new RouteDAO(c).findByZone(id); });
    }
    public List<RouteStop> findStops(UserSession session, long routeId) {
        return database.read(c -> { Access.staff(c, session); return new RouteStopDAO(c).findByRouteOrdered(routeId); });
    }
}
