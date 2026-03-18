package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility methods for parsing SVG attribute values: numbers, doubles,
 * style properties, opacity, fill-rule, and generic style-or-attribute lookup.
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
            Matcher m = Pattern.compile(Pattern.quote(prop) + "\\s*:\\s*([^;]+)").matcher(style);
            if (m.find()) return m.group(1).trim();
        }
        String v = el.getAttribute(prop);
        return v.isEmpty() ? null : v;
    }
}
