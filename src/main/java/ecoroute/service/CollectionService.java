package ecoroute.service;

import ecoroute.dao.*;
import ecoroute.db.Database;
import ecoroute.model.*;
import ecoroute.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static ecoroute.db.DatabaseContract.*;

public final class CollectionService {
    private final Database database;
    public CollectionService(Database database) { this.database = database; }

    public void complete(UserSession session, long stopId, Map<Long, BigDecimal> actualByCategory,
                         LocalDateTime arrival, LocalDateTime completion) {
        Validation.require(actualByCategory != null && !actualByCategory.isEmpty(), "Actual quantities are required.");
        Map<Long, BigDecimal> actuals = new HashMap<>(actualByCategory);
        actuals.forEach((id, quantity) -> {
            Validation.id(id, "Category"); Validation.quantity(quantity, false, "Actual quantity");
        });
        Validation.require(arrival != null && completion != null && !completion.isBefore(arrival)
                && !completion.isAfter(LocalDateTime.now()), "Collection times must be ordered and cannot be in the future.");
        database.transaction(c -> {
            Access.staff(c, session);
            RouteStopDAO stops = new RouteStopDAO(c);
            RouteStop initial = stops.findById(stopId).orElseThrow(() -> new InvalidRequestException("Stop does not exist."));
            RouteDAO routes = new RouteDAO(c);
            Route initialRoute = routes.findById(initial.getRouteId()).orElseThrow(() -> new InvalidRequestException("Route does not exist."));
            // Same lock order as assignment: vehicle, route, request, generator, stop. Serializes totals per vehicle.
            VehicleDAO vehicles = new VehicleDAO(c);
            Vehicle vehicle = vehicles.lockById(initialRoute.getVehicleId()).orElseThrow(() -> new InvalidRequestException("Vehicle does not exist."));
            Route route = routes.lockById(initialRoute.getRouteId()).orElseThrow(() -> new InvalidRequestException("Route does not exist."));
            RequestDAO requests = new RequestDAO(c);
            PickupRequest request = requests.lockById(initial.getRequestId()).orElseThrow(() -> new InvalidRequestException("Request does not exist."));
            WasteGenerator generator = new GeneratorDAO(c).lockById(request.getGeneratorId())
                    .orElseThrow(() -> new InvalidRequestException("Generator does not exist."));
            Validation.require(generator.isActive(), "Generator is inactive.");
            RouteStop stop = stops.lockById(stopId).orElseThrow(() -> new InvalidRequestException("Stop does not exist."));
            Validation.transition(vehicle.getStatus(), IN_USE);
            Validation.transition(route.getStatus(), PLANNED);
            Validation.transition(stop.getStatus(), PENDING);
            Validation.transition(request.getStatus(), ASSIGNED);
            Validation.require(!arrival.toLocalDate().isBefore(route.getRouteDate()) && !arrival.isBefore(request.getRequestDate()),
                    "Arrival precedes the route date or request creation.");
            RequestWasteDAO waste = new RequestWasteDAO(c);
            List<RequestWaste> lines = waste.findByRequest(request.getRequestId());
            Set<Long> expectedCategories = new HashSet<>();
            lines.forEach(line -> expectedCategories.add(line.getCategoryId()));
            Validation.require(expectedCategories.equals(actuals.keySet()), "Supply actual quantities for exactly the request's categories.");
            BigDecimal alreadyCollected = BigDecimal.ZERO;
            for (RouteStop other : stops.findByRouteOrdered(route.getRouteId())) {
                for (RequestWaste line : waste.findByRequest(other.getRequestId())) {
                    if (line.getActualQuantity() != null) alreadyCollected = alreadyCollected.add(line.getActualQuantity());
                }
            }
            BigDecimal thisCollection = actuals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            Validation.require(alreadyCollected.add(thisCollection).compareTo(vehicle.getCapacityKg()) <= 0,
                    "Actual route weight would exceed vehicle capacity; staff must resolve the discrepancy before completion.");
            for (RequestWaste line : lines) {
                Validation.require(line.getActualQuantity() == null, "Actual quantities have already been recorded.");
                waste.updateActualQuantity(request.getRequestId(), line.getCategoryId(), actuals.get(line.getCategoryId()));
            }
            stops.updateActualTiming(stopId, arrival, completion);
            stops.updateStopStatus(stopId, PENDING, COMPLETED);
            requests.complete(request.getRequestId(), completion);
            if (stops.findByRouteOrdered(route.getRouteId()).stream().allMatch(s -> COMPLETED.equals(s.getStatus()))) {
                routes.updateStatus(route.getRouteId(), PLANNED, COMPLETED);
                vehicles.updateStatus(vehicle.getVehicleId(), IN_USE, AVAILABLE);
            }
            return null;
        });
    }
}
