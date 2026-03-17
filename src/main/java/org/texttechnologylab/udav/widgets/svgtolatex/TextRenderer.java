package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Processes SVG {@code <text>} and {@code <tspan>} elements, emitting TikZ
 * {@code \node} commands with the correct font, colour, anchor, and rotation.
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

        boolean hasTspan = false;
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            if ("tspan".equals(((Element) kids.item(i)).getTagName().replaceFirst(".*:", "").toLowerCase())) {
                hasTspan = true; break;
            }
        }

        if (hasTspan) {
            for (int i = 0; i < kids.getLength(); i++) {
                if (!(kids.item(i) instanceof Element)) continue;
                Element child = (Element) kids.item(i);
                if ("tspan".equals(child.getTagName().replaceFirst(".*:", "").toLowerCase()))
                    processTspan(child, textCtm, rotate, scale, textInh);
            }
            return;
        }

        double lx  = parseDouble(el.getAttribute("x"), 0);
        double ly  = parseDouble(el.getAttribute("y"), 0);
        double dy  = parseDy(el, textInh.fontSize);
        double[] wp = applyMtxAbs(textCtm, lx, ly + dy);
        emitTextNode(el, wp[0], wp[1], scale, rotate, ly + dy, textInh);
    }

    // -----------------------------------------------------------------------
    // <tspan>  (two overloads for different call-sites)
    // -----------------------------------------------------------------------

    public void processTspan(Element el, double[] ctm, InheritedAttrs inh) {
        double scale  = scaleApprox(ctm);
        double rotate = rotationDeg(ctm);
        processTspan(el, ctm, rotate, scale, inh);
    }

    private void processTspan(Element el, double[] ctm, double rotate, double scale, InheritedAttrs inh) {
        InheritedAttrs tspanInh = inh.copy();
        applyTextAttrs(el, tspanInh);

        double lx = parseDouble(el.getAttribute("x"), 0);
        double ly = parseDouble(el.getAttribute("y"), 0);
        double dy = parseDy(el, tspanInh.fontSize);
        double[] wp = applyMtxAbs(ctm, lx, ly + dy);
        emitTextNode(el, wp[0], wp[1], scale, rotate, ly + dy, tspanInh);
    }

    // -----------------------------------------------------------------------
    // Shared text-node emission
    // -----------------------------------------------------------------------

    private void emitTextNode(Element el, double finalX, double finalY,
                              double scale, double rotate, double rawY,
                              InheritedAttrs inh) {
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

        String rawTextContent = el.getTextContent();
        String content;
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
    // Attribute helpers
    // -----------------------------------------------------------------------

    /**
     * Apply text-specific presentation attributes from an element onto
     * an InheritedAttrs copy.
     */
    private void applyTextAttrs(Element el, InheritedAttrs inh) {
        if (!el.getAttribute("fill").isEmpty())        inh.fill       = el.getAttribute("fill");
        if (!el.getAttribute("stroke").isEmpty())      inh.stroke     = el.getAttribute("stroke");
        if (!el.getAttribute("text-anchor").isEmpty()) inh.textAnchor = el.getAttribute("text-anchor");
        { String fsAttr = el.getAttribute("font-size");
            if (fsAttr.isEmpty()) { Matcher fsm = Pattern.compile("font-size\\s*:\\s*([^;]+)").matcher(el.getAttribute("style")); if (fsm.find()) fsAttr = fsm.group(1).trim(); }
            if (!fsAttr.isEmpty()) inh.fontSize = parseDouble(fsAttr, inh.fontSize); }
        { String ff = el.getAttribute("font-family").trim();
            if (ff.isEmpty()) { Matcher ffm = Pattern.compile("font-family\\s*:\\s*([^;]+)").matcher(el.getAttribute("style")); if (ffm.find()) ff = ffm.group(1).trim(); }
            if (!ff.isEmpty()) inh.fontFamily = ff; }
        { String fw = el.getAttribute("font-weight").trim();
            if (fw.isEmpty()) { Matcher fwm = Pattern.compile("font-weight\\s*:\\s*([^;]+)").matcher(el.getAttribute("style")); if (fwm.find()) fw = fwm.group(1).trim(); }
            if (!fw.isEmpty()) inh.fontWeight = fw; }
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

    private boolean isFontWeightBold(String w) {
        if (w == null) return false;
        w = w.trim().toLowerCase();
        if (w.equals("bold") || w.equals("bolder")) return true;
        try { return Double.parseDouble(w) >= 600; } catch (NumberFormatException ignore) {}
        return false;
    }

    /** Escape TeX special characters in a text string. */
    public static String escapeTex(String s) {
        return s.replace("\\", "\\textbackslash{}")
                .replace("_",  "\\_")
                .replace("%",  "\\%")
                .replace("&",  "\\&")
                .replace("$",  "\\$")
                .replace("#",  "\\#")
                .replace("{",  "\\{")
                .replace("}",  "\\}")
                .replace("-",  "{-}")
                .replace("\u2212", "\\textminus{}");
    }
}