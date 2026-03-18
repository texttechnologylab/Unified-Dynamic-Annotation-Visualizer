package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Collects SVG {@code <marker>} elements and maps them to TikZ
 * {@code arrows.meta} arrow-head specifications.
 */
public class MarkerHandler {

    /**
     * Maps Inkscape stockid names to the nearest TikZ arrows.meta head.
     */
    private static final Map<String, String> INKSCAPE_STOCK_TO_TIKZ;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Triangle arrow",    "Stealth");
        m.put("TriangleStart",     "Stealth");
        m.put("Dart arrow",        "Kite");
        m.put("Arrow2",            "Stealth[round]");
        m.put("Arrow1",            "Latex[open]");
        m.put("Wide arrow",        "Latex[open]");
        m.put("Stop",              "Bar[width=8\\pgflinewidth]");
        INKSCAPE_STOCK_TO_TIKZ = Collections.unmodifiableMap(m);
    }

    private final ConversionContext ctx;

    public MarkerHandler(ConversionContext ctx) {
        this.ctx = ctx;
    }

    // -----------------------------------------------------------------------
    // Pass 1d: collect markers from <defs>
    // -----------------------------------------------------------------------

    public void collectMarkers() {
        for (Map.Entry<String, Element> e : ctx.defsMap.entrySet()) {
            Element el = e.getValue();
            String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
            if (!"marker".equals(tag)) continue;

            String id      = e.getKey();
            String stockId = el.getAttribute("inkscape:stockid").trim();
            String orient  = el.getAttribute("orient").trim();

            // 1. Direct stockid lookup
            String arrow = null;
            if (!stockId.isEmpty()) {
                arrow = INKSCAPE_STOCK_TO_TIKZ.get(stockId);
            }

            // 2. Heuristic from the path child
            if (arrow == null) {
                NodeList kids = el.getChildNodes();
                for (int i = 0; i < kids.getLength(); i++) {
                    if (!(kids.item(i) instanceof Element)) continue;
                    Element child = (Element) kids.item(i);
                    String style = child.getAttribute("style");
                    String fillAttr = child.getAttribute("fill");
                    boolean noFill = style.contains("fill:none") || "none".equalsIgnoreCase(fillAttr);
                    arrow = noFill ? "Latex[open]" : "Stealth";
                    break;
                }
            }
            if (arrow != null) ctx.markerMap.put(id, arrow);

            // 3. Compute marker extensions beyond path endpoint
            double[] bbox = markerPathBBox(el);
            if (bbox != null) {
                double extAsStart, extAsEnd;
                if ("auto-start-reverse".equals(orient)) {
                    extAsStart = Math.max(0, bbox[1]);
                    extAsEnd   = Math.max(0, bbox[1]);
                } else {
                    extAsStart = Math.max(0, -bbox[0]);
                    extAsEnd   = Math.max(0,  bbox[1]);
                }
                if (extAsStart > 0 || extAsEnd > 0)
                    ctx.markerExtensions.put(id, new double[]{extAsStart, extAsEnd});
            }
        }
    }

    // -----------------------------------------------------------------------
    // Arrow spec building
    // -----------------------------------------------------------------------

    /**
     * Build the TikZ {@code {<start>-<end>}} arrow spec for an element
     * that may have {@code marker-start} / {@code marker-end} properties.
     * Returns an empty string if no markers are defined.
     */
    public String buildArrowSpec(Element el) {
        String msVal = getStyleOrAttr(el, "marker-start");
        String meVal = getStyleOrAttr(el, "marker-end");
        String msId  = markerIdFromUrl(msVal);
        String meId  = markerIdFromUrl(meVal);

        String startArrow = (msId != null) ? ctx.markerMap.get(msId) : null;
        String endArrow   = (meId != null) ? ctx.markerMap.get(meId) : null;

        if (startArrow == null && endArrow == null) return "";
        return String.format("{%s}-{%s}",
                startArrow != null ? startArrow : "",
                endArrow   != null ? endArrow   : "");
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Extract a marker id from a "url(#id)" reference string.
     */
    public static String markerIdFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        Matcher m = Pattern.compile("url\\(#([^)]+)\\)").matcher(url.trim());
        return m.find() ? m.group(1) : null;
    }

    /**
     * Approximate x-extent bounding box of a marker's child paths
     * in marker-local coordinates.
     *
     * @return {@code [min_x, max_x]} or {@code null} if no path was found.
     */
    private double[] markerPathBBox(Element markerEl) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        NodeList kids = markerEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element child = (Element) kids.item(i);
            String childTag = child.getTagName().replaceFirst(".*:", "").toLowerCase();
            if (!"path".equals(childTag) && !"line".equals(childTag)) continue;
            double[] childCtm = parseTransformMtx(child.getAttribute("transform"));
            String d = child.getAttribute("d").trim();
            if (d.isEmpty()) {
                double x1 = parseDouble(child.getAttribute("x1"), 0);
                double x2 = parseDouble(child.getAttribute("x2"), 0);
                double[] p1 = applyMtxAbs(childCtm, x1, 0);
                double[] p2 = applyMtxAbs(childCtm, x2, 0);
                minX = Math.min(minX, Math.min(p1[0], p2[0]));
                maxX = Math.max(maxX, Math.max(p1[0], p2[0]));
            } else {
                double[] nums = parseNumbers(d);
                for (int j = 0; j + 1 < nums.length; j += 2) {
                    double[] pt = applyMtxAbs(childCtm, nums[j], nums[j + 1]);
                    minX = Math.min(minX, pt[0]);
                    maxX = Math.max(maxX, pt[0]);
                }
            }
        }
        return (minX <= maxX) ? new double[]{minX, maxX} : null;
    }
}
