package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages colour registration and resolution for the SVG → TikZ conversion.
 * <p>
 * Every hex colour encountered in the SVG is registered and later emitted as a
 * {@code \definecolor{cRRGGBB}{RGB}{R,G,B}} command.  TikZ built-in colour
 * names are never used.
 */
public class ColorManager {

    /** Fallback hex for "currentColor" and other non-hex values. */
    public static final String CURRENT_COLOR_HEX = "#000000";

    /** hex (lower-case, with #) → TikZ colour name */
    private final Map<String, String> colorDefs = new LinkedHashMap<>();

    /** Back-reference to the conversion context (set after construction). */
    private ConversionContext ctx;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void clear() {
        colorDefs.clear();
    }

    /** Set the back-reference to the conversion context (call once after construction). */
    public void setContext(ConversionContext ctx) {
        this.ctx = ctx;
    }

    /** Get the full map of registered colour definitions (hex → name). */
    public Map<String, String> getColorDefs() {
        return colorDefs;
    }

    /** Register a hex colour (e.g. "#ff0000") so it will be defined in the LaTeX preamble. */
    public void registerHex(String hex) {
        String key = hex.toLowerCase();
        if (!colorDefs.containsKey(key)) colorDefs.put(key, deriveColorName(key));
    }

    /** Look up the TikZ colour name for a hex value, defaulting to "black". */
    public String colorName(String hex) {
        return colorDefs.getOrDefault(hex.toLowerCase(), "black");
    }

    // -----------------------------------------------------------------------
    // Pre-scan: collect all colours referenced in the SVG tree
    // -----------------------------------------------------------------------

