package com.studio72f.freedom;

import java.util.*;

public class ContextRulesTest {
    static int passed;
    public static void main(String[] args) {
        ContextRules.Rule morning = ContextRules.template("morning", "m");
        check(true, ContextRules.matches(morning, at(0, 7 * 60)));       // Monday 07:00
        check(false, ContextRules.matches(morning, at(0, 10 * 60)));     // end is exclusive
        check(false, ContextRules.matches(morning, at(5, 7 * 60)));      // Saturday

        ContextRules.Rule night = ContextRules.template("night", "n");
        night.days = 1 << 4;                                              // Friday only
        check(true, ContextRules.matches(night, at(4, 23 * 60)));        // Fri 23:00
        check(true, ContextRules.matches(night, at(5, 60)));             // Sat 01:00 belongs to Friday
        check(false, ContextRules.matches(night, at(4, 60)));            // Fri 01:00 belongs to Thursday
        check(false, ContextRules.matches(night, at(5, 23 * 60)));

        ContextRules.Rule phones = ContextRules.template("headphones", "h");
        ContextRules.Signals s = at(2, 12 * 60);
        check(false, ContextRules.matches(phones, s));
        s.headphones = true;
        check(true, ContextRules.matches(phones, s));
        phones.enabled = false;
        check(false, ContextRules.matches(phones, s));
        phones.enabled = true;

        ArrayList<ContextRules.Rule> rules = new ArrayList<>(Arrays.asList(ContextRules.everyday(true, false), phones, morning));
        ContextRules.normalize(rules);
        check("everyday", rules.get(rules.size() - 1).id);
        check("h", ContextRules.resolve(rules, s, null).id);
        check("m", ContextRules.resolve(rules, s, "m").id);              // pinned override
        check("everyday", ContextRules.resolve(rules, at(6, 0), null).id);

        check("weekdays 06:00–10:00", ContextRules.describe(morning));
        check("headphones", ContextRules.describe(phones));
        check("22:00–06:00", ContextRules.describe(ContextRules.template("night", "x")));
        System.out.println(passed + " context rule tests passed");
    }
    static ContextRules.Signals at(int day, int minute) { return new ContextRules.Signals(day, minute); }
    static void check(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual); passed++; }
}
