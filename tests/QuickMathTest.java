package com.studio72f.eigen;

/** Sums and unit conversions for search and the assistant. */
public final class QuickMathTest {
    private static int run;
    private static void check(String query, String expected) {
        String got = QuickMath.answer(query);
        if (expected == null ? got != null : !expected.equals(got)) throw new AssertionError(query + ": expected " + expected + ", got " + got);
        run++;
    }
    public static void main(String[] args) {
        check("12 x 4", "12 x 4 = 48");
        check("2+3*4", "2 + 3 × 4 = 14");
        check("(2+3)*4", "(2 + 3) × 4 = 20");
        check("2^10", "2^10 = 1024");
        check("100/3", "100 ÷ 3 = 33.3333");
        check("18% of 2400", "18% of 2400 = 432");
        check("what is 7 * 6?", "7 × 6 = 42");
        check("sqrt(81)", "sqrt(81) = 9");
        check("-5 + 2", "-5 + 2 = -3");
        check("1,200 + 300", "1,200 + 300 = 1500");
        check("5 km in miles", "5 km = 3.1069 mi");
        check("72 f to c", "72 °f = 22.2222 °c");
        check("100 c in f", "100 °c = 212 °f");
        check("3.5 kg lb", "3.5 kg = 7.7162 lb");
        check("1 gb to mb", "1 gb = 1000 mb");
        check("90 min in hours", "90 min = 1.5 h");
        check("60 mph to km/h", "60 mph = 96.5606 km/h");
        // Not maths: names, bare numbers, unknown or mismatched units.
        check("whatsapp", null);
        check("2048", null);
        check("5 km in kg", null);
        check("spotify", null);
        check("1/0", null);
        check("2 (", null);
        System.out.println("QuickMathTest: " + run + " checks passed");
    }
}
