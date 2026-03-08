package org.texttechnologylab.udav.widgets.tools;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Converts an SVG string to a standalone LaTeX/TikZ document.
 *
 * Supported SVG elements (matching the bar-chart use-case):
 *   <svg>, <g transform="translate(x,y)">, <rect>, <path d="M/L/H/V/Z">,
 *   <line>, <text transform="translate(x,y)rotate(deg)">
 *
 * Coordinate conversion:
 *   - SVG px  →  TikZ cm  (1 cm = 37.795 px)
 *   - y-axis is flipped   (SVG y↓  →  TikZ y↑)
 *
 * Colors:
 *   Every hex colour found in the SVG is registered and emitted as a
 *   \definecolor{cRRGGBB}{RGB}{R,G,B} command — TikZ built-in names are
 *   NEVER used.  "currentColor" and other non-hex values resolve to #000000.
 */
public class SvgToLaTeXConverter {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** 1 inch = 96 px; 1 inch = 2.54 cm  →  1 px = 2.54/96 cm */
    private static final double PX_TO_CM = 2.54 / 96.0;

    /**
     * Fallback hex for SVG values that carry no explicit hex colour.
     * "currentColor" is treated as black; "none" is handled separately.
     */
    private static final String CURRENT_COLOR_HEX = "#000000";

    // -----------------------------------------------------------------------
    // Per-conversion state
    // -----------------------------------------------------------------------

    private double svgHeight;

    /** hex (lower-case, with #)  →  tikz color name */
    private final Map<String, String> colorDefs = new LinkedHashMap<>();

    /** id → [x, y, width, height] of the first <rect> child of each <clipPath> */
    private final Map<String, double[]> clipRects = new LinkedHashMap<>();

    private final StringBuilder body = new StringBuilder();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Convert an SVG string to a standalone TikZ LaTeX document string.
     *
     * @param svgString the full SVG XML source
     * @return a complete LaTeX document that renders the chart
     * @throws Exception on XML parse errors
     */
    public String convert(String svgString) throws Exception {
        // Reset state for re-use
        colorDefs.clear();
        clipRects.clear();
        body.setLength(0);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(svgString.getBytes("UTF-8")));

        Element root = doc.getDocumentElement();
        svgHeight = parseDouble(root.getAttribute("height"), 0);

        // Pass 1a – collect clipPath rect definitions
        collectClipPaths(root);
        // Pass 1b – collect every colour referenced in the tree
        collectColors(root);

        // Pass 2 – emit TikZ commands, accumulating absolute translations
        processNode(root, 0, 0, 1.0, new InheritedAttrs());

