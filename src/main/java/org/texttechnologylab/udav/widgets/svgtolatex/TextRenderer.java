package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import java.util.*;
import java.util.regex.*;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Processes SVG {@code <text>}, {@code <tspan>} and {@code <foreignObject>}
 * elements, emitting TikZ {@code \node} commands with the correct font, colour,
 * anchor, and rotation.
 */
public class TextRenderer {

    private final ConversionContext ctx;

    public TextRenderer(ConversionContext ctx) {
        this.ctx = ctx;
    }

    // -----------------------------------------------------------------------
    // <text>
    // -----------------------------------------------------------------------

    public void processText(Element el, double[] ctm, InheritedAttrs inh) {
        String transformAttr = el.getAttribute("transform");
        double[] textCtm = transformAttr.isEmpty() ? ctm
                : composeMtx(ctm, parseTransformMtx(transformAttr));

        double rotate = rotationDeg(textCtm);
        double scale  = scaleApprox(textCtm);

        InheritedAttrs textInh = inh.copy();
        applyTextAttrs(el, textInh);

        // The cursor starts at the element's own position and is carried through every
        // descendant chunk, so a <tspan> without coordinates continues the line.
        TextCursor cursor = new TextCursor();
        cursor.x = parseDouble(el.getAttribute("x"), 0) + parseLength(el, "dx", textInh.fontSize);
        cursor.y = parseDouble(el.getAttribute("y"), 0) + parseLength(el, "dy", textInh.fontSize);

        layoutChildren(el, textCtm, rotate, scale, textInh, cursor, true);
    }

    /**
     * Entry point for a {@code <tspan>} reached on its own rather than through its
     * {@code <text>} parent. It has no preceding chunk, so its own position is the start.
     */
    public void processTspan(Element el, double[] ctm, InheritedAttrs inh) {
        double scale  = scaleApprox(ctm);
        double rotate = rotationDeg(ctm);
        InheritedAttrs tspanInh = inh.copy();
        applyTextAttrs(el, tspanInh);

        TextCursor cursor = new TextCursor();
        cursor.x = parseDouble(el.getAttribute("x"), 0);
        cursor.y = parseDouble(el.getAttribute("y"), 0);
        layoutSpan(el, ctm, rotate, scale, tspanInh, cursor, true);
    }

    /**
     * Lays out the children of a text container: bare text nodes become chunks at the
     * cursor, nested spans recurse and keep moving the same cursor.
     *
     * @param anchorHere whether a chunk starting here may apply {@code text-anchor};
     *                   only the first chunk of an absolutely-positioned run may
     */
    private void layoutChildren(Element container, double[] ctm, double rotate, double scale,
                                InheritedAttrs inh, TextCursor cursor, boolean anchorHere) {
        NodeList kids = container.getChildNodes();
        boolean first = anchorHere;
        for (int i = 0; i < kids.getLength(); i++) {
            Node kid = kids.item(i);
            if (kid instanceof Text textNode) {
                String content = collapseWhitespace(textNode.getData());
                if (content.isEmpty()) continue;
                emitChunk(container, content, ctm, rotate, scale, inh, cursor, first);
                first = false;
                continue;
            }
            if (!(kid instanceof Element child)) continue;
            String tag = child.getTagName().replaceFirst(".*:", "").toLowerCase(Locale.ROOT);
            if (tag.equals("tspan") || tag.equals("a")) {
                InheritedAttrs childInh = inh.copy();
                applyTextAttrs(child, childInh);
                layoutSpan(child, ctm, rotate, scale, childInh, cursor, false);
                first = false;
            }
        }
    }

    /** Applies a span's own positioning to the cursor, then lays out its content. */
    private void layoutSpan(Element el, double[] ctm, double rotate, double scale,
                            InheritedAttrs inh, TextCursor cursor, boolean anchorHere) {
        boolean absolute = anchorHere;
        if (!el.getAttribute("x").isEmpty()) {
            cursor.x = parseDouble(el.getAttribute("x"), cursor.x);
            absolute = true;
        }
        if (!el.getAttribute("y").isEmpty()) {
            cursor.y = parseDouble(el.getAttribute("y"), cursor.y);
            absolute = true;
        }
        // dx/dy shift the cursor relative to wherever it already is, and accumulate.
        cursor.x += parseLength(el, "dx", inh.fontSize);
        cursor.y += parseLength(el, "dy", inh.fontSize);

        if (hasElementChild(el)) {
            layoutChildren(el, ctm, rotate, scale, inh, cursor, absolute);
            return;
        }
        String content = collapseWhitespace(el.getTextContent());
        if (content.isEmpty()) return;
        emitChunk(el, content, ctm, rotate, scale, inh, cursor, absolute);
    }

