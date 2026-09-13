package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.parseDouble;
import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.parseNumbers;

/**
 * Turns {@code <pattern>} paint servers into TikZ fill options.
 * <p>
 * A tile is mapped onto one of the tilings of TikZ's {@code patterns} library
 * (dots, hatching, crosshatch, grid) when it resembles one; a plain dot grid gets a
 * custom pattern cell so its phase matches the document. Any other tile falls back
 * to a flat fill of its dominant colour, lightened by the estimated coverage, since
 * an unresolved paint server would paint nothing. A recognised pattern never yields
 * null: the shape always gets some fill.
 */
public class PatternHandler {

    /** Tolerance below which a stroke direction component counts as zero. */
    private static final double ORIENTATION_MARGIN = 1e-6;

    private final ConversionContext ctx;

    public PatternHandler(ConversionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Builds TikZ fill options for {@code url(#id)} when the id names a {@code <pattern>}.
     *
     * @return the option string, or null when {@code rawFill} is not a pattern reference
     */
    public String buildPatternOpts(String rawFill) {
        if (rawFill == null || !rawFill.startsWith("url(#")) return null;
        String id = rawFill.replaceAll("url\\(#([^)]+)\\).*", "$1").trim();
        Element pattern = ctx.defsMap.get(id);
        if (pattern == null) return null;
        if (!"pattern".equals(localName(pattern))) return null;

        Element tile = resolveTile(pattern);
        List<Element> shapes = drawableChildren(tile);
        if (shapes.isEmpty()) {
            // A pattern with no content paints nothing, per spec.
            return "fill=none";
        }

        String colorName = ctx.colors.colorName(dominantHex(shapes));

        // A dot grid gets a custom cell so its phase can be placed; the library's
        // tilings are tried after that.
        String dots = declareDotPattern(pattern, shapes);
        if (dots != null) {
            ctx.usesPatternLibrary = true;
            return "pattern=" + dots + ", pattern color=" + colorName;
        }

        String tikzPattern = classify(shapes);
        if (tikzPattern != null) {
            ctx.usesPatternLibrary = true;
            return "pattern={" + sized(tikzPattern, pattern, shapes) + "}, pattern color=" + colorName;
        }

        // No TikZ equivalent: flat fill, lightened by the tile's estimated coverage.
        double coverage = estimateCoverage(pattern, tile, shapes);
        String blended = ctx.colors.blendTowardsWhite(dominantHex(shapes), coverage);
        return "fill=" + ctx.colors.colorName(blended);
    }

    /**
     * Declares a dot tiling as a custom pattern cell placed at the right phase.
     * <p>
     * The library's {@code Dots} anchors its cells to the canvas origin and offers no
     * phase control, so a tiling with the right spacing, size and colour can still
     * sit half a period out of step with the document. A cell declared with
     * {@code \pgfdeclarepatternformonly} lets the mark be positioned within the cell.
     * The phase is the dot's TikZ position taken modulo the cell size. The mark is
     * drawn nine times, once per neighbouring cell, because pgf clips a cell to its
     * own box and a dot straddling the edge would otherwise be cut in half.
     *
     * @return the declared pattern's name, or null when the tile is not a plain dot grid
     */
    private String declareDotPattern(Element pattern, List<Element> shapes) {
        if (shapes.size() != 1) return null;
        Element dot = shapes.get(0);
        String tag = localName(dot);
        if (!tag.equals("circle") && !tag.equals("ellipse")) return null;
        if (!dot.getAttribute("transform").isEmpty()) return null;

        double tileW = parseDouble(inherited(pattern, "width"), 0);
        double tileH = parseDouble(inherited(pattern, "height"), 0);
        if (tileW <= 0 || tileH <= 0) return null;

        // A viewBox on the pattern scales its content into the tile, so the dot's
        // coordinates are in viewBox units, not tile units.
        double contentScaleX = 1, contentScaleY = 1;
        double[] vb = parseNumbers(inherited(pattern, "viewBox"));
        if (vb.length >= 4 && vb[2] > 0 && vb[3] > 0) {
            contentScaleX = tileW / vb[2];
            contentScaleY = tileH / vb[3];
        }

        double cx = parseDouble(dot.getAttribute("cx"), 0) * contentScaleX;
        double cy = parseDouble(dot.getAttribute("cy"), 0) * contentScaleY;
        double radius = dotRadius(shapes) * Math.min(contentScaleX, contentScaleY);
        if (radius <= 0) return null;

        // The pattern's own origin offsets the whole tiling.
        double originX = parseDouble(inherited(pattern, "x"), 0);
        double originY = parseDouble(inherited(pattern, "y"), 0);

        double cellW = tileW * TransformUtils.PX_TO_CM;
        double cellH = tileH * TransformUtils.PX_TO_CM;
        // Where the dot lands on the TikZ canvas, reduced into one cell. The y
        // conversion is the flipped one, so the phase differs from an unshifted
        // tiling's.
        double markX = positiveMod(TransformUtils.toX(originX + cx), cellW);
        double markY = positiveMod(TransformUtils.toY(originY + cy, ctx.svgHeight), cellH);

        StringBuilder cell = new StringBuilder();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                cell.append(String.format(Locale.US,
                        "\\pgfpathcircle{\\pgfpoint{%.4fcm}{%.4fcm}}{%.4fcm}",
                        markX + dx * cellW, markY + dy * cellH, radius * TransformUtils.PX_TO_CM));
            }
        }
        cell.append("\\pgfusepath{fill}");

        String spec = String.format(Locale.US, "%.4f|%.4f|%.4f|%.4f|%.4f",
                cellW, cellH, markX, markY, radius);
        String name = patternName(spec);
        ctx.pendingPatterns.putIfAbsent(name, String.format(Locale.US,
                "\\pgfdeclarepatternformonly{%s}{\\pgfpointorigin}{\\pgfpoint{%.4fcm}{%.4fcm}}"
                        + "{\\pgfpoint{%.4fcm}{%.4fcm}}{%s}%n",
                name, cellW, cellH, cellW, cellH, cell));
        return name;
    }

    /**
     * Reads a pattern attribute, following {@code href} when the pattern does not carry
     * it. Pattern attributes inherit along that chain as gradient stops do; a
     * positioned instance typically declares only its offset.
     */
    private String inherited(Element pattern, String name) {
        Element current = pattern;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            String value = current.getAttribute(name);
            if (!value.isEmpty()) return value;
            String href = current.getAttribute("xlink:href");
            if (href.isEmpty()) href = current.getAttribute("href");
            if (!href.startsWith("#")) return "";
            current = ctx.defsMap.get(href.substring(1));
        }
        return "";
    }

