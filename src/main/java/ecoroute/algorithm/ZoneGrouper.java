package ecoroute.algorithm;

import ecoroute.planning.RequestEligibility;
import ecoroute.planning.RequestView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * STEP 1 OF THE ALGORITHM PIPELINE
 *
 * Takes a flat list of pending requests and splits it into one bucket per
 * zone, so that vehicle assignment can then be solved independently inside
 * each zone.
 *
 *     [R1 z1, R2 z2, R3 z1, R4 z2, R5 z1]
 *                    |
 *                    v
 *     { 1 -> [R1, R3, R5],  2 -> [R2, R4] }
 *
 * WHY GROUP BY ZONE AT ALL
 * ------------------------
 * Three independent reasons, all worth saying in a viva:
 *
 *  1. OPERATIONAL - real collection fleets work in fixed service areas. A
 *     truck does not cross the city for one pickup.
 *  2. ALGORITHMIC - it decomposes one large bin-packing problem into several
 *     small independent ones. Smaller inputs mean a greedy heuristic behaves
 *     far better, and each zone can be planned, reviewed or re-run alone.
 *  3. SCOPE - true geographic clustering (k-means and friends) is explicitly
 *     out of scope. Predefined zones are the project's deliberate, documented
 *     simplification.
 *
 *     Honest limitation to state up front: this is NOT geographic clustering.
 *     Zone boundaries are an administrative decision made by whoever seeds
 *     the ZONE table, not something this algorithm optimises.
 *
 * WHERE THE ZONE COMES FROM
 * -------------------------
 * PICKUP_REQUEST has no zone_id - that is a deliberate schema rule, because
 * storing it there would duplicate data already held on WASTE_GENERATOR and
 * could drift out of step with it. The zone is derived through
 * generator_id -> WASTE_GENERATOR.zone_id, and RequestView already carries
 * the resolved value.
 *
 * WHY TreeMap AND NOT HashMap  (the determinism trap)
 * ---------------------------------------------------
 * HashMap has NO guaranteed iteration order. Loop over a HashMap and the
 * zones may come out 2,1,3 on your laptop and 3,1,2 on the examiner's -
 * which means a different vehicle assignment, a different route, and a demo
 * that does not match your rehearsal.
 *
 * TreeMap keeps keys in sorted order, so zones always come out 1,2,3.
 * The work plan demands determinism three separate times. This one word -
 * TreeMap instead of HashMap - is a large part of how we deliver it.
 *
 * COMPLEXITY
 *   O(R log Z) for grouping R requests into Z zones (TreeMap insert is log Z),
 *   plus O(R log R) for the per-zone sort. At this project's scale - tens of
 *   requests in a day - both are instant.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class ZoneGrouper {

    /**
     * Deterministic ordering of requests INSIDE a zone bucket: ascending
     * request id.
     *
     * Why sort at all? Because the list arrives from a SQL query, and a
     * SELECT without ORDER BY has no guaranteed row order. Sorting here means
     * the grouping output is identical on every run regardless of what the
     * database happened to return.
     *
     * This is only a stable BASELINE order. VehicleAssignment re-sorts by
     * quantity descending for its First-Fit Decreasing strategy - but because
     * it starts from a known order, its tie-breaking is also deterministic.
     */
    private static final Comparator<RequestView> BY_REQUEST_ID =
            new Comparator<RequestView>() {
                @Override
                public int compare(RequestView a, RequestView b) {
                    return Long.compare(a.getRequestId(), b.getRequestId());
                }
            };

    /**
     * Group requests by zone, with NO filtering.
     *
     * Use this when the caller has already filtered the list (for example
     * when the port guarantees it returned only PENDING rows).
     *
     * @param requests may be null or empty
     * @return zoneId -> requests, zones in ascending id order, each bucket
     *         sorted by request id. Never null.
     */
    public Map<Long, List<RequestView>> groupByZone(List<RequestView> requests) {
        Map<Long, List<RequestView>> grouped = new TreeMap<Long, List<RequestView>>();
        if (requests == null) return grouped;

        for (RequestView request : requests) {
            if (request == null) continue;          // defensive: never trust input
            Long zoneId = Long.valueOf(request.getZoneId());

            List<RequestView> bucket = grouped.get(zoneId);
            if (bucket == null) {
                bucket = new ArrayList<RequestView>();
                grouped.put(zoneId, bucket);
            }
            bucket.add(request);
        }

        for (List<RequestView> bucket : grouped.values()) {
            Collections.sort(bucket, BY_REQUEST_ID);
        }
        return grouped;
    }

    /**
     * Group ONLY the requests that may legally be planned onto routeDate,
     * and report the rest.
     *
     * This is the method the planning service actually uses, because it
     * applies the eligibility rules - PENDING only, positive quantity, and
     * the preferred-pickup-date rule that Member 2's save() enforces.
     *
     * @param requests  candidate requests
     * @param routeDate the date the routes are being planned for
     * @param rejectedOut if non-null, every skipped request is added here so
     *                    the caller can report it instead of losing it
     *                    silently. Passing a collection in to be filled is
     *                    the "output parameter" idiom - used sparingly, it
     *                    avoids inventing yet another result wrapper class.
     */
    public Map<Long, List<RequestView>> groupEligibleByZone(List<RequestView> requests,
                                                            LocalDate routeDate,
                                                            List<RequestView> rejectedOut) {
        List<RequestView> eligible = new ArrayList<RequestView>();
        if (requests != null) {
            for (RequestView request : requests) {
                if (request == null) continue;
                if (RequestEligibility.isEligible(request, routeDate)) {
                    eligible.add(request);
                } else if (rejectedOut != null) {
                    rejectedOut.add(request);
                }
            }
        }
        return groupByZone(eligible);
    }

    /**
     * The zone ids that actually have work, in ascending order.
     * Convenience for the service, which loops zone by zone.
     */
    public List<Long> zonesWithWork(Map<Long, List<RequestView>> grouped) {
        return new ArrayList<Long>(grouped.keySet());
    }

    /** Total requests across all buckets - handy for assertions and reports. */
    public int countAll(Map<Long, List<RequestView>> grouped) {
        int total = 0;
        for (List<RequestView> bucket : grouped.values()) total += bucket.size();
        return total;
    }

    /** A readable dump of the grouping, for console demos and debugging. */
    public String describe(Map<Long, List<RequestView>> grouped) {
        StringBuilder sb = new StringBuilder();
        sb.append("Zone grouping: ").append(grouped.size()).append(" zone(s), ")
          .append(countAll(grouped)).append(" request(s)\n");
        for (Map.Entry<Long, List<RequestView>> entry : grouped.entrySet()) {
            sb.append("  Zone ").append(entry.getKey())
              .append(" (").append(entry.getValue().size()).append(" request(s)):\n");
            for (RequestView request : entry.getValue()) {
                sb.append("      ").append(request).append('\n');
            }
        }
        return sb.toString();
    }
}