    /** Draws one run at the cursor and advances the cursor past it. */
    private void emitChunk(Element styleSource, String content, double[] ctm,
                           double rotate, double scale, InheritedAttrs inh,
                           TextCursor cursor, boolean applyAnchor) {
        double[] wp = applyMtxAbs(ctm, cursor.x, cursor.y);
        emitTextNode(styleSource, content, wp[0], wp[1], scale, rotate, cursor.y, inh, applyAnchor);
        cursor.x += measureAdvance(content, inh);
    }

    private static boolean hasElementChild(Element el) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element) return true;
        }
        return false;
    }

    /** HTML/SVG whitespace collapsing: runs of space become one, ends are trimmed. */
    private static String collapseWhitespace(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").strip();
    }

    /** Reads {@code dx}/{@code dy}, which may be given in {@code em}. */
    private static double parseLength(Element el, String attribute, double fontSize) {
        String value = el.getAttribute(attribute).trim();
        if (value.isEmpty()) return 0;
        return value.endsWith("em")
                ? parseDouble(value.substring(0, value.length() - 2).trim(), 0) * fontSize
                : parseDouble(value, 0);
    }


    // -----------------------------------------------------------------------
    // Text measurement
    // -----------------------------------------------------------------------

    /**
     * How far the text position advances after drawing {@code text}, in SVG user units.
     * <p>
     * A {@code <tspan>} that carries no absolute position starts where the previous
     * one ended, which needs font metrics. They come from {@link FontMetrics}, a table
     * frozen from AWT, the machinery Batik lays text out with. The generated LaTeX is
     * typeset in Latin Modern and does not match those widths exactly, but the author
     * positioned the text against the metrics of the font they named, so the intended
     * positions are reproduced rather than reflowed to Latin Modern's widths.
     */
    public static double measureAdvance(String text, InheritedAttrs inh) {
        if (text == null || text.isEmpty()) return 0;
        boolean bold = isFontWeightBold(inh.fontWeight);
        String family = FontMetrics.resolveFamily(inh.fontFamily);
        return FontMetrics.advanceAtMeasureSize(text, family, bold)
                * inh.fontSize / FontMetrics.MEASURE_SIZE;
    }

    // -----------------------------------------------------------------------
    // Text layout
    // -----------------------------------------------------------------------

    /**
     * The current text position while laying out one {@code <text>} element.
     * SVG text is a sequence of chunks sharing one moving cursor: a chunk with an
     * absolute {@code x}/{@code y} moves it, a chunk without one continues from where
     * the last glyph ended.
     */
    private static final class TextCursor {
        double x, y;
    }

    // -----------------------------------------------------------------------
    // Shared text-node emission
    // -----------------------------------------------------------------------

    private void emitTextNode(Element el, String content, double finalX, double finalY,
                              double scale, double rotate, double rawY,
                              InheritedAttrs inh, boolean applyAnchor) {
        ColorManager colors = ctx.colors;
        String fill = colors.resolveFill(el, inh);
        if ("none".equals(fill)) {
            colors.registerHex(ColorManager.CURRENT_COLOR_HEX);
            fill = colors.colorName(ColorManager.CURRENT_COLOR_HEX);
        }

        String textAnchor = el.getAttribute("text-anchor");
        if (textAnchor.isEmpty()) {
            Matcher m = Pattern.compile("text-anchor\\s*:\\s*(\\w+)").matcher(el.getAttribute("style"));
            if (m.find()) textAnchor = m.group(1);
        }
        if (textAnchor.isEmpty()) textAnchor = inh.textAnchor;
        // A chunk that merely continues the previous one is placed at the pen position,
        // so it always starts there; only a run beginning at an absolute coordinate
        // gets to shift itself by its text-anchor.
        if (!applyAnchor) textAnchor = "start";

        String texMode = inh.texMode;
        {
            String tm = el.getAttribute("data-texmode").trim();
            if (tm.isEmpty()) tm = el.getAttribute("texmode").trim();
            if (!tm.isEmpty()) texMode = tm.toLowerCase();
        }

        double fontSize = inh.fontSize;
        String tikzAnchor = svgAnchorToTikz(textAnchor, rotate, rawY);

        if ("north".equals(tikzAnchor)) {
            finalY -= 0.70 * fontSize * scale;
        } else if ("south".equals(tikzAnchor)) {
            finalY += 0.20 * fontSize * scale;
        }

        String rawTextContent = content;
        if ("raw".equals(texMode)) {
            content = rawTextContent;
        } else if ("math".equals(texMode)) {
            content = "$" + rawTextContent + "$";
        } else {
            content = escapeTex(rawTextContent);
        }

        boolean isBold = isFontWeightBold(inh.fontWeight);
        double explicitSW = parseDouble(el.getAttribute("stroke-width"), -1);
        if (explicitSW < 0) explicitSW = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);
        boolean strokeAsBold = (explicitSW >= 1.5);
        String strokeColor = colors.resolveStroke(el, inh);
        boolean hasRealStroke = !strokeColor.equals("none") && explicitSW > 0;

        if (hasRealStroke && !strokeColor.equals(fill) && texMode.isEmpty()) {
            content = String.format("\\contour{%s}{%s}", strokeColor, content);
        }

        List<String> opts = new ArrayList<>();
        opts.add("inner sep=0pt");
        opts.add("text=" + fill);
        opts.add("anchor=" + tikzAnchor);
        if (Math.abs(rotate) > 0.1)
            opts.add(String.format(Locale.US, "rotate=%.1f", -rotate));

        String familyCmd = resolveFontFamily(inh.fontFamily);
        double fontSizePt = fontSize * scale * 0.75;
        String weightCmd = isBold ? "\\bfseries" : "";

        opts.add(String.format(Locale.US,
                "font=\\fontsize{%.2fpt}{%.2fpt}\\selectfont%s%s",
                fontSizePt, fontSizePt * 1.2, familyCmd, weightCmd));

        if ((hasRealStroke || strokeAsBold) && texMode.isEmpty()) {
            double swPt = explicitSW * scale * 0.75 * 0.5;
            ctx.body.append(String.format(Locale.US, "\\contourlength{%.3fpt}\n", swPt));
        }

        ctx.body.append(String.format(Locale.US,
                "\\node[%s] at (%.4f, %.4f) {%s};\n",
                String.join(", ", opts), ctx.toX(finalX), ctx.toY(finalY), content));
    }

    // -----------------------------------------------------------------------
    // <foreignObject>
    // -----------------------------------------------------------------------

    /**
     * Renders the text inside a {@code <foreignObject>} as a centred TikZ node.
     * <p>
     * Mermaid, draw.io and other tools that reuse HTML text layout put their labels
     * in a {@code <foreignObject>} holding XHTML rather than in {@code <text>}. This
     * is not an HTML layout engine: the text is split at block-level boundaries and
     * centred in the box the foreignObject declares. Anything relying on CSS layout
     * (floats, tables, images) comes out as its text content, in the right place,
     * unstyled.
     */
    public void processForeignObject(Element el, double[] ctm, InheritedAttrs inh) {
        List<String> lines = extractLines(el);
        if (lines.isEmpty()) return;

        double x = parseDouble(el.getAttribute("x"), 0);
        double y = parseDouble(el.getAttribute("y"), 0);
        double w = parseDouble(el.getAttribute("width"), 0);
        double h = parseDouble(el.getAttribute("height"), 0);

        double[] elemMtx = parseTransformMtx(el.getAttribute("transform"));
        double[] boxCtm  = composeMtx(ctm, elemMtx);
        double[] centre  = applyMtxAbs(boxCtm, x + w / 2, y + h / 2);
        double scale     = scaleApprox(boxCtm);

        // Styling comes from the deepest styled descendant: StyleSheet has already
        // flattened the document's CSS onto the XHTML elements, so .nodeLabel and
        // friends resolve here as they would on an SVG element.
        InheritedAttrs effective = inh.copy();
        applyInheritedFrom(el, effective);

        ColorManager colors = ctx.colors;
        String fill = colors.resolveFill(el, effective);
        if ("none".equals(fill)) {
            colors.registerHex(ColorManager.CURRENT_COLOR_HEX);
            fill = colors.colorName(ColorManager.CURRENT_COLOR_HEX);
        }

        String content = lines.stream().map(TextRenderer::escapeTex)
                .collect(java.util.stream.Collectors.joining(" \\\\ "));

        double fontSizePt = effective.fontSize * scale * 0.75;
        List<String> opts = new ArrayList<>();
        opts.add("inner sep=0pt");
        opts.add("text=" + fill);
        opts.add("anchor=center");
        if (lines.size() > 1) opts.add("align=center");
        opts.add(String.format(Locale.US,
                "font=\\fontsize{%.2fpt}{%.2fpt}\\selectfont%s%s",
                fontSizePt, fontSizePt * 1.2,
                resolveFontFamily(effective.fontFamily),
                isFontWeightBold(effective.fontWeight) ? "\\bfseries" : ""));

        ctx.body.append(String.format(Locale.US,
                "\\node[%s] at (%.4f, %.4f) {%s};%n",
                String.join(", ", opts), ctx.toX(centre[0]), ctx.toY(centre[1]), content));
    }

    /**
     * Text of an XHTML subtree, one entry per block-level box.
     * Inline elements ({@code span}, {@code b}, {@code a}, ...) keep their text on the
     * current line; {@code p}, {@code div}, {@code li} and {@code br} start a new one.
     * Whitespace is collapsed the way HTML collapses it.
     */
    private static List<String> extractLines(Element root) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        collectLines(root, lines, current);
        flushLine(lines, current);
        return lines;
    }

    private static final Set<String> BLOCK_LEVEL = Set.of(
            "p", "div", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre");

    private static void collectLines(Node node, List<String> lines, StringBuilder current) {
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node kid = kids.item(i);
            if (kid instanceof Text text) {
                String value = text.getData().replaceAll("\\s+", " ");
                if (!value.isBlank() || current.length() > 0) current.append(value);
                continue;
            }
            if (!(kid instanceof Element child)) continue;
            String tag = child.getTagName().replaceFirst(".*:", "").toLowerCase(Locale.ROOT);
            if (tag.equals("br")) { flushLine(lines, current); continue; }
            boolean block = BLOCK_LEVEL.contains(tag);
            if (block) flushLine(lines, current);
            collectLines(child, lines, current);
            if (block) flushLine(lines, current);
        }
    }

    private static void flushLine(List<String> lines, StringBuilder current) {
        String line = current.toString().strip();
        if (!line.isEmpty()) lines.add(line);
        current.setLength(0);
    }

    // -----------------------------------------------------------------------
    // Attribute helpers
    // -----------------------------------------------------------------------

    /**
     * Apply text-specific presentation attributes from an element onto
     * an InheritedAttrs copy.
     */
    private void applyInheritedFrom(Element el, InheritedAttrs inh) {
        applyTextAttrs(el, inh);
    }

    private void applyTextAttrs(Element el, InheritedAttrs inh) {
        // getStyleOrAttr throughout: a style declaration outranks a presentation
        // attribute, and StyleSheet has already folded the document's CSS into it.
        String fill       = getStyleOrAttr(el, "fill");
        String stroke     = getStyleOrAttr(el, "stroke");
        String textAnchor = getStyleOrAttr(el, "text-anchor");
        String fontSize   = getStyleOrAttr(el, "font-size");
        String fontFamily = getStyleOrAttr(el, "font-family");
        String fontWeight = getStyleOrAttr(el, "font-weight");

        if (fill       != null) inh.fill       = fill;
        if (stroke     != null) inh.stroke     = stroke;
        if (textAnchor != null) inh.textAnchor = textAnchor;
        if (fontSize   != null) inh.fontSize   = resolveFontSize(fontSize, inh.fontSize);
        if (fontFamily != null) inh.fontFamily = fontFamily.trim();
        if (fontWeight != null) inh.fontWeight = fontWeight.trim();
    }

    private double parseDy(Element el, double fontSize) {
        String dyAttr = el.getAttribute("dy").trim();
        if (dyAttr.isEmpty()) return 0;
        return dyAttr.endsWith("em")
                ? parseDouble(dyAttr.replace("em","").trim(), 0) * fontSize
                : parseDouble(dyAttr, 0);
    }

    // -----------------------------------------------------------------------
    // Anchor mapping
    // -----------------------------------------------------------------------

    private String svgAnchorToTikz(String anchor, double rotate, double rawY) {
        if (Math.abs(rotate) == 45 && "end".equals(anchor)) return "base east";
        switch (anchor) {
            case "middle": return (rawY < 0) ? "south" : "north";
            case "end":    return "base east";
            case "start":  return "base west";
            default:       return "north";
        }
    }

    // -----------------------------------------------------------------------
    // Font-family resolution
    // -----------------------------------------------------------------------

    private static final Map<String, String[]> FONT_FAMILY_MAP;
    static {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("dejavu sans mono",  new String[]{"\\fontfamily{DejaVuSansMono-TLF}\\selectfont", "dejavu"});
        m.put("dejavu sans",       new String[]{"\\fontfamily{DejaVuSans-TLF}\\selectfont",     "dejavu"});
        m.put("dejavu serif",      new String[]{"\\fontfamily{DejaVuSerif-TLF}\\selectfont",    "dejavu"});
        m.put("dejavu",            new String[]{"\\fontfamily{DejaVuSans-TLF}\\selectfont",     "dejavu"});
        m.put("helvetica",         new String[]{"\\fontfamily{phv}\\selectfont",               "helvet"});
        m.put("arial",             new String[]{"\\fontfamily{phv}\\selectfont",               "helvet"});
        m.put("times new roman",   new String[]{"\\fontfamily{ptm}\\selectfont",               "mathptmx"});
        m.put("times",             new String[]{"\\fontfamily{ptm}\\selectfont",               "mathptmx"});
        m.put("georgia",           new String[]{"\\rmfamily",                                  ""});
        m.put("verdana",           new String[]{"\\fontfamily{phv}\\selectfont",               "helvet"});
        m.put("trebuchet",         new String[]{"\\sffamily",                                  ""});
        m.put("courier new",       new String[]{"\\fontfamily{pcr}\\selectfont",               ""});
        m.put("courier",           new String[]{"\\ttfamily",                                  ""});
        m.put("sans-serif",        new String[]{"\\sffamily",                                  ""});
        m.put("sans serif",        new String[]{"\\sffamily",                                  ""});
        m.put("serif",             new String[]{"\\rmfamily",                                  ""});
        m.put("monospace",         new String[]{"\\ttfamily",                                  ""});
        FONT_FAMILY_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Maps an SVG font-family list onto the LaTeX font-selection command.
     * <p>
     * This resolves the list differently from {@link FontMetrics#resolveFamily}:
     * that one answers which font the metrics were measured from, this one which
     * font LaTeX can set. A font missing from the metrics table may still have a
     * LaTeX family, and the reverse, so the two do not share a resolver.
     */
    private String resolveFontFamily(String svgFamily) {
        if (svgFamily == null || svgFamily.isEmpty()) return "\\sffamily";
        for (String candidate : svgFamily.split(",")) {
            String key = candidate.trim().toLowerCase()
                    .replaceAll("^['\"]|['\"]$", "");
            String[] entry = FONT_FAMILY_MAP.get(key);
            if (entry == null) {
                for (Map.Entry<String, String[]> e : FONT_FAMILY_MAP.entrySet()) {
                    if (key.contains(e.getKey()) || e.getKey().contains(key)) {
                        entry = e.getValue(); break;
                    }
                }
            }
            if (entry != null) {
                if (!entry[1].isEmpty()) ctx.pendingPackages.add(entry[1]);
                return entry[0];
            }
            if (key.contains("mono") || key.contains("typewriter")) return "\\ttfamily";
            if (key.contains("sans"))                                return "\\sffamily";
            if (key.contains("serif"))                               return "\\rmfamily";
        }
        return "\\sffamily";
    }

    // -----------------------------------------------------------------------
    // LaTeX helpers
    // -----------------------------------------------------------------------

    private static boolean isFontWeightBold(String w) {
        if (w == null) return false;
        w = w.trim().toLowerCase();
        if (w.equals("bold") || w.equals("bolder")) return true;
        try { return Double.parseDouble(w) >= 600; } catch (NumberFormatException ignore) {}
        return false;
    }

    /** Escape TeX special characters in a text string. */
    public static String escapeTex(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        boolean inMath = false;   // whether a $...$ run is currently open

        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);

            String mathBody = MATH_SYMBOLS.get(cp);
            if (mathBody != null) {
                // Adjacent symbols share one $...$ run: "$\\alpha\\beta$" rather than
                // "$\\alpha$$\\beta$", which would add spurious inter-group spacing.
                if (!inMath) { out.append('$'); inMath = true; }
                out.append(mathBody);
                continue;
            }
            if (inMath) { out.append('$'); inMath = false; }

            String textBody = TEXT_SYMBOLS.get(cp);
            if (textBody != null) { out.append(textBody); continue; }

            appendLiteral(out, cp);
        }
        if (inMath) out.append('$');
        return out.toString();
    }

    /**
     * Appends one character that has no symbol mapping, escaping it if TeX would
     * otherwise read it as syntax, and substituting it if the font cannot show it.
     */
    private static void appendLiteral(StringBuilder out, int cp) {
        switch (cp) {
            // The ten characters that are TeX syntax. An unescaped % does not fail,
            // it comments out the rest of the line.
            case '\\' -> out.append("\\textbackslash{}");
            case '{'  -> out.append("\\{");
            case '}'  -> out.append("\\}");
            case '_'  -> out.append("\\_");
            case '%'  -> out.append("\\%");
            case '&'  -> out.append("\\&");
            case '$'  -> out.append("\\$");
            case '#'  -> out.append("\\#");
            case '~'  -> out.append("\\textasciitilde{}");
            case '^'  -> out.append("\\textasciicircum{}");
            // Braced so consecutive hyphens cannot form an en- or em-dash ligature.
            case '-'  -> out.append("{-}");
            default -> {
                if (cp < 0x80) {
                    out.appendCodePoint(cp);
                } else if (isDirectlyRenderable(cp)) {
                    out.appendCodePoint(cp);
                } else {
                    // A byte inputenc cannot map is a compile error, so an
                    // unrepresentable character becomes a visible placeholder.
                    out.append(UNREPRESENTABLE);
                }
            }
        }
    }

    /**
     * True for characters {@code inputenc[utf8]} + {@code fontenc[T1]} can typeset
     * directly: Latin-1 Supplement and Latin Extended-A, which cover Western and
     * Central European text. Anything outside that needs an explicit mapping, and
     * scripts pdflatex cannot set at all (CJK, Arabic, Hebrew, Cyrillic) fall through
     * to the placeholder; rendering those needs XeLaTeX or LuaLaTeX.
     */
    private static boolean isDirectlyRenderable(int cp) {
        return (cp >= 0x00A0 && cp <= 0x00FF)    // Latin-1 Supplement
            || (cp >= 0x0100 && cp <= 0x017F);   // Latin Extended-A
    }

    /** Stand-in for a character this preamble cannot typeset. */
    private static final String UNREPRESENTABLE = "?";

    /** Characters rendered as a math-mode command, merged into shared $...$ runs. */
    private static final Map<Integer, String> MATH_SYMBOLS = new HashMap<>();
    /** Characters rendered as a text-mode command. */
    private static final Map<Integer, String> TEXT_SYMBOLS = new HashMap<>();

    private static void math(int cp, String body) { MATH_SYMBOLS.put(cp, terminated(body)); }
    private static void text(int cp, String body) { TEXT_SYMBOLS.put(cp, terminated(body)); }

    /**
     * Adds {@code {}} after a command that ends in a letter. TeX reads a control
     * sequence greedily, so a Greek word mapping to {@code M\pio\rho} would contain
     * the undefined control sequence {@code \pio}; {@code M\pi{}o\rho{}} is the same
     * output and cannot run on.
     */
    private static String terminated(String body) {
        return body.matches(".*\\\\[a-zA-Z]+") ? body + "{}" : body;
    }

    static {
        // --- Greek. Capitals that look like Latin letters are set as those letters.
        String[] lower = {"alpha","beta","gamma","delta","epsilon","zeta","eta","theta",
                          "iota","kappa","lambda","mu","nu","xi",null,"pi","rho",null,
                          "sigma","tau","upsilon","phi","chi","psi","omega"};
        for (int k = 0; k < lower.length; k++) {
            if (lower[k] != null) math(0x03B1 + k, "\\" + lower[k]);
        }
        math(0x03BF, "o");            // omicron has no command of its own
        math(0x03C2, "\\varsigma");
        String[] upperCmd = {null,"B",null,"\\Delta","E","Z","H","\\Theta","I","K",
                             "\\Lambda","M","N","\\Xi","O","\\Pi","P",null,
                             "\\Sigma","T","\\Upsilon","\\Phi","X","\\Psi","\\Omega"};
        math(0x0391, "A");
        for (int k = 0; k < upperCmd.length; k++) {
            if (upperCmd[k] != null) math(0x0391 + k, upperCmd[k]);
        }

        // Accented Greek (tonos, dialytika). LaTeX cannot set the accent in math mode;
        // folding to the base letter keeps the word readable.
        int[][] foldGreek = {
            {0x03AC, 0x03B1}, {0x03AD, 0x03B5}, {0x03AE, 0x03B7}, {0x03AF, 0x03B9},
            {0x03CA, 0x03B9}, {0x0390, 0x03B9}, {0x03CC, 0x03BF}, {0x03CD, 0x03C5},
            {0x03CB, 0x03C5}, {0x03B0, 0x03C5}, {0x03CE, 0x03C9},
            {0x0386, 0x0391}, {0x0388, 0x0395}, {0x0389, 0x0397}, {0x038A, 0x0399},
            {0x038C, 0x039F}, {0x038E, 0x03A5}, {0x038F, 0x03A9},
        };
        for (int[] pair : foldGreek) {
            String base = MATH_SYMBOLS.get(pair[1]);
            if (base != null) MATH_SYMBOLS.put(pair[0], base);
        }

        // --- Arrows
        math(0x2190, "\\leftarrow");   math(0x2192, "\\rightarrow");
        math(0x2191, "\\uparrow");     math(0x2193, "\\downarrow");
        math(0x2194, "\\leftrightarrow");
        math(0x21D0, "\\Leftarrow");   math(0x21D2, "\\Rightarrow");
        math(0x21D4, "\\Leftrightarrow");

        // --- Relations and operators
        math(0x2260, "\\neq");     math(0x2264, "\\leq");    math(0x2265, "\\geq");
        math(0x2248, "\\approx");  math(0x2261, "\\equiv");  math(0x221E, "\\infty");
        math(0x2211, "\\sum");     math(0x220F, "\\prod");   math(0x221A, "\\sqrt{}");
        math(0x222B, "\\int");     math(0x2202, "\\partial");math(0x2205, "\\emptyset");
        math(0x2208, "\\in");      math(0x2209, "\\notin");  math(0x2282, "\\subset");
        math(0x2283, "\\supset");  math(0x222A, "\\cup");    math(0x2229, "\\cap");
        math(0x2200, "\\forall");  math(0x2203, "\\exists"); math(0x00AC, "\\neg");
        math(0x2227, "\\wedge");   math(0x2228, "\\vee");    math(0x22C5, "\\cdot");
        math(0x2032, "'");           math(0x2033, "''");
        math(0x2212, "-");           // U+2212 MINUS SIGN, not a hyphen

        // --- Typography
        text(0x2013, "--");                 text(0x2014, "---");
        text(0x2010, "-");                  text(0x2011, "-");
        text(0x2018, "`");                  text(0x2019, "'");
        text(0x201A, "\\quotesinglbase{}");
        text(0x201C, "``");                 text(0x201D, "''");
        text(0x201E, "\\quotedblbase{}");
        text(0x00AB, "\\guillemotleft{}"); text(0x00BB, "\\guillemotright{}");
        text(0x2026, "\\ldots{}");         text(0x2022, "\\textbullet{}");
        text(0x2030, "\\textperthousand{}");
        text(0x2020, "\\dag{}");           text(0x2021, "\\ddag{}");
        text(0x2122, "\\texttrademark{}");
        text(0x00A0, "~");                  // no-break space is TeX's ~ by definition

        // --- Currency beyond Latin-1
        text(0x20AC, "\\texteuro{}");
    }
}
