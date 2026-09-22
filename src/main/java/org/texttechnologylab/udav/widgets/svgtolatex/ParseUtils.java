package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import java.util.*;
import java.util.Locale;
import java.util.regex.*;

/**
 * Static utility methods for parsing SVG attribute values: numbers, doubles,
 * CSS lengths, style properties, opacity, fill-rule, and generic
 * style-or-attribute lookup.
 */
public final class ParseUtils {

    private ParseUtils() {} // utility class

    /**
     * Parse a string to a double, stripping common CSS unit suffixes.
     * Returns {@code def} on null, empty, or unparseable input.
     */
    public static double parseDouble(String s, double def) {
        if (s == null || s.trim().isEmpty()) return def;
        s = s.trim().replaceAll("(?i)(px|pt|em|rem|ex|cm|mm|in|pc|vh|vw|%)$", "").trim();
        if (s.isEmpty()) return def;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return def; }
    }

    // -----------------------------------------------------------------------
    // CSS lengths
    // -----------------------------------------------------------------------

    /**
     * A CSS length as written in the document: the number plus the unit it carried.
     * <p>
     * {@link #parseDouble} drops the unit, which is fine for a coordinate inside a
     * viewBox but not for a value that establishes a size: {@code width="100%"} is
     * not 100 user units, and {@code width="105mm"} is not 105.
     *
     * @param value the numeric part
     * @param unit  the unit as written, lower-cased; {@code ""} when unitless
     */
    public record Length(double value, String unit) {

        /** The length in CSS px, meaningful only when {@link #isAbsolute()}. */
        public double px() {
            return value * switch (unit) {
                case "", "px" -> 1.0;
                case "pt"     -> 96.0 / 72.0;   // 1pt = 1/72in
                case "pc"     -> 16.0;          // 1pc = 12pt
                case "in"     -> 96.0;
                case "cm"     -> 96.0 / 2.54;
                case "mm"     -> 96.0 / 25.4;
                case "q"      -> 96.0 / 101.6;  // quarter-millimetre
                default       -> 1.0;
            };
        }

        /**
         * True when this length resolves to a fixed number of pixels on its own.
         * <p>
         * False for percentages (which need a containing block) and for the
         * font-relative units em/ex/rem/ch (which need a font size). Those are
         * resolved by the caller, which knows what they are relative to.
         */
        public boolean isAbsolute() {
            return switch (unit) {
                case "", "px", "pt", "pc", "in", "cm", "mm", "q" -> true;
                default -> false;
            };
        }

        /** True when the length was written as a percentage. */
        public boolean isPercentage() {
            return "%".equals(unit);
        }
    }

    /** A length not present in the document at all. */
    public static final Length ABSENT = new Length(0, "absent");

    private static final Pattern LENGTH =
            Pattern.compile("^\\s*([+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)\\s*"
                    + "(px|pt|pc|in|cm|mm|q|em|ex|rem|ch|%)?\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Parse a CSS length, keeping its unit.
     * Returns {@link #ABSENT} for null, empty or unparseable input.
     */
    public static Length parseLength(String s) {
        if (s == null) return ABSENT;
        Matcher m = LENGTH.matcher(s);
        if (!m.matches()) return ABSENT;
        try {
            String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
            return new Length(Double.parseDouble(m.group(1)), unit);
        } catch (NumberFormatException e) {
            return ABSENT;
        }
    }

    /** Root font size assumed when resolving {@code rem}; the SVG/CSS default. */
    private static final double ROOT_FONT_SIZE_PX = 16;

    /**
     * Resolves a {@code font-size} value against the size inherited at that point.
     * <p>
     * Relative units are common here: {@code 65%} and {@code 0.8em} are how exporters
     * write a subscript, and {@link #parseDouble} would read the 65 as an absolute
     * size.
     *
     * @return the resolved size in px, or {@code inherited} when there is nothing to read
     */
    public static double resolveFontSize(String value, double inherited) {
        Length len = parseLength(value);
        if (len == ABSENT) return inherited;
        return switch (len.unit()) {
            case "%"   -> inherited * len.value() / 100.0;
            case "em"  -> inherited * len.value();
            case "rem" -> ROOT_FONT_SIZE_PX * len.value();
            // No font is loaded to ask; x-height is close to half the em across the
            // families SVG documents name.
            case "ex", "ch" -> inherited * len.value() * 0.5;
            default -> len.isAbsolute() ? len.px() : inherited;
        };
    }

    /**
     * Extract all numbers (including negatives and scientific notation)
     * from a whitespace/comma-separated string.  Handles both "1,2,3" and
     * "1 2 3" as well as mixed formats.
     */
    public static double[] parseNumbers(String s) {
        if (s == null || s.trim().isEmpty()) return new double[0];
        List<Double> nums = new ArrayList<>();
        Matcher m = Pattern.compile("-?[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?").matcher(s);
        while (m.find()) {
            try { nums.add(Double.parseDouble(m.group())); }
            catch (NumberFormatException ignored) {}
        }
        double[] r = new double[nums.size()];
        for (int i = 0; i < nums.size(); i++) r[i] = nums.get(i);
        return r;
    }

    /**
     * Extract a numeric value for a named CSS property from a {@code style}
     * attribute string.  Returns {@code def} if not found.
     */
    public static double parseStyleDouble(String style, String prop, double def) {
        if (style == null || style.isEmpty()) return def;
        Matcher m = Pattern.compile(prop + "\\s*:\\s*([0-9.]+)").matcher(style);
        if (m.find()) return parseDouble(m.group(1), def);
        return def;
    }

    /**
     * Read the element's {@code opacity} attribute or style property.
     * Returns a value in [0,1) if set, or -1 if not present / opaque.
     */
    public static double parseOpacity(Element el) {
        String attr = el.getAttribute("opacity").trim();
        if (!attr.isEmpty()) {
            try {
                double v = Double.parseDouble(attr);
                if (v >= 0 && v < 1) return v;
            } catch (NumberFormatException ignored) {}
        }
        return parseStyleDouble(el.getAttribute("style"), "opacity", -1);
    }

    /**
     * Returns true when the element declares {@code fill-rule: evenodd}
     * (either as an attribute or inside the {@code style} attribute).
     */
    public static boolean isEvenOdd(Element el) {
        String attr = el.getAttribute("fill-rule").trim();
        if ("evenodd".equalsIgnoreCase(attr)) return true;
        String style = el.getAttribute("style");
        if (!style.isEmpty()) {
            Matcher m = Pattern.compile("fill-rule\\s*:\\s*(\\S+)").matcher(style);
            if (m.find() && "evenodd".equalsIgnoreCase(m.group(1).trim())) return true;
        }
        return false;
    }

    /**
     * Read a property from the {@code style} attribute first, then from a
     * plain attribute.  Returns {@code null} if neither is set.
     */
    public static String getStyleOrAttr(Element el, String prop) {
        String style = el.getAttribute("style");
        if (!style.isEmpty()) {
            // Anchored on the left because a property name can be the tail of another
            // one: unanchored, "color" also matches "stop-color:" and "flood-color:".
            // No trailing anchor is needed, the ":" supplies it, so "stroke" does not
            // match "stroke-width:".
            Matcher m = Pattern.compile("(?:^|[;\\s])" + Pattern.quote(prop) + "\\s*:\\s*([^;]+)")
                    .matcher(style);
            if (m.find()) return m.group(1).trim();
        }
        String v = el.getAttribute(prop);
        return v.isEmpty() ? null : v;
    }
}
