package ecoroute.service;

import ecoroute.dao.*;
import ecoroute.db.Database;
import ecoroute.model.*;
import ecoroute.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.util.*;
import java.util.stream.Collectors;
import static ecoroute.db.DatabaseContract.*;

/** Records disposal for a completed route and selects the nearest suitable facility. */
public final class DisposalService {
    private final Database database;
    public DisposalService(Database database) { this.database = database; }

    public record RouteCategoryStatus(
            long categoryId,
            String categoryName,
            BigDecimal weightKg,
            boolean disposed,
            String disposedAtFacility
    ) {}

    public record FacilityCompatibility(
            boolean compatible,
            Set<Long> acceptedRemainingCatIds,
            Set<Long> otherRemainingCatIds,
            BigDecimal expectedNetWeightKg,
            String message
    ) {}

    /**
     * Calls sp_record_disposal. The procedure handles capacity and category compatibility checks
     * and marks the route COMPLETED when all categories have been disposed across compatible facilities.
     */
    public long recordDisposal(UserSession session, long routeId, long facilityId,
                               BigDecimal grossWeightKg, BigDecimal tareWeightKg, String receiptNumber) {
        Validation.id(routeId, "Route"); Validation.id(facilityId, "Facility");
        Validation.require(grossWeightKg != null && grossWeightKg.compareTo(BigDecimal.ZERO) > 0, "Gross weight must be positive.");
        Validation.require(tareWeightKg != null && tareWeightKg.compareTo(BigDecimal.ZERO) >= 0, "Tare weight must be non-negative.");
        Validation.require(grossWeightKg.compareTo(tareWeightKg) >= 0, "Gross weight must be >= tare weight.");
        return database.transaction(c -> {
            Access.staff(c, session);
            try (CallableStatement cs = c.prepareCall("{call sp_record_disposal(?,?,?,?,?)}")) {
                cs.setLong(1, routeId);
                cs.setLong(2, facilityId);
                cs.setBigDecimal(3, grossWeightKg);
                cs.setBigDecimal(4, tareWeightKg);
                if (receiptNumber != null && !receiptNumber.isBlank()) {
                    cs.setString(5, receiptNumber);
                } else {
                    cs.setNull(5, java.sql.Types.VARCHAR);
                }
                cs.execute();
            } catch (java.sql.SQLException e) {
                if (e.getErrorCode() == 90022 || (e.getMessage() != null && e.getMessage().contains("sp_record_disposal"))) {
                    executeDisposalFallback(c, routeId, facilityId, grossWeightKg, tareWeightKg, receiptNumber);
                } else {
                    throw new InvalidRequestException("Disposal failed: " + e.getMessage());
                }
            }

            // Update vehicle status back to AVAILABLE once the route is fully completed
            Route r = new RouteDAO(c).findById(routeId).orElse(null);
            if (r != null && COMPLETED.equals(r.getStatus())) {
                new VehicleDAO(c).updateStatus(r.getVehicleId(), IN_ROUTE, AVAILABLE);
            }

            return new DisposalRecordDAO(c).findLatestByRoute(routeId)
                    .map(DisposalRecord::getDisposalId)
                    .orElseThrow(() -> new InvalidRequestException("Disposal record not found after procedure call."));
        });
    }

