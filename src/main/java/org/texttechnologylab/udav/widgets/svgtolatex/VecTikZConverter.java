package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Converts an SVG string to a standalone LaTeX/TikZ document.
 * <p>
 * The work is delegated to specialised components:
 * <ul>
 *   <li>{@link ColorManager}: colour registration and resolution</li>
 *   <li>{@link GradientHandler}: gradient stop collection and TikZ shadings</li>
 *   <li>{@link PatternHandler}: patterns to TikZ tilings</li>
 *   <li>{@link MarkerHandler}: SVG markers to TikZ arrow heads</li>
 *   <li>{@link PathBuilder}: SVG path {@code d} data to TikZ path strings</li>
 *   <li>{@link StrokeHelper}: dash patterns, line caps/joins</li>
 *   <li>{@link StyleSheet}: CSS from {@code <style>} elements</li>
 *   <li>{@link TextRenderer}: text, tspan and foreignObject to {@code \node} commands</li>
 *   <li>{@link ElementProcessor}: dispatches each SVG element to the above</li>
 * </ul>
 *
 * Supported SVG elements:
 *   {@code <svg>}, {@code <g>}, {@code <a>}, {@code <rect>}, {@code <circle>},
 *   {@code <ellipse>}, {@code <line>}, {@code <polyline>}, {@code <polygon>},
 *   {@code <path>}, {@code <text>}, {@code <tspan>}, {@code <foreignObject>},
 *   {@code <use>}, {@code <symbol>}, {@code <switch>}, {@code <image>}, plus
 *   {@code <clipPath>}, {@code <mask>}, gradients, patterns and markers referenced
 *   from them.
 *
 * Coordinate conversion:
 *   SVG px to TikZ cm (1 px = 2.54/96 cm), y-axis flipped.
 */
public class VecTikZConverter {

    private final ConversionContext ctx = new ConversionContext();

    /**
     * CSS default width for a replaced element with no intrinsic size, used when the
     * root {@code <svg>} gives neither an absolute width nor a viewBox to measure.
     */
    private static final double DEFAULT_VIEWPORT_WIDTH = 300;

