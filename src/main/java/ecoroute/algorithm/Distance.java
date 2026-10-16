package ecoroute.algorithm;

import ecoroute.planning.RequestView;

/**
 * Straight-line distance between two generator locations.
 *
 * =====================================================================
 *  WHY THIS IS AN APPROXIMATION - AND WHY THAT IS ACCEPTABLE
 * =====================================================================
 * We use EUCLIDEAN distance on raw latitude/longitude degrees:
 *
 *      d = sqrt( (lat1 - lat2)^2 + (lon1 - lon2)^2 )
 *
 * That is geographically wrong in two separate ways, and you must be able
 * to name both:
 *
 *  1. LAT/LONG ARE ANGLES ON A SPHERE, NOT FLAT X/Y COORDINATES.
 *     One degree of latitude is always about 111 km. One degree of
 *     longitude is 111 km at the equator but shrinks towards the poles -
 *     at Pune's latitude of roughly 18.5 N it is about 105 km. Treating
 *     both axes as equal therefore stretches east-west distances by a few
 *     percent relative to north-south ones.
 *
 *  2. IT IS STRAIGHT-LINE DISTANCE, NOT ROAD DISTANCE.
 *     A truck cannot drive through buildings. Real travel distance needs
 *     a road network, which needs a mapping API - explicitly out of scope
 *     for this project.
 *
 *  WHY IT IS STILL FINE HERE
 *     The value is never reported to a user as a distance. It is used only
 *     to RANK candidate next stops - "which unvisited stop is closest?"
 *     Within a single city zone, spanning a few kilometres, a uniform few-
 *     percent distortion almost never changes which stop is nearest. The
 *     ordering is what matters, and the ordering is stable.
 *
 *     The Member 3 work plan states this directly: "Treat latitude/longitude
 *     distance as an academic approximation only."
 *
 *  THE CORRECT ALTERNATIVE, IF ASKED
 *     The HAVERSINE formula gives true great-circle distance on a sphere:
 *         a = sin^2(dLat/2) + cos(lat1) cos(lat2) sin^2(dLon/2)
 *         d = 2R * atan2(sqrt(a), sqrt(1-a))     with R = 6371 km
 *     It is maybe eight lines of code. We deliberately do NOT use it,
 *     because it would make the output look authoritative while still
 *     ignoring roads - a false sense of precision. Honest approximation
 *     beats misleading precision. Name Haversine as known future scope.
 *
 * =====================================================================
 *  WHY double AND NOT BigDecimal
 * =====================================================================
 * Quantities use BigDecimal because exactness decides whether Member 2's
 * capacity check passes. Distance decides only a sort order, is already
 * approximate, and Math.sqrt returns a double anyway. Using BigDecimal
 * here would add cost and complexity while buying nothing.
 *
 * Knowing WHICH values need exactness and which do not is a real
 * engineering judgement, and a good thing to be asked about.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class Distance {

    private Distance() { }

    /**
     * Returned when either point has no coordinates. Using the largest
     * possible double means such a stop always sorts LAST among candidates
     * in a nearest-neighbour scan, instead of crashing on a null or - worse -
     * being treated as distance zero and jumped to first.
     */
    public static final double UNKNOWN = Double.MAX_VALUE;

    /**
     * Straight-line distance in DEGREES between two requests' generators.
     *
     * The unit is degrees, not kilometres, and that is deliberate: the number
     * is a ranking key, never a figure shown to a user. Labelling it "km"
     * would imply an accuracy it does not have.
     *
     * @return the distance, or UNKNOWN if either point lacks coordinates
     */
    public static double between(RequestView a, RequestView b) {
        if (a == null || b == null) return UNKNOWN;
        if (!a.hasCoordinates() || !b.hasCoordinates()) return UNKNOWN;

        double deltaLatitude  = a.getLatitude().doubleValue()  - b.getLatitude().doubleValue();
        double deltaLongitude = a.getLongitude().doubleValue() - b.getLongitude().doubleValue();

        return Math.sqrt(deltaLatitude * deltaLatitude + deltaLongitude * deltaLongitude);
    }

    /**
     * A rough conversion to kilometres, for display only.
     *
     * Uses 111 km per degree, the average length of one degree of latitude.
     * This is an ESTIMATE and is labelled as such everywhere it appears.
     * Never use it in a calculation that anything depends on.
     */
    public static double toApproxKilometres(double degrees) {
        if (degrees == UNKNOWN) return 0.0;
        return degrees * 111.0;
    }
}
