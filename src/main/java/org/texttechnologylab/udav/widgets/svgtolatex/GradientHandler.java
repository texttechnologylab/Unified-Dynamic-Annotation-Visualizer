package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.texttechnologylab.udav.widgets.svgtolatex.ColorManager.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

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

    public GradientHandler(ConversionContext ctx) {
        this.ctx = ctx;
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
     * Unified gradient-options dispatcher.  Tries linear first, then radial.
     * Returns null if {@code rawFill} does not reference a known gradient.
     */
    public String buildGradientOpts(String rawFill,
                                    double shapeX, double shapeY,
                                    double shapeW, double shapeH) {
        String r = buildLinearGradientOpts(rawFill, shapeX, shapeY, shapeW, shapeH);
        if (r != null) return r;
        return buildRadialGradientOpts(rawFill);
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

        double gx1 = parseDouble(gradEl.getAttribute("x1"), 0);
        double gy1 = parseDouble(gradEl.getAttribute("y1"), 0);
        double gx2 = parseDouble(gradEl.getAttribute("x2"), 1);
        double gy2 = parseDouble(gradEl.getAttribute("y2"), 0);

        String gradUnits = gradEl.getAttribute("gradientUnits");
        if (gradUnits.isEmpty()) gradUnits = "objectBoundingBox";

        double ox1, oy1, ox2, oy2;
        if ("userSpaceOnUse".equals(gradUnits)) {
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

        int[] rgbA = interpolateGradColor(stops, tMin);
        int[] rgbB = interpolateGradColor(stops, tMax);
        String hexA = String.format("#%02x%02x%02x", rgbA[0], rgbA[1], rgbA[2]);
        String hexB = String.format("#%02x%02x%02x", rgbB[0], rgbB[1], rgbB[2]);
        ctx.colors.registerHex(hexA);
        ctx.colors.registerHex(hexB);

        if (hexA.equals(hexB)) return "fill=" + ctx.colors.colorName(hexA);

        String c1 = ctx.colors.colorName(hexA);
        String c2 = ctx.colors.colorName(hexB);

        double angleDeg = Math.toDegrees(Math.atan2(-dy, dx));
        while (angleDeg <    0) angleDeg += 360;
        while (angleDeg >= 360) angleDeg -= 360;

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
            double offset = parseDouble(s.getAttribute("offset"), 0);
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