        return buildDocument();
    }

    // -----------------------------------------------------------------------
    // ClipPath collection
    // -----------------------------------------------------------------------

    /**
     * Pre-scan the tree for every {@code <clipPath id="…">} and record the
     * bounding rect of its first {@code <rect>} child.  The rect coordinates
     * are stored RAW (in the clipPath's own local space) so that the caller
     * can later combine them with the accumulated tx/ty of the referencing
     * element.
     */
    private void collectClipPaths(Node node) {
        if (node instanceof Element) {
            Element el = (Element) node;
            String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
            if ("clippath".equals(tag)) {
                String id = el.getAttribute("id");
                if (!id.isEmpty()) {
                    NodeList kids = el.getChildNodes();
                    for (int i = 0; i < kids.getLength(); i++) {
                        if (!(kids.item(i) instanceof Element)) continue;
                        Element child = (Element) kids.item(i);
                        String ct = child.getTagName().replaceFirst(".*:", "").toLowerCase();
                        if ("rect".equals(ct)) {
                            double x = parseDouble(child.getAttribute("x"), 0);
                            double y = parseDouble(child.getAttribute("y"), 0);
                            double w = parseDouble(child.getAttribute("width"),  0);
                            double h = parseDouble(child.getAttribute("height"), 0);
                            clipRects.put(id, new double[]{x, y, w, h});
                            break; // only the first rect matters
                        }
                    }
                }
            }
        }
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) collectClipPaths(kids.item(i));
    }

    // -----------------------------------------------------------------------
    // Color collection
    // -----------------------------------------------------------------------

    private void collectColors(Node node) {
        if (node instanceof Element) {
            Element el = (Element) node;
            for (String attr : new String[]{"fill", "stroke"}) {
                String v = el.getAttribute(attr).trim();
                if (v.startsWith("#")) {
                    if (v.length() == 4) v = "#" + v.charAt(1) + "" + v.charAt(1)
                            + v.charAt(2) + v.charAt(2)
                            + v.charAt(3) + v.charAt(3);
                    registerHex(v);
                } else if (v.startsWith("rgb")) {
                    String h = rgbToHex(v); if (h != null) registerHex(h);
                } else if (!v.isEmpty() && !v.equals("none") && !v.equals("transparent")
                        && !v.equals("currentColor")) {
                    String h = CSS_NAMED_COLORS.get(v.toLowerCase());
                    if (h != null) registerHex(h);
                }
            }
            String style = el.getAttribute("style");
            if (!style.isEmpty()) {
                Matcher m = Pattern.compile("(?:fill|stroke)\\s*:\\s*(#[0-9a-fA-F]{3,6})").matcher(style);
                while (m.find()) {
                    String hx = m.group(1);
                    if (hx.length() == 4) hx = "#" + hx.charAt(1) + "" + hx.charAt(1)
                            + hx.charAt(2) + hx.charAt(2)
                            + hx.charAt(3) + hx.charAt(3);
                    registerHex(hx);
                }
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

    /**
     * Converts "rgb(255, 84, 0)" or "rgb(255,84,0)" to "#ff5400".
     * Returns null if the string cannot be parsed.
     */
    private String rgbToHex(String rgb) {
        Matcher m = Pattern.compile("rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)").matcher(rgb.trim());
        if (!m.find()) return null;
        int r = Integer.parseInt(m.group(1));
        int g = Integer.parseInt(m.group(2));
        int b = Integer.parseInt(m.group(3));
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private void registerHex(String hex) {
        String key = hex.toLowerCase();
        if (!colorDefs.containsKey(key)) colorDefs.put(key, deriveColorName(key));
    }

    /** Always produces a safe TikZ identifier from the hex value, e.g. "cff0000". */
    private String deriveColorName(String hex) {
        return "c" + hex.toLowerCase().replace("#", "");
    }

    private String colorName(String hex) {
        return colorDefs.getOrDefault(hex.toLowerCase(), "black");
    }

    // -----------------------------------------------------------------------
    // Node dispatch
    // -----------------------------------------------------------------------

    /**
     * Simple container for inherited SVG presentation attributes.
     * We only track the small subset used in this SVG grammar.
     */
    private static class InheritedAttrs {
        String fill   = "currentColor";  // SVG spec default fill is black
        String stroke = "currentColor";
        String textAnchor = "start";
        double fontSize = 10;

        InheritedAttrs copy() {
            InheritedAttrs c = new InheritedAttrs();
            c.fill       = fill;
            c.stroke     = stroke;
            c.textAnchor = textAnchor;
            c.fontSize   = fontSize;
            return c;
        }
    }

    private void processNode(Node node, double tx, double ty, double scale, InheritedAttrs inh) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();

        switch (tag) {
            case "svg":      processGroup(el, tx, ty, scale, inh);  break;
            case "g":        processGroup(el, tx, ty, scale, inh);  break;
            case "rect":     processRect(el, tx, ty, scale, inh);   break;
            case "circle":   processCircle(el, tx, ty, scale, inh); break;
            case "path":     processPath(el, tx, ty, scale, inh);   break;
            case "line":     processLine(el, tx, ty, scale, inh);   break;
            case "text":     processText(el, tx, ty, scale, inh);   break;
            case "clippath": break;
        }
    }

    // -----------------------------------------------------------------------
    // <g> / <svg>
    // -----------------------------------------------------------------------

    private void processGroup(Element el, double tx, double ty, double scale, InheritedAttrs inh) {
        String transformAttr = el.getAttribute("transform");
        double[] dt       = parseTranslate(transformAttr);
        double childScale = parseScale(transformAttr);
        // Compose: new_screen = parentScale * (childScale * local + childT) + parentT
        //                     = (parentScale*childScale) * local + (parentT + parentScale*childT)
        double newScale = scale * childScale;
        double newTx    = tx + scale * dt[0];
        double newTy    = ty + scale * dt[1];

        InheritedAttrs ni = inh.copy();
        if (!el.getAttribute("fill").isEmpty())        ni.fill       = el.getAttribute("fill");
        if (!el.getAttribute("stroke").isEmpty())      ni.stroke     = el.getAttribute("stroke");
        if (!el.getAttribute("text-anchor").isEmpty()) ni.textAnchor = el.getAttribute("text-anchor");
        if (!el.getAttribute("font-size").isEmpty())
            ni.fontSize = parseDouble(el.getAttribute("font-size"), ni.fontSize);

        // clip-path: the clip rect lives in the coordinate space of THIS element
        // (i.e. after newTx/newTy/newScale have been applied), so we use those.
        boolean clipScope = false;
        String clipPathAttr = el.getAttribute("clip-path");
        if (!clipPathAttr.isEmpty()) {
            Matcher cm = Pattern.compile("url\\(#([^)]+)\\)").matcher(clipPathAttr);
            if (cm.find()) {
                double[] rect = clipRects.get(cm.group(1));
                if (rect != null) {
                    double x1 = toX(newScale * rect[0]              + newTx);
                    double y1 = toY(newScale * (rect[1] + rect[3])  + newTy);
                    double x2 = toX(newScale * (rect[0] + rect[2])  + newTx);
                    double y2 = toY(newScale * rect[1]               + newTy);
                    body.append("\\begin{scope}\n");
                    body.append(String.format(Locale.US,
                            "\\clip (%.4f, %.4f) rectangle (%.4f, %.4f);\n",
                            x1, y1, x2, y2));
                    clipScope = true;
                }
            }
        }

        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
            processNode(kids.item(i), newTx, newTy, newScale, ni);

        if (clipScope) body.append("\\end{scope}\n");
    }

    // -----------------------------------------------------------------------
    // <rect>
    // -----------------------------------------------------------------------

    private void processRect(Element el, double tx, double ty, double scale, InheritedAttrs inh) {
        double x = scale * parseDouble(el.getAttribute("x"), 0) + tx;
        double y = scale * parseDouble(el.getAttribute("y"), 0) + ty;
        double w = scale * parseDouble(el.getAttribute("width"),  0);
        double h = scale * parseDouble(el.getAttribute("height"), 0);

        String fill   = resolveFill(el, inh);
        String stroke = resolveStroke(el, inh);

        // SVG rect: top-left (x,y), bottom-right (x+w, y+h)
        double tikzX1 = toX(x);
        double tikzY1 = toY(y);        // top    in SVG → higher y in TikZ
        double tikzX2 = toX(x + w);
        double tikzY2 = toY(y + h);    // bottom in SVG → lower y in TikZ

        List<String> opts = new ArrayList<>();
        if (!fill.equals("none"))   opts.add("fill="   + fill);
        if (!stroke.equals("none")) opts.add("draw="   + stroke);

        double opacity = parseStyleDouble(el.getAttribute("style"), "opacity", -1);
        if (opacity >= 0 && opacity < 1) opts.add(String.format(Locale.US, "opacity=%.2f", opacity));

        body.append(String.format(Locale.US,
                "\\path[%s] (%.4f, %.4f) rectangle (%.4f, %.4f);\n",
                String.join(", ", opts), tikzX1, tikzY1, tikzX2, tikzY2));
    }

    // -----------------------------------------------------------------------
    // <circle>
    // -----------------------------------------------------------------------

    private void processCircle(Element el, double tx, double ty, double scale, InheritedAttrs inh) {
        double cx = scale * parseDouble(el.getAttribute("cx"), 0) + tx;
        double cy = scale * parseDouble(el.getAttribute("cy"), 0) + ty;
        double r  = scale * parseDouble(el.getAttribute("r"),  0);
        String fill   = resolveFill(el, inh);
        String stroke = resolveStroke(el, inh);

        List<String> opts = new ArrayList<>();
        if (!fill.equals("none"))   opts.add("fill="  + fill);
        if (!stroke.equals("none")) opts.add("draw="  + stroke);
        if (opts.isEmpty()) opts.add("draw=c000000");

        double opacity = parseStyleDouble(el.getAttribute("style"), "opacity", -1);
        if (opacity >= 0 && opacity < 1)
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));

        body.append(String.format(Locale.US,
                "\\path[%s] (%.4f, %.4f) circle (%.4fcm);\n",
                String.join(", ", opts), toX(cx), toY(cy), r * PX_TO_CM));
    }

    // -----------------------------------------------------------------------
    // <path d="…">  – M/L/H/V/Z/C and their lower-case relatives
    // -----------------------------------------------------------------------

    private void processPath(Element el, double tx, double ty, double scale, InheritedAttrs inh) {
        String d = el.getAttribute("d").trim();
        if (d.isEmpty()) return;

        String fill   = resolveFill(el, inh);
        String stroke = resolveStroke(el, inh);

        double strokeWidth = parseDouble(el.getAttribute("stroke-width"), -1);
        if (strokeWidth < 0)
            strokeWidth = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);

        String pathStr = buildTikzPath(d, tx, ty, scale);
        if (pathStr.isEmpty()) return;

        List<String> opts = new ArrayList<>();
        if (!stroke.equals("none")) {
            opts.add("draw=" + stroke);
            if (strokeWidth > 0)
                opts.add(String.format(Locale.US, "line width=%.4fcm", strokeWidth * PX_TO_CM));
        }
        if (!fill.equals("none")) opts.add("fill=" + fill);
        if (opts.isEmpty())       opts.add("draw=c000000");

        double opacity = parseStyleDouble(el.getAttribute("style"), "opacity", -1);
        if (opacity >= 0 && opacity < 1)
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));

        body.append(String.format("\\path[%s] %s;\n", String.join(", ", opts), pathStr));
    }

    /**
     * Converts an SVG path "d" string into a TikZ path string.
     *
     * Supported commands: M m  L l  H h  V v  Z z  C c
     *
     * Straight segments  →  " -- (x,y)"
     * Cubic bezier (C/c) →  ".. controls (c1) and (c2) .. (end)"
     * Close path (Z/z)   →  " -- cycle"
     */
    private String buildTikzPath(String d, double tx, double ty, double scale) {
        StringBuilder sb = new StringBuilder();
        double cx = 0, cy = 0;
        double startX = 0, startY = 0;
        boolean firstSeg = true;

        Matcher m = Pattern.compile("([MmLlHhVvZzCcAa])([^MmLlHhVvZzCcAa]*)").matcher(d);
        while (m.find()) {
            char cmd  = m.group(1).charAt(0);
            double[] a = parseNumbers(m.group(2));

            switch (cmd) {
                case 'M': {
                    cx = scale * a[0] + tx; cy = scale * a[1] + ty;
                    startX = cx; startY = cy;
                    if (!firstSeg) sb.append(" ");
                    sb.append(tikzPt(cx, cy));
                    firstSeg = false;
                    for (int i = 2; i + 1 < a.length; i += 2) {
                        cx = scale * a[i] + tx; cy = scale * a[i+1] + ty;
                        sb.append(" -- ").append(tikzPt(cx, cy));
                    }
                    break;
                }
                case 'm': {
                    cx += scale * a[0]; cy += scale * a[1];
                    startX = cx; startY = cy;
                    if (!firstSeg) sb.append(" ");
                    sb.append(tikzPt(cx, cy));
                    firstSeg = false;
                    for (int i = 2; i + 1 < a.length; i += 2) {
                        cx += scale * a[i]; cy += scale * a[i+1];
                        sb.append(" -- ").append(tikzPt(cx, cy));
                    }
                    break;
                }
                case 'L': {
                    for (int i = 0; i + 1 < a.length; i += 2) {
                        cx = scale * a[i] + tx; cy = scale * a[i+1] + ty;
                        sb.append(" -- ").append(tikzPt(cx, cy));
                    }
                    break;
                }
                case 'l': {
                    for (int i = 0; i + 1 < a.length; i += 2) {
                        cx += scale * a[i]; cy += scale * a[i+1];
                        sb.append(" -- ").append(tikzPt(cx, cy));
                    }
                    break;
                }
                case 'H': {
                    for (double v : a) { cx = scale * v + tx; sb.append(" -- ").append(tikzPt(cx, cy)); }
                    break;
                }
                case 'h': {
                    for (double v : a) { cx += scale * v; sb.append(" -- ").append(tikzPt(cx, cy)); }
                    break;
                }
                case 'V': {
                    for (double v : a) { cy = scale * v + ty; sb.append(" -- ").append(tikzPt(cx, cy)); }
                    break;
                }
                case 'v': {
                    for (double v : a) { cy += scale * v; sb.append(" -- ").append(tikzPt(cx, cy)); }
                    break;
                }
                case 'Z': case 'z': {
                    sb.append(" -- cycle");
                    cx = startX; cy = startY;
                    break;
                }
                case 'C': {
                    for (int i = 0; i + 5 < a.length; i += 6) {
                        double x1 = scale * a[i]   + tx, y1 = scale * a[i+1] + ty;
                        double x2 = scale * a[i+2] + tx, y2 = scale * a[i+3] + ty;
                        double x  = scale * a[i+4] + tx, y  = scale * a[i+5] + ty;
                        sb.append(String.format(Locale.US,
                                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                                toX(x1), toY(y1), toX(x2), toY(y2), toX(x), toY(y)));
                        cx = x; cy = y;
                    }
                    break;
                }
                case 'A': {
                    for (int i = 0; i + 6 < a.length; i += 7) {
                        double rx2 = scale * a[i];
                        double ry2 = scale * a[i+1];
                        boolean la = a[i+3] != 0, sw = a[i+4] != 0;
                        double ex = scale * a[i+5] + tx, ey = scale * a[i+6] + ty;
                        sb.append(svgArcToBezier(cx, cy, rx2, ry2, a[i+2], la, sw, ex, ey));
                        cx = ex; cy = ey;
                    }
                    break;
                }
                case 'a': {
                    for (int i = 0; i + 6 < a.length; i += 7) {
                        double rx2 = scale * a[i];
                        double ry2 = scale * a[i+1];
                        boolean la = a[i+3] != 0, sw = a[i+4] != 0;
                        double ex = cx + scale * a[i+5], ey = cy + scale * a[i+6];
                        sb.append(svgArcToBezier(cx, cy, rx2, ry2, a[i+2], la, sw, ex, ey));
                        cx = ex; cy = ey;
                    }
                    break;
                }
                case 'c': {
                    for (int i = 0; i + 5 < a.length; i += 6) {
                        double x1 = cx + scale * a[i],   y1 = cy + scale * a[i+1];
                        double x2 = cx + scale * a[i+2], y2 = cy + scale * a[i+3];
                        double x  = cx + scale * a[i+4], y  = cy + scale * a[i+5];
                        sb.append(String.format(Locale.US,
                                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                                toX(x1), toY(y1), toX(x2), toY(y2), toX(x), toY(y)));
                        cx = x; cy = y;
                    }
                    break;
                }
            }
        }
        return sb.toString();
    }

    /** Format one SVG coordinate pair as a TikZ coordinate after axis conversion. */
    private String tikzPt(double svgX, double svgY) {
        return String.format(Locale.US, "(%.4f, %.4f)", toX(svgX), toY(svgY));
    }

    // -----------------------------------------------------------------------
    // SVG arc → cubic Bézier conversion
    // -----------------------------------------------------------------------

    /**
     * Converts one SVG arc command (endpoint parameterization) into a series of
     * TikZ ".. controls .. and .. .." Bézier segments.
     *
     * All coordinate parameters are in SVG world space (scale/tx/ty already applied).
     * The conversion follows the algorithm in the SVG 1.1 spec, Appendix F.
     *
     * @param x1,y1  current point (start of arc)
     * @param rx,ry  radii (already scaled)
     * @param xRotDeg  x-axis rotation in degrees
     * @param largeArc  large-arc-flag
     * @param sweep     sweep-flag (true = positive angle direction in SVG, i.e. clockwise)
     * @param x2,y2  endpoint
     */
    private String svgArcToBezier(double x1, double y1,
                                  double rx, double ry, double xRotDeg,
                                  boolean largeArc, boolean sweep,
                                  double x2, double y2) {
        if (x1 == x2 && y1 == y2) return "";
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0) {
            // Degenerate: straight line
            return String.format(Locale.US, " -- (%.4f, %.4f)", toX(x2), toY(y2));
        }

        double phi    = Math.toRadians(xRotDeg);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        // Step 1 – midpoint transform
        double dx2 = (x1 - x2) / 2.0;
        double dy2 = (y1 - y2) / 2.0;
        double x1p =  cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        // Step 2 – ensure radii are large enough
        double x1pSq = x1p * x1p, y1pSq = y1p * y1p;
        double rxSq  = rx * rx,    rySq  = ry * ry;
        double lambda = x1pSq / rxSq + y1pSq / rySq;
        if (lambda > 1) {
            double sqrtL = Math.sqrt(lambda);
            rx *= sqrtL; ry *= sqrtL;
            rxSq = rx * rx; rySq = ry * ry;
        }

        // Step 3 – compute center (cx', cy') in rotated frame
        double num = Math.max(0, rxSq * rySq - rxSq * y1pSq - rySq * x1pSq);
        double den = rxSq * y1pSq + rySq * x1pSq;
        double sq  = (den == 0) ? 0 : Math.sqrt(num / den);
        if (largeArc == sweep) sq = -sq;
        double cxp =  sq * rx * y1p / ry;
        double cyp = -sq * ry * x1p / rx;

        // Step 4 – transform center back to original coords
        double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0;
        double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0;

        // Step 5 – compute start angle θ1 and arc extent Δθ
        double ux =  (x1p - cxp) / rx,  uy =  (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx,  vy = (-y1p - cyp) / ry;
        double theta1 = svgVectorAngle(1, 0, ux, uy);
        double dTheta  = svgVectorAngle(ux, uy, vx, vy);
        if (!sweep && dTheta > 0) dTheta -= 2 * Math.PI;
        if ( sweep && dTheta < 0) dTheta += 2 * Math.PI;

        // Step 6 – split into ≤90° segments; approximate each with a cubic Bézier
        int nSegs = Math.max(1, (int) Math.ceil(Math.abs(dTheta) / (Math.PI / 2)));
        double dThetaSeg = dTheta / nSegs;
        StringBuilder sb = new StringBuilder();
        double t = theta1;
        for (int i = 0; i < nSegs; i++) {
            sb.append(arcSegmentToBezier(cx, cy, rx, ry, cosPhi, sinPhi, t, t + dThetaSeg));
            t += dThetaSeg;
        }
        return sb.toString();
    }

    /** Signed angle between vectors (ux,uy) and (vx,vy). */
    private double svgVectorAngle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt((ux*ux + uy*uy) * (vx*vx + vy*vy));
        double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        if (ux * vy - uy * vx < 0) a = -a;
        return a;
    }

    /**
     * Approximates one arc segment (|dTheta| ≤ π/2) as a cubic Bézier and
     * returns the TikZ ".. controls (p1) and (p2) .. (end)" string.
     * All coordinates are in SVG world space; toX/toY are applied for output.
     */
    private String arcSegmentToBezier(double cx, double cy,
                                      double rx, double ry,
                                      double cosPhi, double sinPhi,
                                      double theta, double thetaEnd) {
        double dTheta = thetaEnd - theta;
        // Cubic Bézier approximation: alpha = (4/3)*tan(dTheta/4)
        double alpha = (4.0 / 3.0) * Math.tan(dTheta / 4.0);

        double cosT  = Math.cos(theta),    sinT  = Math.sin(theta);
        double cosT2 = Math.cos(thetaEnd), sinT2 = Math.sin(thetaEnd);

        // Endpoint and derivative vectors on the unit ellipse (in ellipse frame)
        double ex1 = rx * cosT,  ey1 = ry * sinT;   // start on ellipse
        double ex2 = rx * cosT2, ey2 = ry * sinT2;  // end on ellipse
        double dx1 = -rx * sinT,  dy1 = ry * cosT;  // derivative at start
        double dx2 = -rx * sinT2, dy2 = ry * cosT2; // derivative at end

        // Control points in ellipse frame → rotate by phi → translate by (cx, cy)
        double[] cp1 = arcToWorld(cx, cy, cosPhi, sinPhi, ex1 + alpha * dx1, ey1 + alpha * dy1);
        double[] cp2 = arcToWorld(cx, cy, cosPhi, sinPhi, ex2 - alpha * dx2, ey2 - alpha * dy2);
        double[] ep  = arcToWorld(cx, cy, cosPhi, sinPhi, ex2, ey2);

        return String.format(Locale.US,
                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                toX(cp1[0]), toY(cp1[1]),
                toX(cp2[0]), toY(cp2[1]),
                toX(ep[0]),  toY(ep[1]));
    }

    /** Rotate (px,py) by phi and translate by (cx,cy) — used for arc control points. */
    private double[] arcToWorld(double cx, double cy, double cosPhi, double sinPhi,
                                double px, double py) {
        return new double[]{ cosPhi * px - sinPhi * py + cx,
                sinPhi * px + cosPhi * py + cy };
    }

    // -----------------------------------------------------------------------
    // <line>
    // -----------------------------------------------------------------------

    private void processLine(Element el, double tx, double ty, double scale, InheritedAttrs inh) {
        double x1 = scale * parseDouble(el.getAttribute("x1"), 0) + tx;
        double y1 = scale * parseDouble(el.getAttribute("y1"), 0) + ty;
        double x2 = scale * parseDouble(el.getAttribute("x2"), 0) + tx;
        double y2 = scale * parseDouble(el.getAttribute("y2"), 0) + ty;
        String stroke = resolveStroke(el, inh);
        body.append(String.format(Locale.US,
                "\\draw[draw=%s] (%.4f, %.4f) -- (%.4f, %.4f);\n",
                stroke, toX(x1), toY(y1), toX(x2), toY(y2)));
    }

    // -----------------------------------------------------------------------
    // <text>
    // -----------------------------------------------------------------------

    private void processText(Element el, double tx, double ty, double scale, InheritedAttrs inh) {
        double x = scale * parseDouble(el.getAttribute("x"), 0);
        double y = scale * parseDouble(el.getAttribute("y"), 0);

        double effectiveY = y;

        String transformAttr = el.getAttribute("transform");
        double ttx = 0, tty = 0, rotate = 0;
        if (!transformAttr.isEmpty()) {
            double[] t = parseTranslate(transformAttr);
            ttx = t[0]; tty = t[1];
            Matcher rm = Pattern.compile("rotate\\(([^)]+)\\)").matcher(transformAttr);
            if (rm.find()) rotate = parseDouble(rm.group(1), 0);
        }

        double localX, localY;
        if (rotate != 0) {
            double rad = Math.toRadians(rotate);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            localX = cos * x - sin * effectiveY + ttx;
            localY = sin * x + cos * effectiveY + tty;
        } else {
            localX = x + ttx;
            localY = effectiveY + tty;
        }

        double finalX = localX + tx;
        double finalY = localY + ty;

        String fill = resolveFill(el, inh);
        // SVG text default fill is black; "none" means no explicit fill was set anywhere
        if ("none".equals(fill)) { registerHex(CURRENT_COLOR_HEX); fill = colorName(CURRENT_COLOR_HEX); }

        // text-anchor can be a direct attribute OR inside style="text-anchor: end;"
        String textAnchor = el.getAttribute("text-anchor");
        if (textAnchor.isEmpty()) {
            Matcher m = Pattern.compile("text-anchor\\s*:\\s*(\\w+)").matcher(el.getAttribute("style"));
            if (m.find()) textAnchor = m.group(1);
        }
        if (textAnchor.isEmpty()) textAnchor = inh.textAnchor;
        // Pass the raw (pre-scale) y attribute so the anchor can distinguish
        // labels above the axis (y<0 → anchor=south) from below (y>0 → anchor=north).
        double rawY = parseDouble(el.getAttribute("y"), 0);
        String tikzAnchor = svgAnchorToTikz(textAnchor, rotate, rawY);

        String content = escapeTex(el.getTextContent());

        List<String> opts = new ArrayList<>();
        opts.add("text=" + fill);
        opts.add("anchor=" + tikzAnchor);
        if (rotate != 0)
            opts.add(String.format(Locale.US, "rotate=%.1f", -rotate)); // SVG CW → TikZ CCW

        body.append(String.format(Locale.US,
                "\\node[%s] at (%.4f, %.4f) {%s};\n",
                String.join(", ", opts), toX(finalX), toY(finalY), content));
    }

    // -----------------------------------------------------------------------
    // Attribute resolution helpers
    // -----------------------------------------------------------------------

    private String resolveFill(Element el, InheritedAttrs inh) {
        String v = directColor(el, "fill");
        if (v != null) return v;
        v = styleColor(el, "fill");
        if (v != null) return v;
        return resolveColorValue(inh.fill);
    }

    private String resolveStroke(Element el, InheritedAttrs inh) {
        String v = directColor(el, "stroke");
        if (v != null) return v;
        v = styleColor(el, "stroke");
        if (v != null) return v;
        return resolveColorValue(inh.stroke);
    }

    /** Returns a TikZ color name/value, or null if the attribute is absent. */
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

    private String resolveColorValue(String v) {
        if (v == null || v.isEmpty()) return "none";
        v = v.trim();
        if (v.startsWith("#")) {
            // Expand 3-digit hex (#rgb → #rrggbb)
            if (v.length() == 4) v = "#" + String.valueOf(v.charAt(1)) + v.charAt(1)
                    + v.charAt(2) + v.charAt(2)
                    + v.charAt(3) + v.charAt(3);
            return colorName(v);
        }
        if (v.equals("none") || v.equals("transparent")) return "none";
        if (v.startsWith("rgb")) {
            String hex = rgbToHex(v);
            if (hex != null) { registerHex(hex); return colorName(hex); }
        }
        // CSS/SVG named colors
        String namedHex = CSS_NAMED_COLORS.get(v.toLowerCase());
        if (namedHex != null) { registerHex(namedHex); return colorName(namedHex); }
        // "currentColor" and anything else → black
        registerHex(CURRENT_COLOR_HEX);
        return colorName(CURRENT_COLOR_HEX);
    }

    /** Subset of CSS named colors most likely to appear in D3/SVG output. */
    private static final Map<String, String> CSS_NAMED_COLORS;
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

    // -----------------------------------------------------------------------
    // Transform parsing
    // -----------------------------------------------------------------------

    /** Returns [tx, ty] from a "translate(x,y)" or "translate(x)" string. */
    private double[] parseTranslate(String transform) {
        double[] r = {0, 0};
        if (transform == null || transform.isEmpty()) return r;
        Matcher m = Pattern.compile("translate\\(([^,)]+)(?:,\\s*([^)]+))?\\)").matcher(transform);
        if (m.find()) {
            r[0] = parseDouble(m.group(1), 0);
            r[1] = m.group(2) != null ? parseDouble(m.group(2), 0) : 0;
        }
        return r;
    }

    /** Returns the uniform scale factor from a "scale(s)" or "scale(sx,sy)" transform, or 1.0. */
    private double parseScale(String transform) {
        if (transform == null || transform.isEmpty()) return 1.0;
        Matcher m = Pattern.compile("scale\\(([^,)]+)").matcher(transform);
        if (m.find()) return parseDouble(m.group(1), 1.0);
        return 1.0;
    }

    // -----------------------------------------------------------------------
    // Coordinate conversion
    // -----------------------------------------------------------------------

    private double toX(double svgX) { return svgX * PX_TO_CM; }

    /** Flip y: SVG y=0 is top; TikZ y=0 is bottom. */
    private double toY(double svgY) { return (svgHeight - svgY) * PX_TO_CM; }

    // -----------------------------------------------------------------------
    // Miscellaneous helpers
    // -----------------------------------------------------------------------

    private String svgAnchorToTikz(String anchor, double rotate, double rawY) {
        // Rotated labels (e.g. -45°) with text-anchor=end → top-right corner at the point.
        if (Math.abs(rotate) == 45 && "end".equals(anchor)) return "north east";
        switch (anchor) {
            case "middle":
                // Bottom x-axis: y>0, dy≈0.71em → text hangs below → anchor=north
                // Top x-axis:    y<0, dy=0em    → text sits above → anchor=south
                return (rawY < 0) ? "south" : "north";
            // "end" = right-aligned; SVG y is baseline, dy≈0.32em ≈ half cap-height,
            // so the visual center lands near the raw y.  TikZ `east` (center) matches.
            case "end":   return "east";
            // "start" = left-aligned; SVG y is the baseline with no dy offset.
            // TikZ `base west` aligns the baseline to the coordinate, matching SVG exactly.
            // (vs `west` which centers vertically — that would sit ~half cap-height too low)
            case "start": return "base west";
            default:      return "north";
        }
    }

    private String escapeTex(String s) {
        return s.replace("\u2212", "-")   // Unicode MINUS SIGN → ASCII hyphen-minus
                .replace("\\", "\\textbackslash{}")
                .replace("_",  "\\_")
                .replace("%",  "\\%")
                .replace("&",  "\\&")
                .replace("$",  "\\$")
                .replace("#",  "\\#")
                .replace("{",  "\\{")
                .replace("}",  "\\}");
    }

    private double parseDouble(String s, double def) {
        if (s == null || s.trim().isEmpty()) return def;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private double parseStyleDouble(String style, String prop, double def) {
        if (style == null || style.isEmpty()) return def;
        Matcher m = Pattern.compile(prop + "\\s*:\\s*([0-9.]+)").matcher(style);
        if (m.find()) return parseDouble(m.group(1), def);
        return def;
    }

    private double[] parseNumbers(String s) {
        if (s == null || s.trim().isEmpty()) return new double[0];
        // Use regex so that sequences like "47.636,-11.374" and "10.5-3.2" both parse correctly.
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

    // -----------------------------------------------------------------------
    // LaTeX document assembly
    // -----------------------------------------------------------------------

    private String buildDocument() {
        StringBuilder sb = new StringBuilder();
        sb.append("\\documentclass{standalone}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        sb.append("\\usepackage{tikz}\n");
        sb.append("\\begin{document}\n");

        // Emit \definecolor for every colour found in the SVG — no exceptions
        for (Map.Entry<String, String> e : colorDefs.entrySet()) {
            String name = e.getValue();
            String hex  = e.getKey().replace("#", "");
            if (hex.length() < 6) continue;
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            sb.append(String.format("\\definecolor{%s}{RGB}{%d,%d,%d}\n", name, r, g, b));
        }

        sb.append("\\begin{tikzpicture}[x=1cm, y=1cm]\n");
        sb.append(body);
        sb.append("\\end{tikzpicture}\n");
        sb.append("\\end{document}\n");
        return sb.toString();
    }
}