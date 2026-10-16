package ecoroute.harness;

import java.math.BigDecimal;

/**
 * A tiny zero-dependency test harness that prints a readable PASS/FAIL report.
 *
 * WHY NOT JUNIT, GIVEN THE POM ALREADY HAS IT?
 * --------------------------------------------
 * We can add JUnit wrappers later and they will run under `mvn test`. But for
 * the live demonstration this is better: one command produces a labelled,
 * colour-coded list of every mandatory scenario and its result, readable from
 * across the room. "Show me what happens when a request is too large" is then
 * answered by pointing at a line, not by opening source code.
 *
 * It also has zero dependencies, so it cannot break because of a jar that
 * failed to download ten minutes before an evaluation.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class Check {

    private Check() { }

    private static int passed = 0;
    private static int failed = 0;
    private static int suiteCount = 0;

    public static void reset() {
        passed = 0;
        failed = 0;
        suiteCount = 0;
    }

    public static int getPassed() { return passed; }
    public static int getFailed() { return failed; }
    public static int getSuiteCount() { return suiteCount; }

    public static void assertNoFailures() {
        if (failed > 0) {
            int errs = failed;
            throw new AssertionError("Check harness reported " + errs + " failure(s)");
        }
    }

    private static final String GREEN = "\u001B[32m";
    private static final String RED   = "\u001B[31m";
    private static final String BOLD  = "\u001B[1m";
    private static final String CYAN  = "\u001B[36m";
    private static final String DIM   = "\u001B[2m";
    private static final String RESET = "\u001B[0m";

    public static void suite(String name) {
        suiteCount++;
        System.out.println();
        System.out.println(BOLD + CYAN + "=== " + name + " ===" + RESET);
    }

    /** A free-text explanatory line inside a suite. */
    public static void note(String text) {
        System.out.println("    " + DIM + text + RESET);
    }

    public static void isTrue(String label, boolean condition) {
        if (condition) pass(label); else fail(label, "expected true, got false");
    }

    public static void isFalse(String label, boolean condition) {
        if (!condition) pass(label); else fail(label, "expected false, got true");
    }

    public static void eq(String label, Object expected, Object actual) {
        boolean ok = (expected == null) ? (actual == null) : expected.equals(actual);
        if (ok) pass(label);
        else fail(label, "expected <" + expected + "> but got <" + actual + ">");
    }

    public static void eq(String label, long expected, long actual) {
        if (expected == actual) pass(label);
        else fail(label, "expected <" + expected + "> but got <" + actual + ">");
    }

    /**
     * BigDecimal comparison by VALUE, using compareTo.
     *
     * Deliberately NOT equals(): BigDecimal.equals() also compares scale, so
     * new BigDecimal("100.0").equals(new BigDecimal("100.00")) is FALSE even
     * though both are one hundred. Every quantity assertion in this project
     * must use compareTo, and this method makes that automatic.
     */
    public static void eqDecimal(String label, String expected, BigDecimal actual) {
        if (actual != null && new BigDecimal(expected).compareTo(actual) == 0) {
            pass(label + "  " + DIM + "(" + actual + ")" + RESET);
        } else {
            fail(label, "expected <" + expected + "> but got <" + actual + ">");
        }
    }

    public static void eqDouble(String label, double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) <= tolerance) pass(label);
        else fail(label, "expected <" + expected + "> but got <" + actual
                       + "> (tolerance " + tolerance + ")");
    }

    public static void notNull(String label, Object value) {
        if (value != null) pass(label); else fail(label, "expected a value, got null");
    }

    public static void isNull(String label, Object value) {
        if (value == null) pass(label); else fail(label, "expected null, got <" + value + ">");
    }

    /** Assert that a piece of code throws a particular exception type. */
    public static void throwsType(String label, Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
            fail(label, "expected " + expected.getSimpleName() + " but nothing was thrown");
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                pass(label + "  " + DIM + "[" + t.getClass().getSimpleName() + "]" + RESET);
            } else {
                fail(label, "expected " + expected.getSimpleName() + " but got "
                          + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    public static void doesNotThrow(String label, Runnable action) {
        try {
            action.run();
            pass(label);
        } catch (Throwable t) {
            fail(label, "unexpected " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void pass(String label) {
        passed++;
        System.out.println("  " + GREEN + "PASS" + RESET + "  " + label);
    }

    private static void fail(String label, String detail) {
        failed++;
        System.out.println("  " + RED + "FAIL" + RESET + "  " + label);
        System.out.println("        " + RED + detail + RESET);
    }

    /** @return 0 when everything passed, 1 otherwise - suitable for System.exit(). */
    public static int report() {
        int total = passed + failed;
        System.out.println();
        System.out.println(BOLD + "---------------------------------------------------" + RESET);
        if (failed == 0) {
            System.out.println(BOLD + GREEN + "  ALL TESTS PASSED" + RESET
                    + "   " + passed + "/" + total + " checks across " + suiteCount + " suite(s)");
        } else {
            System.out.println(BOLD + RED + "  TESTS FAILED" + RESET
                    + "   " + passed + " passed, " + failed + " failed, " + total + " total");
        }
        System.out.println(BOLD + "---------------------------------------------------" + RESET);
        return failed == 0 ? 0 : 1;
    }
}
