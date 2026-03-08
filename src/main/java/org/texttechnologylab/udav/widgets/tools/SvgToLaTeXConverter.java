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
 *   - SVG px  �  TikZ cm  (1 cm = 37.795 px)
 *   - y-axis is flipped   (SVG y�  �  TikZ y�)
 *
 * Colors:
 *   Every hex colour found in the SVG is registered and emitted as a
 *   \definecolor{cRRGGBB}{RGB}{R,G,B} command  TikZ built-in names are
 *   NEVER used.  "currentColor" and other non-hex values resolve to #000000.
 */
public class SvgToLaTeXConverter {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** 1 inch = 96 px; 1 inch = 2.54 cm  �  1 px = 2.54/96 cm */
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

    /** hex (lower-case, with #)  �  tikz color name */
    private final Map<String, String> colorDefs = new LinkedHashMap<>();

    /** id � [x, y, width, height] of the first <rect> child of each <clipPath> */
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

        // Pass 1a  collect clipPath rect definitions
        collectClipPaths(root);
        // Pass 1b  collect every colour referenced in the tree
        collectColors(root);

        // Pass 2  emit TikZ commands, accumulating absolute translations
        processNode(root, 0, 0, 1.0, new InheritedAttrs());

        return buildDocument();
    }

    // -----------------------------------------------------------------------
    // ClipPath collection
    // -----------------------------------------------------------------------

    /**
     * Pre-scan the tree for every {@code <clipPath id="&">} and record the
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
                if (v.startsWith("#")) registerHex(v);
            }
            String style = el.getAttribute("style");
            if (!style.isEmpty()) {
                Matcher m = Pattern.compile("(?:fill|stroke)\\s*:\\s*(#[0-9a-fA-F]{6})").matcher(style);
                while (m.find()) registerHex(m.group(1));
            }
        }
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) collectColors(kids.item(i));
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
        String fill   = "none";
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
        double tikzY1 = toY(y);        // top    in SVG � higher y in TikZ
        double tikzX2 = toX(x + w);
        double tikzY2 = toY(y + h);    // bottom in SVG � lower y in TikZ

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
    // <path d="&">   M/L/H/V/Z/C and their lower-case relatives
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
     * Straight segments  �  " -- (x,y)"
     * Cubic bezier (C/c) �  ".. controls (c1) and (c2) .. (end)"
     * Close path (Z/z)   �  " -- cycle"
     */
    private String buildTikzPath(String d, double tx, double ty, double scale) {
        StringBuilder sb = new StringBuilder();
        double cx = 0, cy = 0;
        double startX = 0, startY = 0;
        boolean firstSeg = true;

        Matcher m = Pattern.compile("([MmLlHhVvZzCc])([^MmLlHhVvZzCc]*)").matcher(d);
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

        // text-anchor can be a direct attribute OR inside style="text-anchor: end;"
        String textAnchor = el.getAttribute("text-anchor");
        if (textAnchor.isEmpty()) {
            Matcher m = Pattern.compile("text-anchor\\s*:\\s*(\\w+)").matcher(el.getAttribute("style"));
            if (m.find()) textAnchor = m.group(1);
        }
        if (textAnchor.isEmpty()) textAnchor = inh.textAnchor;
        String tikzAnchor = svgAnchorToTikz(textAnchor, rotate);

        String content = escapeTex(el.getTextContent());

        List<String> opts = new ArrayList<>();
        opts.add("text=" + fill);
        opts.add("anchor=" + tikzAnchor);
        if (rotate != 0)
            opts.add(String.format(Locale.US, "rotate=%.1f", -rotate)); // SVG CW � TikZ CCW

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
        if (v.startsWith("#")) return colorName(v);
        if (v.equals("none")) return "none";
        // "currentColor" and any other non-hex value � treat as black (#000000)
        registerHex(CURRENT_COLOR_HEX);
        return colorName(CURRENT_COLOR_HEX);
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

    private String svgAnchorToTikz(String anchor, double rotate) {
        // Rotated labels (e.g. -45�) with text-anchor=end � top-right corner at the point.
        if (Math.abs(rotate) == 45 && "end".equals(anchor)) return "north east";
        switch (anchor) {
            // "middle" = horizontally centred; in SVG the label hangs *below* its y
            // coordinate (dyH0.71em pushes the baseline down by ~1 line height so the
            // top of the glyph sits at y).  TikZ `north` places the top of the text
            // box at the coordinate, which is the exact equivalent.
            case "middle": return "north";
            // "end" = right-aligned, vertically centred at the coordinate.
            // SVG dyH0.32em (~half line height) achieves the same centering that
            // TikZ `east` gives automatically.
            case "end":    return "east";
            case "start":  return "west";
            default:       return "north";
        }
    }

    private String escapeTex(String s) {
        return s.replace("\u2212", "-")   // Unicode MINUS SIGN � ASCII hyphen-minus
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

        // Emit \definecolor for every colour found in the SVG  no exceptions
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