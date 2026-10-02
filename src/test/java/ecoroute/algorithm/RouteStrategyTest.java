package ecoroute.algorithm;

import ecoroute.fake.FakeData;
import ecoroute.harness.Check;
import ecoroute.planning.RequestView;
import ecoroute.planning.StopPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PHASE 6 TEST - RouteStrategy, NearestNeighborRoute and RequestOrderRoute.
 *
 * Covers mandatory scenarios 7 and 8, and demonstrates the Strategy pattern
 * with a measured comparison between two implementations.
 *
 * Run with:
 *     mvn -q clean test-compile
 *     java -cp target/classes:target/test-classes ecoroute.algorithm.RouteStrategyTest
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public class RouteStrategyTest {

    public static void main(String[] args) {

        NearestNeighborRoute nearest = new NearestNeighborRoute();

        // ==============================================================
        Check.suite("SCENARIO 7 - stop_sequence must be unique 1..N");
        Check.note("The DB enforces UNIQUE(route_id, stop_sequence); we must not rely on that.");
        // ==============================================================
        List<RequestView> five = new ArrayList<RequestView>();
        five.add(FakeData.requestAt(1L, 1L, "100.000", 18.5000, 73.8000));
        five.add(FakeData.requestAt(2L, 1L, "100.000", 18.5100, 73.8100));
        five.add(FakeData.requestAt(3L, 1L, "100.000", 18.5200, 73.8200));
        five.add(FakeData.requestAt(4L, 1L, "100.000", 18.5300, 73.8300));
        five.add(FakeData.requestAt(5L, 1L, "100.000", 18.5400, 73.8400));

        List<StopPlan> plan = nearest.order(five);

        Check.eq("all 5 requests appear in the plan", 5, plan.size());

        Set<Integer> sequences = new HashSet<Integer>();
        boolean contiguous = true;
        for (int i = 0; i < plan.size(); i++) {
            sequences.add(Integer.valueOf(plan.get(i).getStopSequence()));
            if (plan.get(i).getStopSequence() != i + 1) contiguous = false;
        }
        Check.eq("there are 5 DISTINCT stop sequences", 5, sequences.size());
        Check.isTrue("they run 1,2,3,4,5 with no gaps", contiguous);
        Check.isTrue("the first sequence is 1, not 0", plan.get(0).getStopSequence() == 1);
        Check.note("Member 2 validates 'positive, unique and supplied in ascending order'.");

        Set<Long> requestIds = new HashSet<Long>();
        for (StopPlan stop : plan) requestIds.add(Long.valueOf(stop.getRequestId()));
        Check.eq("no request is duplicated - UNIQUE(request_id) is safe", 5, requestIds.size());

        // ==============================================================
        Check.suite("SCENARIO 8 - 3+ coordinates, deterministic order");
        // ==============================================================
        List<RequestView> scattered = new ArrayList<RequestView>();
        scattered.add(FakeData.requestAt(10L, 1L, "100.000", 18.5000, 73.8000));  // origin
        scattered.add(FakeData.requestAt(11L, 1L, "100.000", 18.5900, 73.8900));  // far
        scattered.add(FakeData.requestAt(12L, 1L, "100.000", 18.5050, 73.8050));  // very near 10
        scattered.add(FakeData.requestAt(13L, 1L, "100.000", 18.5400, 73.8400));  // middle

        List<StopPlan> route = nearest.order(scattered);

        Check.eq("starts at the lowest request id (10)", 10L, route.get(0).getRequestId());
        Check.eq("then hops to its nearest neighbour (12)", 12L, route.get(1).getRequestId());
        Check.eq("then the middle one (13)", 13L, route.get(2).getRequestId());
        Check.eq("then the far one last (11)", 11L, route.get(3).getRequestId());
        Check.note("Classic NN behaviour: grab the near ones, leave the long leg for last.");

        StringBuilder signature = new StringBuilder();
        for (StopPlan stop : route) signature.append(stop.getRequestId()).append(',');

        boolean stable = true;
        for (int i = 0; i < 50; i++) {
            StringBuilder again = new StringBuilder();
            for (StopPlan stop : nearest.order(scattered)) again.append(stop.getRequestId()).append(',');
            if (!signature.toString().equals(again.toString())) stable = false;
        }
        Check.isTrue("50 runs produce the IDENTICAL stop order", stable);

        // Same points, scrambled input order -> same route.
        List<RequestView> scrambled = new ArrayList<RequestView>();
        scrambled.add(scattered.get(2));
        scrambled.add(scattered.get(0));
        scrambled.add(scattered.get(3));
        scrambled.add(scattered.get(1));
        StringBuilder fromScrambled = new StringBuilder();
        for (StopPlan stop : nearest.order(scrambled)) fromScrambled.append(stop.getRequestId()).append(',');
        Check.eq("a scrambled input gives the same route", signature.toString(), fromScrambled.toString());

        System.out.println();
        System.out.println(indent(nearest.describe(route)));

        // ==============================================================
        Check.suite("Equidistant stops - the tie-break rule");
        // ==============================================================
        List<RequestView> tied = new ArrayList<RequestView>();
        tied.add(FakeData.requestAt(20L, 1L, "10.000", 18.5000, 73.8000));   // start
        tied.add(FakeData.requestAt(22L, 1L, "10.000", 18.5100, 73.8000));   // exactly 0.01 north
        tied.add(FakeData.requestAt(21L, 1L, "10.000", 18.4900, 73.8000));   // exactly 0.01 south

        List<StopPlan> tiedRoute = nearest.order(tied);
        Check.eq("starts at the lowest id (20)", 20L, tiedRoute.get(0).getRequestId());
        Check.eq("two stops are equidistant - the LOWER id (21) wins",
                 21L, tiedRoute.get(1).getRequestId());
        Check.note("Without an epsilon + id tie-break this would be decided by rounding noise.");

        // ==============================================================
        Check.suite("Missing coordinates are visited, never dropped");
        // ==============================================================
        List<RequestView> mixed = new ArrayList<RequestView>();
        mixed.add(FakeData.requestAt(30L, 1L, "10.000", 18.5000, 73.8000));
        mixed.add(FakeData.requestWithoutCoordinates());                 // id 109, no lat/long
        mixed.add(FakeData.requestAt(31L, 1L, "10.000", 18.5100, 73.8100));

        List<StopPlan> mixedRoute = nearest.order(mixed);
        Check.eq("all 3 stops are present - none silently dropped", 3, mixedRoute.size());
        Check.eq("the unlocatable stop is appended LAST", 109L,
                 mixedRoute.get(2).getRequestId());
        Check.eq("it still receives a valid stop_sequence", 3,
                 mixedRoute.get(2).getStopSequence());
        Check.note("A dropped stop means a generator is never collected. Correctness first.");

        // ==============================================================
        Check.suite("Contract edge cases - must never throw");
        // ==============================================================
        Check.eq("an empty list returns an empty plan", 0,
                 nearest.order(new ArrayList<RequestView>()).size());
        Check.eq("a null list returns an empty plan", 0, nearest.order(null).size());

        List<RequestView> single = new ArrayList<RequestView>();
        single.add(FakeData.request(40L, 1L, "50.000"));
        List<StopPlan> singlePlan = nearest.order(single);
        Check.eq("a single stop produces one stop", 1, singlePlan.size());
        Check.eq("...numbered 1", 1, singlePlan.get(0).getStopSequence());
        Check.eqDouble("...with zero distance from previous", 0.0,
                 singlePlan.get(0).getDistanceFromPrevious(), 0.0000001);

        // ==============================================================
        Check.suite("THE STRATEGY PATTERN - two implementations, one contract");
        Check.note("This is the proof that RouteStrategy is a real abstraction.");
        // ==============================================================
        List<RouteStrategy> strategies = new ArrayList<RouteStrategy>();
        strategies.add(new NearestNeighborRoute());
        strategies.add(new RequestOrderRoute());

        Check.eq("two strategies implement the same interface", 2, strategies.size());

        System.out.println();
        for (RouteStrategy strategy : strategies) {
            // POLYMORPHISM: identical call, different algorithm at runtime.
            List<StopPlan> result = strategy.order(scattered);
            double total = NearestNeighborRoute.totalDistance(result);

            StringBuilder order = new StringBuilder();
            for (StopPlan stop : result) order.append(stop.getRequestId()).append(' ');

            System.out.println(String.format("      %-22s order: %-14s total: %.5f deg (~%.2f km)",
                    strategy.getName(), order.toString().trim(), total,
                    ecoroute.algorithm.Distance.toApproxKilometres(total)));
            System.out.println("        " + strategy.getDescription());
            System.out.println();
        }

        double nnTotal   = NearestNeighborRoute.totalDistance(new NearestNeighborRoute().order(scattered));
        double fifoTotal = NearestNeighborRoute.totalDistance(new RequestOrderRoute().order(scattered));

        Check.isTrue("MEASURED: nearest-neighbour beats the naive FIFO baseline",
                     nnTotal < fifoTotal);
        System.out.println(String.format("      Improvement: %.5f -> %.5f degrees (%.1f%% shorter)",
                fifoTotal, nnTotal, (1 - nnTotal / fifoTotal) * 100));
        Check.note("A measured claim, not an asserted one.");

        Check.notNull("every strategy states its own limitations",
                      new NearestNeighborRoute().getDescription());
        Check.isTrue("...and admits it is not optimal",
                new NearestNeighborRoute().getDescription().contains("NOT guarantee"));

        System.exit(Check.report());
    }

    private static String indent(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n")) sb.append("      ").append(line).append('\n');
        return sb.toString();
    }
}
