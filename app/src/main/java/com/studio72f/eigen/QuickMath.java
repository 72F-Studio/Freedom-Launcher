package com.studio72f.eigen;

import java.util.*;

/**
 * Sums and unit conversions for search and the assistant, on the phone. Pure Java so it is testable off-device.
 * Arithmetic: + − × ÷ ^ %, brackets, "18% of 2400", sqrt. Units: length, mass, volume, temperature, speed, data,
 * time and area, written "5 km in miles", "72 f to c" or "3.5 kg lb".
 */
public final class QuickMath {
    private QuickMath() { }

    /** The answer to [query] ("12 × 4 = 48", "5 km = 3.107 mi"), or null when it isn't maths. */
    public static String answer(String query) {
        if (query == null) return null;
        String q = query.trim().toLowerCase(Locale.ROOT).replaceAll("^(calc|calculate|what is|what's|convert)\\s+", "").replaceAll("[?=]+$", "").trim();
        if (q.isEmpty()) return null;
        String units = convert(q);
        if (units != null) return units;
        // Must contain an operator and a digit, so a bare number or an app name never reads as a sum.
        if (!q.matches(".*\\d.*") || !q.matches(".*([+\\-*/x×÷^%]|sqrt|of).*")) return null;
        Double v = evaluate(q);
        if (v == null || v.isNaN() || v.isInfinite()) return null;
        return pretty(q) + " = " + format(v);
    }

    // ---- Arithmetic: a small recursive-descent parser.