    private void executeDisposalFallback(java.sql.Connection c, long routeId, long facilityId,
                                         BigDecimal grossWeightKg, BigDecimal tareWeightKg, String receiptNumber) {
        DisposalFacility facility = new DisposalFacilityDAO(c).findById(facilityId)
                .orElseThrow(() -> new InvalidRequestException("Disposal facility not found"));
        if (!ACTIVE.equals(facility.getStatus())) {
            throw new InvalidRequestException("Disposal facility is not ACTIVE");
        }

        RouteDAO routeDao = new RouteDAO(c);
        Route route = routeDao.lockById(routeId)
                .orElseThrow(() -> new InvalidRequestException("Route not found"));
        if (!IN_PROGRESS.equals(route.getStatus()) && !DISPATCHED_TO_DISPOSAL.equals(route.getStatus())) {
            throw new InvalidRequestException("Route is not in a dispatchable state");
        }

        // Determine all route categories
        Set<Long> routeCategoryIds = new HashSet<>();
        for (RouteStop rs : new RouteStopDAO(c).findByRouteOrdered(routeId)) {
            for (RequestWaste rw : new RequestWasteDAO(c).findByRequest(rs.getRequestId())) {
                routeCategoryIds.add(rw.getCategoryId());
            }
        }

        // Determine categories already disposed in prior VERIFIED records for this route
        Set<Long> alreadyDisposedCatIds = new HashSet<>();
        for (DisposalRecord dr : new DisposalRecordDAO(c).findByRoute(routeId)) {
            if (VERIFIED.equals(dr.getStatus())) {
                for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(dr.getFacilityId())) {
                    if (routeCategoryIds.contains(faw.getCategoryId())) {
                        alreadyDisposedCatIds.add(faw.getCategoryId());
                    }
                }
            }
        }

        Set<Long> remainingCatIds = new HashSet<>(routeCategoryIds);
        remainingCatIds.removeAll(alreadyDisposedCatIds);