    /**
     * Recursively walk the DOM tree and register every hex, rgb(), or named
     * colour found in {@code fill} and {@code stroke} attributes (including
     * inside {@code style}).
     */
    public void collectColors(Node node) {
        if (node instanceof Element) {
            Element el = (Element) node;
            for (String attr : new String[]{"fill", "stroke"}) {
                String v = el.getAttribute(attr).trim();
                if (v.startsWith("#")) {
                    registerHex(expandShortHex(v));
                } else if (v.startsWith("rgb")) {
                    String h = rgbToHex(v);
                    if (h != null) registerHex(h);
                } else if (!v.isEmpty() && !v.equals("none") && !v.equals("transparent")
                        && !v.equals("currentColor")) {
                    String h = CSS_NAMED_COLORS.get(v.toLowerCase());
                    if (h != null) registerHex(h);
                }
            }
            String style = el.getAttribute("style");
            if (!style.isEmpty()) {
                Matcher m = Pattern.compile("(?:fill|stroke)\\s*:\\s*(#[0-9a-fA-F]{3,6})").matcher(style);
                while (m.find()) registerHex(expandShortHex(m.group(1)));
                Matcher rm = Pattern.compile("(?:fill|stroke)\\s*:\\s*(rgb\\([^)]+\\))").matcher(style);
                while (rm.find()) { String h = rgbToHex(rm.group(1)); if (h != null) registerHex(h); }
                Matcher nm = Pattern.compile("(?:fill|stroke)\\s*:\\s*([a-zA-Z]+)").matcher(style);
                while (nm.find()) {
                    String h = CSS_NAMED_COLORS.get(nm.group(1).toLowerCase());
                    if (h != null) registerHex(h);
                }
            }
        }
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) collectColors(kids.item(i));
    }

    // -----------------------------------------------------------------------
    // Colour resolution for fill / stroke attribute values
    // -----------------------------------------------------------------------

    /**
     * Resolve an SVG fill attribute value (from an element) to a TikZ colour name.
     * Checks the element's direct attribute, then style, then falls back to inherited.
     * If {@link InheritedAttrs#forceFill} is set, that colour overrides everything.
     */
    public String resolveFill(Element el, InheritedAttrs inh) {
        if (inh.forceFill != null) return resolveColorValue(inh.forceFill);
        String v = directColor(el, "fill");
        if (v != null) return v;
        v = styleColor(el, "fill");
        if (v != null) return v;
        return resolveColorValue(inh.fill);
    }

    /**
     * Resolve an SVG stroke attribute value to a TikZ colour name.
     * If {@link InheritedAttrs#forceNoStroke} is set, always returns "none",
     * overriding even element-level stroke attributes and styles.
     */
    public String resolveStroke(Element el, InheritedAttrs inh) {
        if (inh.forceNoStroke) return "none";
        String v = directColor(el, "stroke");
        if (v != null) return v;
        v = styleColor(el, "stroke");
        if (v != null) return v;
        return resolveColorValue(inh.stroke);
    }

    /**
     * Resolve any raw SVG colour string to a hex value ("#rrggbb").
     * Handles hex, rgb(), named colours, and currentColor.
     */
    public String resolveColorHex(String v) {
        if (v == null || v.isEmpty()) return CURRENT_COLOR_HEX;
        v = v.trim();
        if (v.startsWith("#")) return expandShortHex(v).toLowerCase();
        if (v.startsWith("rgb")) { String h = rgbToHex(v); return h != null ? h : CURRENT_COLOR_HEX; }
        String h = CSS_NAMED_COLORS.get(v.toLowerCase());
        return h != null ? h : CURRENT_COLOR_HEX;
    }

    /**
     * Resolve a raw SVG colour value to a TikZ colour name.
     * Handles "none", "transparent", hex, rgb(), named colours, url() with
     * fallback, and currentColor.
     */
    public String resolveColorValue(String v) {
        if (v == null || v.isEmpty()) return "none";
        v = v.trim();
        if (v.startsWith("#")) {
            v = expandShortHex(v);
            return colorName(v);
        }
        if (v.equals("none") || v.equals("transparent")) return "none";
        if (v.startsWith("rgb")) {
            String hex = rgbToHex(v);
            if (hex != null) { registerHex(hex); return colorName(hex); }
        }
        if (v.startsWith("url(")) {
            int paren = v.indexOf(')');
            if (paren >= 0 && paren + 1 < v.length()) {
                String fallback = v.substring(paren + 1).trim();
                if (!fallback.isEmpty()) return resolveColorValue(fallback);
            }
            // No fallback — try to sample the referenced gradient at t=0.5
            // so gradient strokes get a representative colour instead of vanishing.
            Matcher gm = Pattern.compile("url\\(#([^)]+)\\)").matcher(v);
            if (gm.find() && ctx != null) {
                String gradId = gm.group(1);
                List<GradientHandler.GradStop> stops = ctx.gradStops.get(gradId);
                if (stops != null && !stops.isEmpty()) {
                    // Sample at midpoint
                    String midHex = sampleGradientHex(stops, 0.5);
                    registerHex(midHex);
                    return colorName(midHex);
                }
            }
            return "none";
        }
        String namedHex = CSS_NAMED_COLORS.get(v.toLowerCase());
        if (namedHex != null) { registerHex(namedHex); return colorName(namedHex); }
        registerHex(CURRENT_COLOR_HEX);
        return colorName(CURRENT_COLOR_HEX);
    }

    /**
     * Read the raw fill value from an element or its style, with inheritance fallback.
     * If {@link InheritedAttrs#forceFill} is set, returns that value directly so
     * gradient detection is bypassed and the shadow renders as a uniform colour.
     */
    public String getRawFill(Element el, InheritedAttrs inh) {
        if (inh.forceFill != null) return inh.forceFill;
        String v = el.getAttribute("fill").trim();
        if (v.isEmpty()) {
            String style = el.getAttribute("style");
            if (!style.isEmpty()) {
                Matcher m = Pattern.compile("fill\\s*:\\s*([^;]+)").matcher(style);
                if (m.find()) v = m.group(1).trim();
            }
        }
        return v.isEmpty() ? inh.fill : v;
    }

    // -----------------------------------------------------------------------
    // Conversion helpers
    // -----------------------------------------------------------------------

    /**
     * Converts "rgb(255, 84, 0)" or "rgb(255,84,0)" to "#ff5400".
     * Returns null if the string cannot be parsed.
     */
    public static String rgbToHex(String rgb) {
        Matcher m = Pattern.compile("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)").matcher(rgb.trim());
        if (!m.find()) return null;
        int r = Integer.parseInt(m.group(1));
        int g = Integer.parseInt(m.group(2));
        int b = Integer.parseInt(m.group(3));
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /** Expand 3-digit hex (#abc) to 6-digit (#aabbcc). */
    public static String expandShortHex(String hex) {
        if (hex.length() == 4) {
            return "#" + hex.charAt(1) + "" + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2)
                    + hex.charAt(3) + hex.charAt(3);
        }
        return hex;
    }

    /** Parse a "#rrggbb" hex string into {R, G, B} ints. */
    public static int[] hexToRgb(String hex) {
        hex = hex.replace("#", "");
        return new int[]{
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    /**
     * Sample a gradient's colour at parameter {@code t} ∈ [0,1] and return
     * the result as a lowercase "#rrggbb" hex string.
     */
    public static String sampleGradientHex(List<GradientHandler.GradStop> stops, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        if (stops.size() == 1) {
            return stops.get(0).hex;
        }
        GradientHandler.GradStop lo = stops.get(0), hi = stops.get(stops.size() - 1);
        for (int i = 0; i < stops.size() - 1; i++) {
            if (t >= stops.get(i).offset && t <= stops.get(i + 1).offset) {
                lo = stops.get(i);
                hi = stops.get(i + 1);
                break;
            }
        }
        double span = hi.offset - lo.offset;
        double f = (span < 1e-9) ? 0.0 : (t - lo.offset) / span;
        int[] cLo = hexToRgb(lo.hex), cHi = hexToRgb(hi.hex);
        int r = (int) Math.round(cLo[0] + f * (cHi[0] - cLo[0]));
        int g = (int) Math.round(cLo[1] + f * (cHi[1] - cLo[1]));
        int b = (int) Math.round(cLo[2] + f * (cHi[2] - cLo[2]));
        return String.format("#%02x%02x%02x", r, g, b);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private String directColor(Element el, String attr) {
        String v = el.getAttribute(attr).trim();
        if (v.isEmpty()) return null;
        return resolveColorValue(v);
    }

    private String styleColor(Element el, String attr) {
        String style = el.getAttribute("style");
        if (style.isEmpty()) return null;
        Matcher m = Pattern.compile(attr + "\\s*:\\s*([^;]+)").matcher(style);
        if (!m.find()) return null;
        return resolveColorValue(m.group(1).trim());
    }

    private static String deriveColorName(String hex) {
        return "c" + hex.toLowerCase().replace("#", "");
    }

    // -----------------------------------------------------------------------
    // CSS named colours (subset most likely to appear in D3/SVG output)
    // -----------------------------------------------------------------------

    public static final Map<String, String> CSS_NAMED_COLORS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("black",       "#000000"); m.put("white",       "#ffffff");
        m.put("red",         "#ff0000"); m.put("green",       "#008000");
        m.put("blue",        "#0000ff"); m.put("yellow",      "#ffff00");
        m.put("cyan",        "#00ffff"); m.put("magenta",     "#ff00ff");
        m.put("orange",      "#ffa500"); m.put("purple",      "#800080");
        m.put("pink",        "#ffc0cb"); m.put("brown",       "#a52a2a");
        m.put("grey",        "#808080"); m.put("gray",        "#808080");
        m.put("darkgray",    "#a9a9a9"); m.put("darkgrey",    "#a9a9a9");
        m.put("lightgray",   "#d3d3d3"); m.put("lightgrey",   "#d3d3d3");
        m.put("darkred",     "#8b0000"); m.put("darkblue",    "#00008b");
        m.put("darkgreen",   "#006400"); m.put("darkorange",  "#ff8c00");
        m.put("steelblue",   "#4682b4"); m.put("royalblue",   "#4169e1");
        m.put("navy",        "#000080"); m.put("teal",        "#008080");
        m.put("olive",       "#808000"); m.put("maroon",      "#800000");
        m.put("lime",        "#00ff00"); m.put("aqua",        "#00ffff");
        m.put("fuchsia",     "#ff00ff"); m.put("silver",      "#c0c0c0");
        m.put("gold",        "#ffd700"); m.put("coral",       "#ff7f50");
        m.put("salmon",      "#fa8072"); m.put("tomato",      "#ff6347");
        m.put("orangered",   "#ff4500"); m.put("crimson",     "#dc143c");
        m.put("firebrick",   "#b22222"); m.put("indigo",      "#4b0082");
        m.put("violet",      "#ee82ee"); m.put("plum",        "#dda0dd");
        m.put("orchid",      "#da70d6"); m.put("hotpink",     "#ff69b4");
        m.put("deeppink",    "#ff1493"); m.put("mediumpurple","#9370db");
        m.put("slateblue",   "#6a5acd"); m.put("cornflowerblue","#6495ed");
        m.put("dodgerblue",  "#1e90ff"); m.put("deepskyblue", "#00bfff");
        m.put("lightskyblue","#87cefa"); m.put("skyblue",     "#87ceeb");
        m.put("cadetblue",   "#5f9ea0"); m.put("mediumturquoise","#48d1cc");
        m.put("turquoise",   "#40e0d0"); m.put("aquamarine",  "#7fffd4");
        m.put("seagreen",    "#2e8b57"); m.put("mediumseagreen","#3cb371");
        m.put("limegreen",   "#32cd32"); m.put("forestgreen", "#228b22");
        m.put("yellowgreen", "#9acd32"); m.put("olivedrab",   "#6b8e23");
        m.put("chartreuse",  "#7fff00"); m.put("greenyellow", "#adff2f");
        m.put("khaki",       "#f0e68c"); m.put("darkkhaki",   "#bdb76b");
        m.put("tan",         "#d2b48c"); m.put("burlywood",   "#deb887");
        m.put("wheat",       "#f5deb3"); m.put("bisque",      "#ffe4c4");
        m.put("peachpuff",   "#ffdab9"); m.put("moccasin",    "#ffe4b5");
        m.put("goldenrod",   "#daa520"); m.put("darkgoldenrod","#b8860b");
        m.put("sienna",      "#a0522d"); m.put("saddlebrown", "#8b4513");
        m.put("chocolate",   "#d2691e"); m.put("peru",        "#cd853f");
        m.put("rosybrown",   "#bc8f8f"); m.put("indianred",   "#cd5c5c");
        m.put("lightcoral",  "#f08080"); m.put("darksalmon",  "#e9967a");
        CSS_NAMED_COLORS = Collections.unmodifiableMap(m);
    }
}
