package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Converts an SVG string to a standalone LaTeX/TikZ document.
 *
 * <p>This class orchestrates the conversion by delegating to specialised
 * components:
 * <ul>
 *   <li>{@link ColorManager}      — colour registration and resolution</li>
 *   <li>{@link GradientHandler}   — gradient stop collection and TikZ shading</li>
 *   <li>{@link MarkerHandler}     — SVG markers → TikZ arrow heads</li>
 *   <li>{@link PathBuilder}       — SVG path {@code d} → TikZ path strings</li>
 *   <li>{@link StrokeHelper}      — dash patterns, line caps/joins</li>
 *   <li>{@link TextRenderer}      — text/tspan → {@code \node} commands</li>
 *   <li>{@link ElementProcessor}  — dispatches each SVG element to the above</li>
 * </ul>
 *
 * Supported SVG elements:
 *   {@code <svg>}, {@code <g>}, {@code <rect>}, {@code <circle>},
 *   {@code <ellipse>}, {@code <path>}, {@code <line>}, {@code <text>},
 *   {@code <tspan>}, {@code <use>}, {@code <defs>}, {@code <clipPath>}
 *
 * Coordinate conversion:
 *   SVG px → TikZ cm (1 px = 2.54/96 cm), y-axis flipped.
 */
public class SvgToLaTeXConverter {

    private final ConversionContext ctx = new ConversionContext();

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
        ctx.clear();

        // Parse the SVG XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(svgString.getBytes("UTF-8")));

        Element root = doc.getDocumentElement();
        ctx.rootElement = root;

        // Determine the coordinate space (viewBox vs width/height)
        double displayW = parseDouble(root.getAttribute("width"),  0);
        double displayH = parseDouble(root.getAttribute("height"), 0);

        double rootScale = 1.0;
        double rootTx = 0, rootTy = 0;
        String vbAttr = root.getAttribute("viewBox");
        if (!vbAttr.isEmpty()) {
            double[] vb = parseNumbers(vbAttr);
            if (vb.length >= 4 && vb[2] > 0 && vb[3] > 0) {
                ctx.svgWidth  = (displayW > 0) ? displayW : vb[2];
                ctx.svgHeight = vb[3];
                if (displayW > 0) rootScale = displayW / vb[2];
                else if (displayH > 0) rootScale = displayH / vb[3];
                rootTx = -vb[0] * rootScale;
                rootTy = -vb[1] * rootScale;
            } else {
                ctx.svgWidth  = displayW;
                ctx.svgHeight = displayH;
            }
        } else {
            ctx.svgWidth  = displayW;
            ctx.svgHeight = displayH;
        }

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

        double[] rootCtm = new double[]{rootScale, 0, 0, rootScale, rootTx, rootTy};
        processor.processNode(root, rootCtm, new InheritedAttrs());

        return buildDocument();
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
    // Pass 1b: <defs> collection
    // -----------------------------------------------------------------------

    private void collectDefs(Node node) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
        if ("defs".equals(tag)) {
            NodeList kids = el.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++) {
                if (!(kids.item(i) instanceof Element)) continue;
                Element child = (Element) kids.item(i);
                String id = child.getAttribute("id");
                if (!id.isEmpty()) ctx.defsMap.put(id, child);
            }
            return;
        }
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
        sb.append("\\usepackage[T1]{fontenc}\n");
        sb.append("\\usepackage{textcomp}\n");
        sb.append("\\usepackage{anyfontsize}\n");
        sb.append("\\usepackage{tikz}\n");
        sb.append("\\usetikzlibrary{shadings}\n");
        sb.append("\\usetikzlibrary{arrows.meta}\n");
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