    public static Double evaluate(String raw) {
        String e = raw.replace("×", "*").replace("÷", "/").replace(",", "").replaceAll("(?<=\\d)\\s*x\\s*(?=[\\d(])", "*").replaceAll("\\s+", "");
        e = e.replaceAll("([0-9.]+)%of", "($1/100)*");
        try {
            Parser p = new Parser(e);
            double v = p.expr();
            if (p.pos != e.length()) return null;
            return v;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static final class Parser {
        final String s; int pos;
        Parser(String s) { this.s = s; }
        char peek() { return pos < s.length() ? s.charAt(pos) : '\0'; }
        double expr() {
            double v = term();
            while (true) {
                char c = peek();
                if (c == '+') { pos++; v += term(); } else if (c == '-') { pos++; v -= term(); } else return v;
            }
        }
        double term() {
            double v = power();
            while (true) {
                char c = peek();
                if (c == '*') { pos++; v *= power(); } else if (c == '/') { pos++; v /= power(); } else return v;
            }
        }
        double power() {
            double base = unary();
            if (peek() == '^') { pos++; return Math.pow(base, power()); }
            return base;
        }
        double unary() {
            if (peek() == '-') { pos++; return -unary(); }
            if (peek() == '+') { pos++; return unary(); }
            return postfix();
        }
        double postfix() {
            double v = atom();
            while (peek() == '%') { pos++; v /= 100; }
            return v;
        }
        double atom() {
            if (s.startsWith("sqrt", pos)) { pos += 4; return Math.sqrt(atom()); }
            if (s.startsWith("pi", pos)) { pos += 2; return Math.PI; }
            if (peek() == '(') { pos++; double v = expr(); if (peek() != ')') throw new IllegalStateException("bracket"); pos++; return v; }
            int start = pos;
            while (Character.isDigit(peek()) || peek() == '.') pos++;
            if (start == pos) throw new IllegalStateException("number");
            return Double.parseDouble(s.substring(start, pos));
        }
    }

    // ---- Units. Each unit is a factor to its dimension's base unit (temperature is special-cased).

    private static final class Unit {
        final String dim, symbol; final double factor;
        Unit(String dim, String symbol, double factor) { this.dim = dim; this.symbol = symbol; this.factor = factor; }
    }

    private static final Map<String, Unit> UNITS = new HashMap<>();
    private static void unit(String dim, String symbol, double factor, String... names) {
        Unit u = new Unit(dim, symbol, factor);
        UNITS.put(symbol, u);
        for (String n : names) UNITS.put(n, u);
    }
    static {
        unit("length", "mm", 0.001, "millimetre", "millimetres", "millimeter", "millimeters");
        unit("length", "cm", 0.01, "centimetre", "centimetres", "centimeter", "centimeters");
        unit("length", "m", 1, "metre", "metres", "meter", "meters");
        unit("length", "km", 1000, "kilometre", "kilometres", "kilometer", "kilometers", "kms");
        unit("length", "in", 0.0254, "inch", "inches", "\"");
        unit("length", "ft", 0.3048, "foot", "feet", "'");
        unit("length", "yd", 0.9144, "yard", "yards");
        unit("length", "mi", 1609.344, "mile", "miles");
        unit("mass", "mg", 0.001, "milligram", "milligrams");
        unit("mass", "g", 1, "gram", "grams", "gm");
        unit("mass", "kg", 1000, "kilogram", "kilograms", "kilo", "kilos", "kgs");
        unit("mass", "oz", 28.349523125, "ounce", "ounces");
        unit("mass", "lb", 453.59237, "pound", "pounds", "lbs");
        unit("mass", "st", 6350.29318, "stone", "stones");
        unit("volume", "ml", 0.001, "millilitre", "millilitres", "milliliter", "milliliters");
        unit("volume", "l", 1, "litre", "litres", "liter", "liters");
        unit("volume", "cup", 0.2365882365, "cups");
        unit("volume", "floz", 0.0295735295625, "fl oz", "fluid ounce", "fluid ounces");
        unit("volume", "gal", 3.785411784, "gallon", "gallons");
        unit("volume", "tsp", 0.00492892159375, "teaspoon", "teaspoons");
        unit("volume", "tbsp", 0.01478676478125, "tablespoon", "tablespoons");
        unit("speed", "km/h", 1 / 3.6, "kmh", "kph");
        unit("speed", "mph", 0.44704);
        unit("speed", "m/s", 1, "mps");
        unit("speed", "kn", 0.514444, "knot", "knots");
        unit("data", "b", 1, "byte", "bytes");
        unit("data", "kb", 1e3, "kilobyte", "kilobytes");
        unit("data", "mb", 1e6, "megabyte", "megabytes");
        unit("data", "gb", 1e9, "gigabyte", "gigabytes");
        unit("data", "tb", 1e12, "terabyte", "terabytes");
        unit("data", "kib", 1024, "kibibyte");
        unit("data", "mib", 1048576, "mebibyte");
        unit("data", "gib", 1073741824, "gibibyte");
        unit("time", "s", 1, "sec", "secs", "second", "seconds");
        unit("time", "min", 60, "mins", "minute", "minutes");
        unit("time", "h", 3600, "hr", "hrs", "hour", "hours");
        unit("time", "day", 86400, "days", "d");
        unit("time", "week", 604800, "weeks", "wk");
        unit("area", "m2", 1, "sqm", "sq m", "square metre", "square metres", "square meter", "square meters");
        unit("area", "ft2", 0.09290304, "sqft", "sq ft", "square foot", "square feet");
        unit("area", "acre", 4046.8564224, "acres");
        unit("area", "ha", 10000, "hectare", "hectares");
        unit("temp", "°c", 0, "c", "celsius", "centigrade", "degc");
        unit("temp", "°f", 0, "f", "fahrenheit", "degf");
        unit("temp", "k", 0, "kelvin");
    }

    /** "5 km in miles", "72f to c", "3.5 kg lb". */
    static String convert(String q) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(-?[0-9][0-9,]*\\.?[0-9]*)\\s*([a-z°/'\"0-9 ]+?)\\s+(?:in|to|into|as)?\\s*([a-z°/'\"0-9 ]+)$").matcher(q);
        if (!m.matches()) return null;
        Unit from = UNITS.get(m.group(2).trim()), to = UNITS.get(m.group(3).trim());
        if (from == null || to == null || !from.dim.equals(to.dim) || from == to) return null;
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        double out = from.dim.equals("temp") ? temperature(v, from.symbol, to.symbol) : v * from.factor / to.factor;
        return format(v) + " " + from.symbol + " = " + format(out) + " " + to.symbol;
    }

    private static double temperature(double v, String from, String to) {
        double c = from.equals("°c") ? v : from.equals("°f") ? (v - 32) * 5 / 9 : v - 273.15;
        return to.equals("°c") ? c : to.equals("°f") ? c * 9 / 5 + 32 : c + 273.15;
    }

    static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.format(Locale.ROOT, "%d", (long) v);
        String s = String.format(Locale.ROOT, Math.abs(v) >= 1 ? "%.4f" : "%.6f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String pretty(String q) {
        return q.replace("*", " × ").replace("/", " ÷ ").replace("+", " + ").replaceAll("(?<=[0-9)])\\s*-", " - ").replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")").trim();
    }
}