    /** Remainder that is always in [0, modulus), unlike Java's {@code %} on negatives. */
    private static double positiveMod(double value, double modulus) {
        double r = value % modulus;
        return r < 0 ? r + modulus : r;
    }

    /** A stable, letters-only name derived from the cell's geometry, so cells are shared. */
    private static String patternName(String spec) {
        long h = spec.hashCode() & 0xFFFFFFFFL;
        StringBuilder sb = new StringBuilder("svgpattern");
        do { sb.append((char) ('a' + (int) (h % 26))); h /= 26; } while (h > 0);
        return sb.toString();
    }

    /**
     * Renders the pattern name with the tile's real dimensions attached.
     * The plain {@code patterns} library only offers fixed cell sizes;
     * {@code patterns.meta} takes the spacing and the mark size as keys, so the
     * tiling matches the document's dimensions.
     */
    private String sized(String tikzPattern, Element pattern, List<Element> shapes) {
        double tileW = parseDouble(pattern.getAttribute("width"), 0);
        double tileH = parseDouble(pattern.getAttribute("height"), 0);
        double spacingPx = (tileW > 0 && tileH > 0) ? Math.min(tileW, tileH)
                         : Math.max(tileW, tileH);
        if (spacingPx <= 0) return tikzPattern;   // no usable tile: keep the default size

        String distance = cm(spacingPx);
        return switch (tikzPattern) {
            case "Dots" -> String.format(Locale.US, "Dots[distance=%s,radius=%s]",
                    distance, cm(dotRadius(shapes)));
            case "Lines" -> {
                double angle = lineAngle(shapes);
                yield String.format(Locale.US, "Lines[distance=%s,angle=%.1f,line width=%s]",
                        cm(perpendicularSpacing(tileW, tileH, angle)), angle, cm(lineWidth(shapes)));
            }
            case "Grid" -> String.format(Locale.US, "Grid[distance=%s,line width=%s]",
                    distance, cm(lineWidth(shapes)));
            default -> tikzPattern;
        };
    }

