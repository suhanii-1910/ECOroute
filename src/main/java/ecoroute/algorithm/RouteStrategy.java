package ecoroute.algorithm;

import ecoroute.planning.RequestView;
import ecoroute.planning.StopPlan;

import java.util.List;

/**
 * THE STRATEGY PATTERN - the designated OOP extension point of this project.
 *
 * A RouteStrategy takes the requests loaded onto ONE vehicle and decides the
 * order in which the driver should visit them.
 *
 * =====================================================================
 *  WHY AN INTERFACE INSTEAD OF JUST WRITING THE METHOD
 * =====================================================================
 * The rest of the system depends on this CONTRACT, never on a specific
 * algorithm:
 *
 *     RouteStrategy strategy = new NearestNeighborRoute();   // today
 *     RouteStrategy strategy = new TwoOptRoute();            // next semester
 *     List<StopPlan> stops = strategy.order(requests);       // unchanged
 *
 * RoutePlanningService holds a RouteStrategy, not a NearestNeighborRoute.
 * A better routing algorithm can therefore be added as a NEW CLASS without
 * modifying one line of existing, tested code.
 *
 * The named principles - all fair viva questions:
 *
 *   STRATEGY PATTERN      a family of interchangeable algorithms, each in
 *                         its own class, selected at runtime.
 *   OPEN/CLOSED PRINCIPLE open to extension (add a class), closed to
 *                         modification (don't edit what works).
 *   POLYMORPHISM          strategy.order(...) runs different code depending
 *                         on the actual object, decided at runtime.
 *   DEPENDENCY INVERSION  the service depends on this abstraction, not on a
 *                         concrete implementation.
 *
 * The project brief calls this out explicitly: "A RouteStrategy interface can
 * provide a clean OOP extension point. NearestNeighborRoute can implement the
 * interface, allowing a more advanced routing strategy to be introduced later
 * without rewriting the rest of the application."
 *
 * =====================================================================
 *  WHY AN INTERFACE AND NOT AN ABSTRACT CLASS
 * =====================================================================
 * Route ordering is a pure CAPABILITY. There is no shared state and no
 * shared implementation worth inheriting, so there is nothing for an
 * abstract class to contribute. The Member 3 work plan is explicit:
 * "Abstract classes - use only if a shared abstraction genuinely improves
 * the design." Inventing a hierarchy purely to show off inheritance is
 * exactly the artificial complexity the plan warns against.
 *
 *     Interface      : no state, no constructor, a class may implement many
 *     Abstract class : can hold state and code, only one may be extended
 *
 * =====================================================================
 *  THE CONTRACT EVERY IMPLEMENTATION MUST HONOUR
 * =====================================================================
 *   1. Return EVERY input request exactly once - nothing added, nothing lost.
 *   2. Assign stopSequence as 1, 2, 3 ... N with no gaps and no duplicates.
 *      The database enforces UNIQUE(route_id, stop_sequence) and Member 2
 *      validates "positive, unique and supplied in ascending order".
 *   3. Be DETERMINISTIC - identical input must give identical output.
 *   4. Never throw on an empty or single-element list.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public interface RouteStrategy {

    /**
     * Decide the visiting order for one vehicle's requests.
     *
     * @param requests the requests loaded onto one vehicle; may be empty
     * @return ordered stops with stopSequence 1..N, never null
     */
    List<StopPlan> order(List<RequestView> requests);

    /** Short name for logs, reports and the UI, e.g. "Nearest Neighbour". */
    String getName();

    /**
     * An honest one-line statement of what this strategy does and does NOT
     * guarantee.
     *
     * Putting this in the interface forces every implementation to declare
     * its own limitations. It is also directly useful: Member 4 can print it
     * under the route table, so the demonstration itself states that the
     * result is a heuristic rather than an optimum. That is exactly the
     * intellectual honesty the project brief asks for.
     */
    String getDescription();
}