        // Facility accepted categories
        Set<Long> facilityAcceptedCatIds = new HashSet<>();
        for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(facilityId)) {
            facilityAcceptedCatIds.add(faw.getCategoryId());
        }

        // Check that facility accepts at least one remaining category
        Set<Long> newlyCovered = new HashSet<>(remainingCatIds);
        newlyCovered.retainAll(facilityAcceptedCatIds);

        if (newlyCovered.isEmpty()) {
            throw new InvalidRequestException("Facility does not accept any remaining waste categories on this route");
        }

        // Capacity check: sum existing disposal records for today at this facility
        BigDecimal capacityKg = facility.getCapacityTonnesPerDay().multiply(BigDecimal.valueOf(1000));
        BigDecimal netWeight = grossWeightKg.subtract(tareWeightKg);
        BigDecimal usedToday = new DisposalRecordDAO(c).findByFacility(facilityId).stream()
                .filter(dr -> !REJECTED.equals(dr.getStatus()) && dr.getDisposalDate() != null
                        && dr.getDisposalDate().toLocalDate().equals(java.time.LocalDate.now()))
                .map(DisposalRecord::getNetDisposedWeightKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (usedToday.add(netWeight).compareTo(capacityKg) > 0) {
            throw new InvalidRequestException("Disposal facility daily capacity exceeded");
        }

        // Insert disposal record
        DisposalRecord record = new DisposalRecord(
                null, routeId, facilityId, java.time.LocalDateTime.now(),
                grossWeightKg, tareWeightKg, netWeight,
                (receiptNumber != null && !receiptNumber.isBlank()) ? receiptNumber : null,
                "VERIFIED", "Disposal recorded via dialect fallback"
        );
        new DisposalRecordDAO(c).insert(record);

        // Check if all categories on the route are now disposed
        Set<Long> allDisposedNow = new HashSet<>(alreadyDisposedCatIds);
        allDisposedNow.addAll(newlyCovered);

        if (allDisposedNow.containsAll(routeCategoryIds)) {
            // Mark route as COMPLETED
            routeDao.updateStatus(routeId, route.getStatus(), COMPLETED);
        }
    }

    /**
     * Returns the set of waste category IDs that remain to be disposed for the given route.
     */
    public Set<Long> getRemainingCategories(UserSession session, long routeId) {
        return database.read(c -> {
            Access.staff(c, session);
            Set<Long> routeCategoryIds = new HashSet<>();
            for (RouteStop rs : new RouteStopDAO(c).findByRouteOrdered(routeId)) {
                for (RequestWaste rw : new RequestWasteDAO(c).findByRequest(rs.getRequestId())) {
                    routeCategoryIds.add(rw.getCategoryId());
                }
            }

            Set<Long> alreadyDisposed = new HashSet<>();
            for (DisposalRecord dr : new DisposalRecordDAO(c).findByRoute(routeId)) {
                if (VERIFIED.equals(dr.getStatus())) {
                    for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(dr.getFacilityId())) {
                        if (routeCategoryIds.contains(faw.getCategoryId())) {
                            alreadyDisposed.add(faw.getCategoryId());
                        }
                    }
                }
            }

            Set<Long> remaining = new HashSet<>(routeCategoryIds);
            remaining.removeAll(alreadyDisposed);
            return remaining;
        });
    }

    /**
     * Returns detailed breakdown of waste categories on the route with their collected weights
     * and disposal status.
     */
    public List<RouteCategoryStatus> getRouteCategoriesStatus(UserSession session, long routeId) {
        return database.read(c -> {
            Access.staff(c, session);
            Map<Long, BigDecimal> categoryWeights = new LinkedHashMap<>();
            for (RouteStop rs : new RouteStopDAO(c).findByRouteOrdered(routeId)) {
                for (RequestWaste rw : new RequestWasteDAO(c).findByRequest(rs.getRequestId())) {
                    BigDecimal wt = rw.getActualQuantity() != null ? rw.getActualQuantity() : rw.getEstimatedQuantity();
                    if (wt == null) wt = BigDecimal.ZERO;
                    categoryWeights.merge(rw.getCategoryId(), wt, BigDecimal::add);
                }
            }

            Map<Long, String> disposedFacilityMap = new HashMap<>();
            for (DisposalRecord dr : new DisposalRecordDAO(c).findByRoute(routeId)) {
                if (VERIFIED.equals(dr.getStatus())) {
                    DisposalFacility fac = new DisposalFacilityDAO(c).findById(dr.getFacilityId()).orElse(null);
                    String facName = fac != null ? fac.getFacilityName() : "Facility #" + dr.getFacilityId();
                    for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(dr.getFacilityId())) {
                        disposedFacilityMap.putIfAbsent(faw.getCategoryId(), facName);
                    }
                }
            }

            CategoryDAO catDao = new CategoryDAO(c);
            List<RouteCategoryStatus> result = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> entry : categoryWeights.entrySet()) {
                long catId = entry.getKey();
                BigDecimal weight = entry.getValue();
                String name = catDao.findById(catId).map(WasteCategory::getCategoryName).orElse("Category #" + catId);
                String facName = disposedFacilityMap.get(catId);
                result.add(new RouteCategoryStatus(catId, name, weight, facName != null, facName));
            }
            return result;
        });
    }

    /**
     * Evaluates compatibility of a selected facility against the remaining waste categories on a route.
     */
    public FacilityCompatibility evaluateFacilityCompatibility(UserSession session, long routeId, long facilityId) {
        return database.read(c -> {
            Access.staff(c, session);
            Set<Long> routeCategoryIds = new HashSet<>();
            Map<Long, BigDecimal> weightsByCat = new HashMap<>();
            for (RouteStop rs : new RouteStopDAO(c).findByRouteOrdered(routeId)) {
                for (RequestWaste rw : new RequestWasteDAO(c).findByRequest(rs.getRequestId())) {
                    routeCategoryIds.add(rw.getCategoryId());
                    BigDecimal wt = rw.getActualQuantity() != null ? rw.getActualQuantity() : rw.getEstimatedQuantity();
                    if (wt == null) wt = BigDecimal.ZERO;
                    weightsByCat.merge(rw.getCategoryId(), wt, BigDecimal::add);
                }
            }

            Set<Long> alreadyDisposed = new HashSet<>();
            for (DisposalRecord dr : new DisposalRecordDAO(c).findByRoute(routeId)) {
                if (VERIFIED.equals(dr.getStatus())) {
                    for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(dr.getFacilityId())) {
                        if (routeCategoryIds.contains(faw.getCategoryId())) {
                            alreadyDisposed.add(faw.getCategoryId());
                        }
                    }
                }
            }

            Set<Long> remainingCatIds = new HashSet<>(routeCategoryIds);
            remainingCatIds.removeAll(alreadyDisposed);

            CategoryDAO catDao = new CategoryDAO(c);
            if (remainingCatIds.isEmpty()) {
                return new FacilityCompatibility(
                        false, Collections.emptySet(), Collections.emptySet(), BigDecimal.ZERO,
                        "All waste categories on this route have already been verified as disposed."
                );
            }

            Set<Long> facilityCats = new HashSet<>();
            for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(facilityId)) {
                facilityCats.add(faw.getCategoryId());
            }

            Set<Long> acceptedRemaining = new HashSet<>(remainingCatIds);
            acceptedRemaining.retainAll(facilityCats);

            Set<Long> otherRemaining = new HashSet<>(remainingCatIds);
            otherRemaining.removeAll(acceptedRemaining);

            if (acceptedRemaining.isEmpty()) {
                String remainingNames = remainingCatIds.stream()
                        .map(id -> catDao.findById(id).map(WasteCategory::getCategoryName).orElse("Cat #" + id))
                        .collect(Collectors.joining(", "));
                String facilityNames = facilityCats.stream()
                        .map(id -> catDao.findById(id).map(WasteCategory::getCategoryName).orElse("Cat #" + id))
                        .collect(Collectors.joining(", "));
                return new FacilityCompatibility(
                        false, Collections.emptySet(), otherRemaining, BigDecimal.ZERO,
                        "INCOMPATIBLE: Facility accepts [" + facilityNames + "], but this route requires [" + remainingNames + "]."
                );
            }

            BigDecimal expectedNet = acceptedRemaining.stream()
                    .map(id -> weightsByCat.getOrDefault(id, BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String acceptedNames = acceptedRemaining.stream()
                    .map(id -> catDao.findById(id).map(WasteCategory::getCategoryName).orElse("Cat #" + id))
                    .collect(Collectors.joining(", "));

            if (otherRemaining.isEmpty()) {
                return new FacilityCompatibility(
                        true, acceptedRemaining, Collections.emptySet(), expectedNet,
                        "COMPATIBLE: Accepts all remaining categories (" + acceptedNames + ", ~" + expectedNet + " kg). Completes route disposal."
                );
            } else {
                String otherNames = otherRemaining.stream()
                        .map(id -> catDao.findById(id).map(WasteCategory::getCategoryName).orElse("Cat #" + id))
                        .collect(Collectors.joining(", "));
                return new FacilityCompatibility(
                        true, acceptedRemaining, otherRemaining, expectedNet,
                        "PARTIALLY COMPATIBLE: Unloads " + acceptedNames + " (~" + expectedNet + " kg). Note: [" + otherNames + "] must be unloaded at a specialized facility."
                );
            }
        });
    }

    /**
     * Selects the nearest active facility that accepts remaining waste categories on the route
     * and has available daily capacity using a Euclidean distance heuristic.
     */
    public Optional<DisposalFacility> selectNearestSuitableFacility(
            UserSession session, long routeId, double fromLat, double fromLon) {
        return database.read(c -> {
            Access.staff(c, session);
            Set<Long> routeCategoryIds = new HashSet<>();
            for (RouteStop rs : new RouteStopDAO(c).findByRouteOrdered(routeId)) {
                for (RequestWaste rw : new RequestWasteDAO(c).findByRequest(rs.getRequestId())) {
                    routeCategoryIds.add(rw.getCategoryId());
                }
            }

            Set<Long> alreadyDisposed = new HashSet<>();
            for (DisposalRecord dr : new DisposalRecordDAO(c).findByRoute(routeId)) {
                if (VERIFIED.equals(dr.getStatus())) {
                    for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(dr.getFacilityId())) {
                        if (routeCategoryIds.contains(faw.getCategoryId())) {
                            alreadyDisposed.add(faw.getCategoryId());
                        }
                    }
                }
            }

            Set<Long> remainingCatIds = new HashSet<>(routeCategoryIds);
            remainingCatIds.removeAll(alreadyDisposed);

            if (remainingCatIds.isEmpty()) {
                return Optional.empty();
            }

            DisposalFacilityDAO facDao = new DisposalFacilityDAO(c);

            // 1. First check if any active facility with remaining capacity accepts ALL remaining categories
            List<DisposalFacility> allMatchCandidates = facDao.findSuitableForCategories(remainingCatIds).stream()
                    .filter(f -> hasRemainingCapacity(c, f))
                    .collect(Collectors.toList());

            if (!allMatchCandidates.isEmpty()) {
                return allMatchCandidates.stream().min((a, b) -> {
                    double da = dist(fromLat, fromLon, a.getLatitude(), a.getLongitude());
                    double db = dist(fromLat, fromLon, b.getLatitude(), b.getLongitude());
                    return Double.compare(da, db);
                });
            }

            // 2. Fall back to active facilities with capacity that accept ANY of the remaining categories
            List<DisposalFacility> anyMatchCandidates = facDao.findAcceptingAnyCategory(remainingCatIds).stream()
                    .filter(f -> hasRemainingCapacity(c, f))
                    .collect(Collectors.toList());

            return anyMatchCandidates.stream().max((a, b) -> {
                // Count accepted remaining categories
                Set<Long> aCats = new HashSet<>();
                for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(a.getFacilityId())) {
                    if (remainingCatIds.contains(faw.getCategoryId())) aCats.add(faw.getCategoryId());
                }
                Set<Long> bCats = new HashSet<>();
                for (FacilityAcceptedWaste faw : new FacilityAcceptedWasteDAO(c).findByFacility(b.getFacilityId())) {
                    if (remainingCatIds.contains(faw.getCategoryId())) bCats.add(faw.getCategoryId());
                }
                int comp = Integer.compare(aCats.size(), bCats.size());
                if (comp != 0) return comp;
                // Closer distance wins
                double da = dist(fromLat, fromLon, a.getLatitude(), a.getLongitude());
                double db = dist(fromLat, fromLon, b.getLatitude(), b.getLongitude());
                return Double.compare(db, da); // reversed for max()
            });
        });
    }

    private static boolean hasRemainingCapacity(java.sql.Connection c, DisposalFacility f) {
        BigDecimal used = new DisposalRecordDAO(c).findByFacility(f.getFacilityId()).stream()
                .filter(dr -> VERIFIED.equals(dr.getStatus()) && dr.getDisposalDate() != null
                        && dr.getDisposalDate().toLocalDate().equals(java.time.LocalDate.now()))
                .map(DisposalRecord::getNetDisposedWeightKg)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal capacityKg = f.getCapacityTonnesPerDay().multiply(BigDecimal.valueOf(1000));
        return used.compareTo(capacityKg) < 0;
    }

    public List<DisposalFacility> findAllFacilities(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new DisposalFacilityDAO(c).findAll(); });
    }

    public Optional<DisposalRecord> findRecord(UserSession session, long routeId) {
        return database.read(c -> { Access.staff(c, session); return new DisposalRecordDAO(c).findLatestByRoute(routeId); });
    }

    public List<DisposalRecord> findRecordsByRoute(UserSession session, long routeId) {
        return database.read(c -> { Access.staff(c, session); return new DisposalRecordDAO(c).findByRoute(routeId); });
    }

    public List<DisposalRecord> findAllRecords(UserSession session) {
        return database.read(c -> { Access.staff(c, session); return new DisposalRecordDAO(c).findAll(); });
    }

    private static double dist(double lat1, double lon1, Double lat2, Double lon2) {
        if (lat2 == null || lon2 == null) return Double.MAX_VALUE;
        double dlat = lat2 - lat1, dlon = lon2 - lon1;
        return Math.sqrt(dlat * dlat + dlon * dlon);
    }
}