    /**
     * Distance between neighbouring hatch lines, measured perpendicular to them.
     * TikZ takes that perpendicular gap, the SVG only gives the tile: one diagonal
     * stroke per square tile of side S sits S*sin(angle) from the next, not S, which
     * at 45 degrees is a factor of 1.41.
     */
    private static double perpendicularSpacing(double tileW, double tileH, double angleDeg) {
        double horizontalPeriod = tileW > 0 ? tileW : tileH;
        double verticalPeriod   = tileH > 0 ? tileH : tileW;
        if (Math.abs(angleDeg) < 0.5 || Math.abs(angleDeg - 180) < 0.5) return verticalPeriod;
        if (Math.abs(angleDeg - 90) < 0.5) return horizontalPeriod;
        return horizontalPeriod * Math.abs(Math.sin(Math.toRadians(angleDeg)));
    }

    /** Formats an SVG user-unit length as a TikZ dimension. */
    private static String cm(double px) {
        return String.format(Locale.US, "%.4fcm", Math.max(px, 0.05) * TransformUtils.PX_TO_CM);
    }

    /** Radius of the first round shape in the tile, defaulting to a visible dot. */
    private static double dotRadius(List<Element> shapes) {
        for (Element el : shapes) {
            String tag = localName(el);
            if (tag.equals("circle")) return parseDouble(el.getAttribute("r"), 1);
            if (tag.equals("ellipse")) {
                return (parseDouble(el.getAttribute("rx"), 1) + parseDouble(el.getAttribute("ry"), 1)) / 2;
            }
        }
        return 1;
    }

    /** Stroke width of the first stroked shape in the tile. */
    private static double lineWidth(List<Element> shapes) {
        for (Element el : shapes) {
            double w = parseDouble(strokeWidthOf(el), -1);
            if (w > 0) return w;
            if (localName(el).equals("rect")) {
                return Math.min(parseDouble(el.getAttribute("width"), 1),
                                parseDouble(el.getAttribute("height"), 1));
            }
        }
        return 1;
    }

    /**
     * Direction of the tile's strokes in TikZ degrees, measured counter-clockwise.
     * SVG's y axis points down, so the sign of dy flips on the way out.
     */
    private static double lineAngle(List<Element> shapes) {
        for (Element el : shapes) {
            double[] d = strokeDirection(el);
            if (d == null) continue;
            double deg = Math.toDegrees(Math.atan2(-d[1], d[0]));
            while (deg < 0) deg += 180;      // a line and its reverse are the same hatching
            while (deg >= 180) deg -= 180;
            return deg;
        }
        return 0;
    }

    // -----------------------------------------------------------------------
    // Tile resolution
    // -----------------------------------------------------------------------

