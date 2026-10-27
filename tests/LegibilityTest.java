package com.studio72f.freedom;

public class LegibilityTest {
    static int passed;
    public static void main(String[] args) {
        check(21.0, Math.round(Legibility.contrast(1, 0) * 10) / 10.0);
        // Forest measurements: white text over bright spots (0.28) at 20% dim on a black theme → about 3:1, needs Strong.
        check(Legibility.STRONG, Legibility.auto(1, 0, .02, .28, .2));
        // The same photo at 60% dim clears 4.5:1 → Subtle is enough.
        check(Legibility.SUBTLE, Legibility.auto(1, 0, .02, .28, .6));
        // Dark text (paper theme) worries about dark spots, dimmed toward a light background.
        check(Legibility.STRONG, Legibility.auto(.01, .9, .02, .5, .2));
        check(Legibility.SUBTLE, Legibility.auto(.01, .9, .02, .5, .8));
        check(Legibility.OFF, Legibility.resolve("auto", false, Legibility.STRONG));
        check(Legibility.STRONG, Legibility.resolve("auto", true, Legibility.STRONG));
        check(Legibility.SUBTLE, Legibility.resolve("subtle", true, Legibility.STRONG));
        check(Legibility.OFF, Legibility.resolve("off", true, Legibility.STRONG));
        check(.15, Legibility.extraDim(Legibility.STRONG));
        System.out.println(passed + " legibility tests passed");
    }
    static void check(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual); passed++; }
}
