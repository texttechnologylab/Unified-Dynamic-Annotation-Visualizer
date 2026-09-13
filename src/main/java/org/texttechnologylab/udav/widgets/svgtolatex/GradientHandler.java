package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.*;
import java.util.regex.*;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.ColorManager.*;

/**
 * Handles gradient (linear and radial) collection and TikZ option generation.
 */
public class GradientHandler {

    /** A single gradient stop with offset, colour, and opacity. */
    public static class GradStop {
        public final double offset;
        public final String hex;      // lower-case #rrggbb
        public final double opacity;
        public GradStop(double o, String h, double op) { offset = o; hex = h; opacity = op; }
    }

    private final ConversionContext ctx;
    private final PatternHandler patterns;

    public GradientHandler(ConversionContext ctx) {
        this.ctx = ctx;
        this.patterns = new PatternHandler(ctx);
    }

    // -----------------------------------------------------------------------
    // Pass 1c: collect gradient stops from <defs>
    // -----------------------------------------------------------------------

    public void collectGradientStops() {
        for (Map.Entry<String, Element> e : ctx.defsMap.entrySet()) {
            Element el = e.getValue();
            String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
            if (!tag.equals("lineargradient") && !tag.equals("radialgradient")) continue;
            List<GradStop> stops = parseGradientStops(el);
            if (!stops.isEmpty()) {
                ctx.gradStops.put(e.getKey(), stops);
                for (GradStop s : stops) ctx.colors.registerHex(s.hex);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Gradient TikZ options
    // -----------------------------------------------------------------------

    /**
     * Unified paint-server dispatcher: linear gradient, then radial, then pattern.
     * Returns null only if {@code rawFill} references no paint server this can resolve,
     * in which case the caller falls back to ordinary colour resolution.
     */
    public String buildGradientOpts(String rawFill,
                                    double shapeX, double shapeY,
                                    double shapeW, double shapeH) {
        String r = buildLinearGradientOpts(rawFill, shapeX, shapeY, shapeW, shapeH);
        if (r != null) return r;
        r = buildRadialGradientOpts(rawFill);
        if (r != null) return r;
        return patterns.buildPatternOpts(rawFill);
    }

    // -----------------------------------------------------------------------
    // Linear gradient
    // -----------------------------------------------------------------------

    private String buildLinearGradientOpts(String rawFill,
                                           double shapeX, double shapeY,
                                           double shapeW, double shapeH) {
        if (rawFill == null || !rawFill.startsWith("url(#")) return null;
        String gradId = rawFill.replaceAll("url\\(#([^)]+)\\).*", "$1").trim();
        Element gradEl = ctx.defsMap.get(gradId);
        if (gradEl == null) return null;
        String tag = gradEl.getTagName().replaceFirst(".*:", "").toLowerCase();
        if (!tag.equals("lineargradient")) return null;

        List<GradStop> stops = ctx.gradStops.get(gradId);
        if (stops == null || stops.isEmpty()) return null;

        String gradUnits = gradEl.getAttribute("gradientUnits");
        if (gradUnits.isEmpty()) gradUnits = "objectBoundingBox";
        boolean userSpace = "userSpaceOnUse".equals(gradUnits);

        double gx1 = gradientCoord(gradEl.getAttribute("x1"), 0, userSpace, true);
        double gy1 = gradientCoord(gradEl.getAttribute("y1"), 0, userSpace, false);
        double gx2 = gradientCoord(gradEl.getAttribute("x2"), 1, userSpace, true);
        double gy2 = gradientCoord(gradEl.getAttribute("y2"), 0, userSpace, false);

        double ox1, oy1, ox2, oy2;
        if (userSpace) {
            ox1 = gx1; oy1 = gy1; ox2 = gx2; oy2 = gy2;
        } else {
            double bw = (shapeW > 0) ? shapeW : 1;
            double bh = (shapeH > 0) ? shapeH : 1;
            ox1 = shapeX + gx1 * bw; oy1 = shapeY + gy1 * bh;
            ox2 = shapeX + gx2 * bw; oy2 = shapeY + gy2 * bh;
        }

        String gtStr = gradEl.getAttribute("gradientTransform");
        if (!gtStr.isEmpty()) {
            double[] M = parseTransformMtx(gtStr);
            double[] p1 = applyMtxAbs(M, ox1, oy1);
            double[] p2 = applyMtxAbs(M, ox2, oy2);
            ox1 = p1[0]; oy1 = p1[1];
            ox2 = p2[0]; oy2 = p2[1];
        }

        double dx = ox2 - ox1, dy = oy2 - oy1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-12) return null;

        double[] cx = {shapeX, shapeX + shapeW, shapeX,          shapeX + shapeW};
        double[] cy = {shapeY, shapeY,           shapeY + shapeH, shapeY + shapeH};
        double tMin = Double.MAX_VALUE, tMax = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double t = ((cx[i] - ox1) * dx + (cy[i] - oy1) * dy) / lenSq;
            if (t < tMin) tMin = t;
            if (t > tMax) tMax = t;
        }

        // Two colours can only describe a two-stop gradient; anything richer needs a
        // shading declaration, or the intermediate stops are lost.
        if (countStopsWithin(stops, tMin, tMax) > 0) {
            String shading = declareLinearShading(stops, tMin, tMax);
            if (shading != null) {
                double a = shadingAngle(dx, dy);
                return String.format(Locale.US, "shading=%s, shading angle=%.1f", shading, a);
            }
        }

        int[] rgbA = interpolateGradColor(stops, tMin);
        int[] rgbB = interpolateGradColor(stops, tMax);
        String hexA = String.format("#%02x%02x%02x", rgbA[0], rgbA[1], rgbA[2]);
        String hexB = String.format("#%02x%02x%02x", rgbB[0], rgbB[1], rgbB[2]);
        ctx.colors.registerHex(hexA);
        ctx.colors.registerHex(hexB);

        if (hexA.equals(hexB)) return "fill=" + ctx.colors.colorName(hexA);

        String c1 = ctx.colors.colorName(hexA);
        String c2 = ctx.colors.colorName(hexB);

        double angleDeg = shadingAngle(dx, dy);

        if      (angleDeg < 0.5 || angleDeg > 359.5)   return "shade, left color="   + c1 + ", right color="  + c2;
        else if (Math.abs(angleDeg -  90) < 0.5)        return "shade, bottom color=" + c1 + ", top color="    + c2;
        else if (Math.abs(angleDeg - 180) < 0.5)        return "shade, right color="  + c1 + ", left color="   + c2;
        else if (Math.abs(angleDeg - 270) < 0.5)        return "shade, top color="    + c1 + ", bottom color=" + c2;
        else return String.format(Locale.US,
                    "shade, shading angle=%.1f, left color=%s, right color=%s", angleDeg, c1, c2);
    }

    // -----------------------------------------------------------------------
    // Radial gradient
    // -----------------------------------------------------------------------

    private String buildRadialGradientOpts(String rawFill) {
        if (rawFill == null || !rawFill.startsWith("url(#")) return null;
        String gradId = rawFill.replaceAll("url\\(#([^)]+)\\).*", "$1").trim();
        Element gradEl = ctx.defsMap.get(gradId);
        if (gradEl == null) return null;
        String tag = gradEl.getTagName().replaceFirst(".*:", "").toLowerCase();
        if (!tag.equals("radialgradient")) return null;

        List<GradStop> stops = ctx.gradStops.get(gradId);
        if (stops == null || stops.isEmpty()) return null;

        if (countStopsWithin(stops, 0.0, 1.0) > 0) {
            String shading = declareRadialShading(stops);
            if (shading != null) return "shading=" + shading;
        }

        int[] rgbIn  = interpolateGradColor(stops, 0.0);
        int[] rgbOut = interpolateGradColor(stops, 1.0);
        String hexIn  = String.format("#%02x%02x%02x", rgbIn[0],  rgbIn[1],  rgbIn[2]);
        String hexOut = String.format("#%02x%02x%02x", rgbOut[0], rgbOut[1], rgbOut[2]);
        ctx.colors.registerHex(hexIn);
        ctx.colors.registerHex(hexOut);

        if (hexIn.equals(hexOut)) return "fill=" + ctx.colors.colorName(hexIn);

        String cIn  = ctx.colors.colorName(hexIn);
        String cOut = ctx.colors.colorName(hexOut);
        return "shading=radial, inner color=" + cIn + ", outer color=" + cOut;
    }

    /** Parses a stop {@code offset}: a fraction, or a percentage of one, clamped to [0,1]. */
    private static double stopOffset(String value) {
        ParseUtils.Length len = ParseUtils.parseLength(value);
        if (len == ParseUtils.ABSENT) return 0;
        double v = len.isPercentage() ? len.value() / 100.0 : len.value();
        return Math.max(0.0, Math.min(1.0, v));
    }

    /**
     * Reads a gradient coordinate, honouring the percentage form.
     * {@code parseDouble} strips the unit, so {@code x2="100%"} would read as 100 and,
     * in {@code objectBoundingBox} units, make the gradient vector a hundred times
     * too long.
     *
     * @param userSpace  true for {@code gradientUnits="userSpaceOnUse"}, where a
     *                   percentage is of the viewport and a plain number is user units;
     *                   false for {@code objectBoundingBox}, where both are fractions
     *                   of the shape's bounding box
     * @param horizontal which viewport dimension a percentage refers to
     */
    private double gradientCoord(String value, double fallback, boolean userSpace, boolean horizontal) {
        ParseUtils.Length len = ParseUtils.parseLength(value);
        if (len == ParseUtils.ABSENT) return fallback;
        if (len.isPercentage()) {
            return userSpace
                    ? len.value() / 100.0 * (horizontal ? ctx.svgWidth : ctx.svgHeight)
                    : len.value() / 100.0;
        }
        return len.isAbsolute() ? len.px() : len.value();
    }

    // -----------------------------------------------------------------------
    // Multi-stop shadings
    // -----------------------------------------------------------------------

    /**
     * Where a horizontal shading's visible range sits inside its declaration.
     * <p>
     * pgf does not map a declared shading edge-to-edge onto the path: it shows the
     * band from 25bp to 75bp of a 100bp declaration and pads the rest. TikZ's own
     * {@code left color}/{@code right color} shading is declared the same way, with
     * each end colour repeated at 0bp/25bp and at 75bp/100bp. Spreading the stops
     * across the full 0..100 range crops the first and last quarter of every gradient.
     */
    private static final int LINEAR_START_BP = 25;
    private static final int LINEAR_END_BP   = 75;
    private static final int LINEAR_TOTAL_BP = 100;

    /** A radial shading is declared from centre (0bp) to rim, as TikZ's own is. */
    private static final int RADIAL_RADIUS_BP = 50;

    /** Counts stops that fall strictly inside the visible range, i.e. genuine mid-stops. */
    private static int countStopsWithin(List<GradStop> stops, double tMin, double tMax) {
        if (tMax - tMin < 1e-9) return 0;
        int n = 0;
        for (GradStop s : stops) {
            if (s.offset > tMin + 1e-6 && s.offset < tMax - 1e-6) n++;
        }
        return n;
    }

    /**
     * Declares a {@code \pgfdeclarehorizontalshading} carrying every stop visible across
     * the shape, and returns its name.
     * <p>
     * The shape's extent along the gradient axis is {@code [tMin, tMax]} in gradient
     * parameter space, which need not be {@code [0, 1]}: a gradient vector shorter than
     * the shape leaves the ends padded with the first and last stop colour, which is the
     * default {@code spreadMethod}. Mapping that window onto pgf's visible 25..75bp band
     * reproduces the padding with no special case.
     */
    private String declareLinearShading(List<GradStop> stops, double tMin, double tMax) {
        double span = tMax - tMin;
        if (span < 1e-9) return null;

        List<String> parts = new ArrayList<>();
        int[] startRgb = interpolateGradColor(stops, tMin);
        int[] endRgb   = interpolateGradColor(stops, tMax);

        addShadingStop(parts, 0, startRgb);
        addShadingStop(parts, LINEAR_START_BP, startRgb);

        int lastBp = LINEAR_START_BP;
        for (GradStop stop : stops) {
            if (stop.offset <= tMin + 1e-6 || stop.offset >= tMax - 1e-6) continue;
            int bp = LINEAR_START_BP + (int) Math.round(
                    (stop.offset - tMin) / span * (LINEAR_END_BP - LINEAR_START_BP));
            if (bp <= lastBp) bp = lastBp + 1;      // pgf needs strictly increasing positions
            if (bp >= LINEAR_END_BP) break;
            addShadingStop(parts, bp, hexToRgb(stop.hex));
            lastBp = bp;
        }

        addShadingStop(parts, LINEAR_END_BP, endRgb);
        addShadingStop(parts, LINEAR_TOTAL_BP, endRgb);

        String spec = String.join("; ", parts);
        String name = shadingName(spec);
        ctx.pendingShadings.putIfAbsent(name, String.format(Locale.US,
                "\\pgfdeclarehorizontalshading{%s}{%dbp}{%s}%n", name, LINEAR_TOTAL_BP, spec));
        return name;
    }

    /**
     * Declares a {@code \pgfdeclareradialshading} through every stop and returns its name.
     * Offset 0 is the centre, offset 1 the rim.
     */
    private String declareRadialShading(List<GradStop> stops) {
        List<String> parts = new ArrayList<>();
        addShadingStop(parts, 0, interpolateGradColor(stops, 0.0));
        int lastBp = 0;
        for (GradStop stop : stops) {
            if (stop.offset <= 1e-6 || stop.offset >= 1 - 1e-6) continue;
            int bp = (int) Math.round(stop.offset * RADIAL_RADIUS_BP);
            if (bp <= lastBp) bp = lastBp + 1;
            if (bp >= RADIAL_RADIUS_BP) break;
            addShadingStop(parts, bp, hexToRgb(stop.hex));
            lastBp = bp;
        }
        addShadingStop(parts, RADIAL_RADIUS_BP, interpolateGradColor(stops, 1.0));

        String spec = String.join("; ", parts);
        String name = shadingName(spec);
        ctx.pendingShadings.putIfAbsent(name, String.format(Locale.US,
                "\\pgfdeclareradialshading{%s}{\\pgfpointorigin}{%s}%n", name, spec));
        return name;
    }

    private void addShadingStop(List<String> parts, int bp, int[] rgb) {
        String hex = String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
        ctx.colors.registerHex(hex);
        parts.add(String.format(Locale.US, "color(%dbp)=(%s)", bp, ctx.colors.colorName(hex)));
    }

    /**
     * A stable, letters-only name for a shading spec. Deriving it from the content means
     * two shapes sharing a gradient also share one declaration, with no counter to keep.
     */
    private static String shadingName(String spec) {
        long h = spec.hashCode() & 0xFFFFFFFFL;
        StringBuilder sb = new StringBuilder("svgshade");
        do { sb.append((char) ('a' + (int) (h % 26))); h /= 26; } while (h > 0);
        return sb.toString();
    }

    /** Gradient direction as a TikZ {@code shading angle}, accounting for the y-flip. */
    private static double shadingAngle(double dx, double dy) {
        double deg = Math.toDegrees(Math.atan2(-dy, dx));
        while (deg <    0) deg += 360;
        while (deg >= 360) deg -= 360;
        return deg;
    }

    // -----------------------------------------------------------------------
    // Stop parsing and interpolation
    // -----------------------------------------------------------------------

    private List<GradStop> parseGradientStops(Element gradEl) {
        List<GradStop> stops = new ArrayList<>();
        NodeList kids = gradEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element s = (Element) kids.item(i);
            String stag = s.getTagName().replaceFirst(".*:", "").toLowerCase();
            if (!stag.equals("stop")) continue;
            // offset is a number or a percentage, clamped to [0,1]. parseDouble would
            // strip the "%" and return 100 for "100%", stretching the ramp a
            // hundredfold.
            double offset = stopOffset(s.getAttribute("offset"));
            String stopColor = null;
            double stopOpacity = 1.0;
            String style = s.getAttribute("style");
            if (!style.isEmpty()) {
                Matcher mc = Pattern.compile("stop-color\\s*:\\s*([^;]+)").matcher(style);
                if (mc.find()) stopColor = mc.group(1).trim();
                Matcher mo = Pattern.compile("stop-opacity\\s*:\\s*([^;]+)").matcher(style);
                if (mo.find()) stopOpacity = parseDouble(mo.group(1).trim(), 1.0);
            }
            if (stopColor == null || stopColor.isEmpty()) stopColor = s.getAttribute("stop-color");
            String opStr = s.getAttribute("stop-opacity");
            if (!opStr.isEmpty()) stopOpacity = parseDouble(opStr, 1.0);
            stops.add(new GradStop(offset, ctx.colors.resolveColorHex(stopColor), stopOpacity));
        }
        if (stops.isEmpty()) {
            String href = gradEl.getAttribute("xlink:href");
            if (href.isEmpty()) href = gradEl.getAttribute("href");
            if (!href.isEmpty() && href.startsWith("#")) {
                Element ref = ctx.defsMap.get(href.substring(1));
                if (ref != null) stops = parseGradientStops(ref);
            }
        }
        stops.sort((a, b) -> Double.compare(a.offset, b.offset));
        return stops;
    }

    private static int[] interpolateGradColor(List<GradStop> stops, double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        if (stops.size() == 1) return hexToRgb(stops.get(0).hex);
        GradStop lo = stops.get(0), hi = stops.get(stops.size() - 1);
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
        return new int[]{
                (int) Math.round(cLo[0] + f * (cHi[0] - cLo[0])),
                (int) Math.round(cLo[1] + f * (cHi[1] - cLo[1])),
                (int) Math.round(cLo[2] + f * (cHi[2] - cLo[2]))
        };
    }
}