    /**
     * A pattern may inherit its content from another via {@code href}, as gradients
     * inherit their stops. Follows one level of indirection when the pattern has no
     * drawable children of its own.
     */
    private Element resolveTile(Element pattern) {
        if (!drawableChildren(pattern).isEmpty()) return pattern;
        String href = pattern.getAttribute("xlink:href");
        if (href.isEmpty()) href = pattern.getAttribute("href");
        if (href.startsWith("#")) {
            Element referenced = ctx.defsMap.get(href.substring(1));
            if (referenced != null && !drawableChildren(referenced).isEmpty()) return referenced;
        }
        return pattern;
    }

    /** Child elements that paint something. */
    private static List<Element> drawableChildren(Element parent) {
        List<Element> out = new ArrayList<>();
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element el)) continue;
            switch (localName(el)) {
                case "rect", "circle", "ellipse", "line", "path", "polyline", "polygon" -> out.add(el);
                case "g" -> out.addAll(drawableChildren(el));
                default -> { /* defs, desc, title and friends paint nothing */ }
            }
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Classification
    // -----------------------------------------------------------------------

    /**
     * Picks the TikZ pattern the tile most resembles, or null when none fits.
     * Only tiles made entirely of round shapes or entirely of straight strokes are
     * classified; a mixed or complex tile falls back to a flat fill rather than a
     * tiling it does not resemble.
     */
    private String classify(List<Element> shapes) {
        boolean allRound = true;
        int horizontal = 0, vertical = 0, upward = 0, downward = 0, other = 0;

        for (Element el : shapes) {
            String tag = localName(el);
            if (tag.equals("circle") || tag.equals("ellipse")) continue;
            allRound = false;

            double[] direction = strokeDirection(el);
            if (direction == null) { other++; continue; }
            double dx = direction[0], dy = direction[1];
            if (Math.abs(dy) <= ORIENTATION_MARGIN)      horizontal++;
            else if (Math.abs(dx) <= ORIENTATION_MARGIN) vertical++;
            // SVG y grows downward, so a negative dy is a line rising to the right.
            else if (dx * dy < 0)                        upward++;
            else                                         downward++;
        }

        if (allRound) return "Dots";
        if (other > 0) return null;

        boolean hasH = horizontal > 0, hasV = vertical > 0;
        boolean hasUp = upward > 0, hasDown = downward > 0;

        // Grid and crosshatch both need two directions; everything else is one family
        // of parallel strokes, whose angle sized() reads off the tile.
        if (hasH && hasV && !hasUp && !hasDown) return "Grid";
        if (hasUp && hasDown && !hasH && !hasV) return "Grid";
        int directions = (hasH ? 1 : 0) + (hasV ? 1 : 0) + (hasUp ? 1 : 0) + (hasDown ? 1 : 0);
        return directions == 1 ? "Lines" : null;
    }

    /**
     * The overall direction of a straight stroke, as {@code {dx, dy}} in SVG user space,
     * or null when the element is not a single straight segment.
     */
    private static double[] strokeDirection(Element el) {
        switch (localName(el)) {
            case "line" -> {
                return new double[]{
                        parseDouble(el.getAttribute("x2"), 0) - parseDouble(el.getAttribute("x1"), 0),
                        parseDouble(el.getAttribute("y2"), 0) - parseDouble(el.getAttribute("y1"), 0)};
            }
            case "rect" -> {
                // A rect far longer than it is wide reads as a rule, not a box.
                double w = parseDouble(el.getAttribute("width"), 0);
                double h = parseDouble(el.getAttribute("height"), 0);
                if (w <= 0 || h <= 0) return null;
                if (w >= 4 * h) return new double[]{w, 0};
                if (h >= 4 * w) return new double[]{0, h};
                return null;
            }
            case "path", "polyline", "polygon" -> {
                double[] pts = endpointsOf(el);
                return pts == null ? null : new double[]{pts[2] - pts[0], pts[3] - pts[1]};
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * First and last point of a straight two-point path or polyline, as
     * {@code {x1, y1, x2, y2}}; null if the element bends or is not straight-line data.
     */
    private static double[] endpointsOf(Element el) {
        String data = localName(el).equals("path")
                ? el.getAttribute("d")
                : el.getAttribute("points");
        if (data.isEmpty()) return null;
        // Curves and arcs are not straight strokes.
        if (localName(el).equals("path") && data.matches("(?s).*[CcSsQqTtAa].*")) return null;

        double[] n = parseNumbers(data);
        if (n.length < 4 || n.length % 2 != 0) return null;
        // More than two points is only a straight stroke if they are collinear.
        for (int i = 4; i + 1 < n.length; i += 2) {
            double cross = (n[2] - n[0]) * (n[i + 1] - n[1]) - (n[3] - n[1]) * (n[i] - n[0]);
            if (Math.abs(cross) > 1e-6) return null;
        }
        return new double[]{n[0], n[1], n[n.length - 2], n[n.length - 1]};
    }

    // -----------------------------------------------------------------------
    // Flat-fill fallback
    // -----------------------------------------------------------------------

    /** The tile's most prominent paint colour: the first concrete fill, else stroke. */
    private String dominantHex(List<Element> shapes) {
        for (Element el : shapes) {
            String fill = ParseUtils.getStyleOrAttr(el, "fill");
            if (isConcreteColor(fill)) return ctx.colors.resolveColorHex(fill);
        }
        for (Element el : shapes) {
            String stroke = ParseUtils.getStyleOrAttr(el, "stroke");
            if (isConcreteColor(stroke)) return ctx.colors.resolveColorHex(stroke);
        }
        return "#000000";
    }

    private static boolean isConcreteColor(String v) {
        return v != null && !v.isBlank()
                && !v.equals("none") && !v.equals("transparent") && !v.startsWith("url(");
    }

    /**
     * Rough fraction of the tile the pattern's shapes cover, used to lighten the
     * flat-fill fallback. Areas are summed without accounting for overlap, so this is
     * an upper bound, and the result is clamped to [0.1, 0.9].
     */
    private double estimateCoverage(Element pattern, Element tile, List<Element> shapes) {
        double tileW = parseDouble(pattern.getAttribute("width"), 0);
        double tileH = parseDouble(pattern.getAttribute("height"), 0);
        if (tileW <= 0 || tileH <= 0) {
            double[] vb = parseNumbers(tile.getAttribute("viewBox"));
            if (vb.length >= 4) { tileW = vb[2]; tileH = vb[3]; }
        }
        if (tileW <= 0 || tileH <= 0) return 0.5;

        double painted = 0;
        for (Element el : shapes) {
            painted += approximateArea(el);
        }
        double coverage = painted / (tileW * tileH);
        return Math.max(0.1, Math.min(0.9, coverage));
    }

    /** Area of a shape, approximated by its bounding box for anything but a circle. */
    private static double approximateArea(Element el) {
        switch (localName(el)) {
            case "rect" -> {
                return parseDouble(el.getAttribute("width"), 0)
                     * parseDouble(el.getAttribute("height"), 0);
            }
            case "circle" -> {
                double r = parseDouble(el.getAttribute("r"), 0);
                return Math.PI * r * r;
            }
            case "ellipse" -> {
                return Math.PI * parseDouble(el.getAttribute("rx"), 0)
                                * parseDouble(el.getAttribute("ry"), 0);
            }
            default -> {
                // A stroke's area is its length times its width.
                double[] pts = endpointsOf(el);
                double width = parseDouble(strokeWidthOf(el), 1);
                if (pts == null) return 0;
                double dx = pts[2] - pts[0], dy = pts[3] - pts[1];
                return Math.hypot(dx, dy) * width;
            }
        }
    }

    private static String strokeWidthOf(Element el) {
        String w = ParseUtils.getStyleOrAttr(el, "stroke-width");
        return w == null ? "1" : w;
    }

    private static String localName(Node node) {
        return node.getNodeName().replaceFirst(".*:", "").toLowerCase(Locale.ROOT);
    }
}