    /** CSS default height for a replaced element with no intrinsic size. */
    private static final double DEFAULT_VIEWPORT_HEIGHT = 150;

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
        return convert(svgString, null);
    }

    /**
     * As {@link #convert(String)}, but able to resolve {@code <image>} elements that
     * reference a file next to the document. After a successful conversion,
     * {@link #getAssets()} holds any raster the generated document needs beside it.
     * Remote URLs are never fetched.
     *
     * @param baseDir directory the SVG came from, or null to skip external references
     */
    public String convert(String svgString, Path baseDir) throws Exception {
        ctx.clear();
        ctx.baseDir = baseDir;

        // Parse the SVG XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(svgString.getBytes("UTF-8")));

        Element root = doc.getDocumentElement();
        ctx.rootElement = root;

        // Establish the viewport and the user-space to viewport mapping.
        double[] rootCtm = setUpViewport(root);

        // Pass 0: fold any <style> rules into each element's own style attribute, so
        // every later pass resolves styling through getStyleOrAttr and gets the CSS
        // cascade. Must run before anything reads a style.
        StyleSheet.collect(root).applyTo(root);

        // Pass 1: collect definitions
        collectClipPaths(root);
        collectDefs(root);

        GradientHandler gradients = new GradientHandler(ctx);
        gradients.collectGradientStops();

        MarkerHandler markers = new MarkerHandler(ctx);
        markers.collectMarkers();

        ctx.colors.collectColors(root);

        // Pass 2: emit TikZ commands
        StrokeHelper  strokes  = new StrokeHelper(ctx, markers);
        PathBuilder   paths    = new PathBuilder(ctx);
        TextRenderer  text     = new TextRenderer(ctx);
        ElementProcessor processor = new ElementProcessor(ctx, gradients, strokes, paths, text);

        processor.processNode(root, rootCtm, new InheritedAttrs());

        return buildDocument();
    }

    /**
     * Files the generated document references, keyed by the file name used in the
     * {@code .tex}: the payload of every {@code <image>} element. They must be
     * written beside the document for it to compile. No UDAV widget draws an
     * {@code <image>}, which is why the export never asks for these; a document that
     * does carry one needs a caller that writes them out.
     */
    public Map<String, byte[]> getAssets() {
        return Collections.unmodifiableMap(ctx.assets);
    }

    // -----------------------------------------------------------------------
    // Viewport
    // -----------------------------------------------------------------------

    /**
     * Works out the drawing surface and the transform from user space onto it,
     * writing the surface size into {@link ConversionContext#svgWidth} /
     * {@link ConversionContext#svgHeight svgHeight} and returning the root CTM.
     * <p>
     * A percentage width or height has no container to resolve against in a
     * standalone document, so the viewBox gives the natural size, as browsers and
     * Batik do. A physical unit is converted at 96 px per inch. The viewBox is fitted
     * with the default {@code preserveAspectRatio} of {@code xMidYMid meet}: uniform
     * scale, then centred. Explicit {@code preserveAspectRatio} values ({@code slice},
     * {@code none}, other alignments) are not honoured here.
     *
     * @return the root CTM as {@code [a, b, c, d, e, f]}
     */
    private double[] setUpViewport(Element root) {
        ParseUtils.Length widthAttr  = parseLength(root.getAttribute("width"));
        ParseUtils.Length heightAttr = parseLength(root.getAttribute("height"));

        double[] vb = parseNumbers(root.getAttribute("viewBox"));
        boolean hasViewBox = vb.length >= 4 && vb[2] > 0 && vb[3] > 0;

        // A usable width/height is one that resolves to pixels by itself and is positive.
        boolean fixedW = widthAttr.isAbsolute()  && widthAttr.px()  > 0;
        boolean fixedH = heightAttr.isAbsolute() && heightAttr.px() > 0;

        double viewportW, viewportH;
        if (hasViewBox) {
            viewportW = fixedW ? widthAttr.px()  : vb[2];
            viewportH = fixedH ? heightAttr.px() : vb[3];
            // Only one axis pinned: the other follows the viewBox's aspect ratio.
            if (fixedW && !fixedH) viewportH = viewportW * vb[3] / vb[2];
            if (!fixedW && fixedH) viewportW = viewportH * vb[2] / vb[3];
        } else {
            // No viewBox: user units are already px. A percentage or missing size has
            // nothing to resolve against and no intrinsic size to fall back on, so use
            // the CSS default for a replaced element (300x150) rather than 0x0, which
            // would clip the whole document away.
            // TODO: measuring the content's bounding box would beat a fixed guess here.
            viewportW = fixedW ? widthAttr.px()  : DEFAULT_VIEWPORT_WIDTH;
            viewportH = fixedH ? heightAttr.px() : DEFAULT_VIEWPORT_HEIGHT;
        }

        ctx.svgWidth  = viewportW;
        ctx.svgHeight = viewportH;

        if (!hasViewBox) return TransformUtils.identityMtx();

        // preserveAspectRatio="xMidYMid meet": one uniform scale, then centre.
        double scale = Math.min(viewportW / vb[2], viewportH / vb[3]);
        double tx = -vb[0] * scale + (viewportW - vb[2] * scale) / 2;
        double ty = -vb[1] * scale + (viewportH - vb[3] * scale) / 2;
        return new double[]{scale, 0, 0, scale, tx, ty};
    }

    // -----------------------------------------------------------------------
    // Pass 1a: clipPath collection
    // -----------------------------------------------------------------------

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
                            ctx.clipRects.put(id, new double[]{x, y, w, h});
                            break;
                        }
                    }
                }
            }
        }
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) collectClipPaths(kids.item(i));
    }

    // -----------------------------------------------------------------------
    // Pass 1b: id index
    // -----------------------------------------------------------------------

    /**
     * Indexes every element that carries an {@code id}, anywhere in the document.
     * References resolve by id, not by location, and a gradient, marker or symbol may
     * sit outside {@code <defs>}. Being indexed does not make an element render;
     * {@code <defs>} subtrees are still skipped by {@link ElementProcessor}.
     */
    private void collectDefs(Node node) {
        if (!(node instanceof Element el)) return;
        String id = el.getAttribute("id");
        // First definition wins, matching the document-order rule for duplicate ids.
        if (!id.isEmpty()) ctx.defsMap.putIfAbsent(id, el);

        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) collectDefs(kids.item(i));
    }

    // -----------------------------------------------------------------------
    // LaTeX document assembly
    // -----------------------------------------------------------------------

    private String buildDocument() {
        StringBuilder sb = new StringBuilder();
        sb.append("\\documentclass{standalone}\n");
        sb.append("\\usepackage[utf8]{inputenc}\n");
        // Latin Modern must come before fontenc. With the default Computer Modern, T1
        // selects the EC bitmap fonts, and a size that has not been pre-generated is
        // sent to Metafont, which fails on the sizes typical of chart labels ("Font
        // ecss0500 at 226 not found"). Latin Modern is a scalable Type 1 superset of
        // Computer Modern, so any size works.
        sb.append("\\usepackage{lmodern}\n");
        sb.append("\\usepackage[T1]{fontenc}\n");
        sb.append("\\usepackage{textcomp}\n");
        sb.append("\\usepackage{anyfontsize}\n");
        sb.append("\\usepackage{tikz}\n");
        sb.append("\\usetikzlibrary{shadings}\n");
        sb.append("\\usetikzlibrary{arrows.meta}\n");
        if (ctx.usesPatternLibrary) sb.append("\\usetikzlibrary{patterns.meta}\n");
        if (!ctx.assets.isEmpty()) sb.append("\\usepackage{graphicx}\n");
        sb.append("\\usepackage[outline]{contour}\n");
        for (String pkg : ctx.pendingPackages)
            sb.append("\\usepackage{").append(pkg).append("}\n");

        for (Map.Entry<String, String> e : ctx.colors.getColorDefs().entrySet()) {
            String name = e.getValue();
            String hex  = e.getKey().replace("#", "");
            if (hex.length() < 6) continue;
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            sb.append(String.format("\\definecolor{%s}{RGB}{%d,%d,%d}\n", name, r, g, b));
        }

        for (String decl : ctx.pendingShadings.values()) {
            sb.append(decl);
        }
        for (String decl : ctx.pendingPatterns.values()) {
            sb.append(decl);
        }

        sb.append("\\begin{document}\n");
        sb.append("\\noindent%\n");
        sb.append("\\begin{tikzpicture}[x=1cm, y=1cm]\n");
        double wCm = ctx.svgWidth  * PX_TO_CM;
        double hCm = ctx.svgHeight * PX_TO_CM;
        sb.append(String.format(Locale.US,
                "\\useasboundingbox (0, 0) rectangle (%.4f, %.4f);\n", wCm, hCm));
        sb.append("\\begin{pgfinterruptboundingbox}\n");
        sb.append(String.format(Locale.US,
                "\\clip (0, 0) rectangle (%.4f, %.4f);\n", wCm, hCm));
        sb.append(ctx.body);
        sb.append("\\end{pgfinterruptboundingbox}\n");
        sb.append("\\end{tikzpicture}\n");
        sb.append("\\end{document}\n");
        return sb.toString();
    }
}
