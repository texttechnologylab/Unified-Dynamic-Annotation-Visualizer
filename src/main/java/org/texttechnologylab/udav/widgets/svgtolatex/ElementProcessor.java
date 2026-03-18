package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Processes individual SVG elements and emits corresponding TikZ commands
 * into the shared {@link ConversionContext#body}.
 */
public class ElementProcessor {

    private final ConversionContext ctx;
    private final GradientHandler  gradients;
    private final StrokeHelper     strokes;
    private final PathBuilder      paths;
    private final TextRenderer     text;

    public ElementProcessor(ConversionContext ctx,
                            GradientHandler gradients,
                            StrokeHelper strokes,
                            PathBuilder paths,
                            TextRenderer text) {
        this.ctx       = ctx;
        this.gradients = gradients;
        this.strokes   = strokes;
        this.paths     = paths;
        this.text      = text;
    }

    // -----------------------------------------------------------------------
    // Gaussian blur filter detection
    // -----------------------------------------------------------------------

    /**
     * Check if an element has a {@code filter} attribute/style referencing a
     * {@code <feGaussianBlur>}.  Returns the blur's stdDeviation (> 0) or -1
     * if no Gaussian blur is applied.
     */
    private double getGaussianBlurStdDev(Element el) {
        String filterRef = getStyleOrAttr(el, "filter");
        if (filterRef == null || !filterRef.contains("url(#")) return -1;
        Matcher fm = Pattern.compile("url\\(#([^)]+)\\)").matcher(filterRef);
        if (!fm.find()) return -1;
        Element filterEl = ctx.defsMap.get(fm.group(1));
        if (filterEl == null) return -1;
        NodeList kids = filterEl.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element)) continue;
            Element child = (Element) kids.item(i);
            String tag = child.getTagName().replaceFirst(".*:", "").toLowerCase();
            if ("fegaussianblur".equals(tag)) {
                return parseDouble(child.getAttribute("stdDeviation"), -1);
            }
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // Node dispatch
    // -----------------------------------------------------------------------

    public void processNode(Node node, double[] ctm, InheritedAttrs inh) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();

        switch (tag) {
            case "svg":      processGroup(el, ctm, inh);   break;
            case "g":        processGroup(el, ctm, inh);   break;
            case "rect":     processRect(el, ctm, inh);    break;
            case "circle":   processCircle(el, ctm, inh);  break;
            case "ellipse":  processEllipse(el, ctm, inh); break;
            case "path":     processPath(el, ctm, inh);    break;
            case "line":     processLine(el, ctm, inh);    break;
            case "text":     text.processText(el, ctm, inh); break;
            case "tspan":    text.processTspan(el, ctm, inh); break;
            case "defs":     break;
            case "clippath": break;
            case "use":      processUse(el, ctm, inh);     break;
        }
    }

    // -----------------------------------------------------------------------
    // <g> / <svg>
    // -----------------------------------------------------------------------

    private void processGroup(Element el, double[] ctm, InheritedAttrs inh) {
        // Detect Gaussian blur on this group
        String tag0 = el.getTagName().replaceFirst(".*:", "").toLowerCase();
        double groupBlurStdDev = ("g".equals(tag0)) ? getGaussianBlurStdDev(el) : -1;

        String transformAttr = el.getAttribute("transform");
        double[] elemMtx = transformAttr.isEmpty() ? identityMtx() : parseTransformMtx(transformAttr);
        double[] newCtm = composeMtx(ctm, elemMtx);

        boolean nestedSvgClip = false;
        double  clipX1 = 0, clipY1 = 0, clipX2 = 0, clipY2 = 0;

        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
        if ("svg".equals(tag) && el != ctx.rootElement) {
            double svgX = parseDouble(el.getAttribute("x"), 0);
            double svgY = parseDouble(el.getAttribute("y"), 0);
            double svgW = parseDouble(el.getAttribute("width"),  0);
            double svgH = parseDouble(el.getAttribute("height"), 0);

            if (svgW > 0 && svgH > 0) {
                double[] p00 = applyMtxAbs(newCtm, svgX,        svgY);
                double[] p11 = applyMtxAbs(newCtm, svgX + svgW, svgY + svgH);
                clipX1 = Math.min(p00[0], p11[0]);
                clipY1 = Math.min(p00[1], p11[1]);
                clipX2 = Math.max(p00[0], p11[0]);
                clipY2 = Math.max(p00[1], p11[1]);
                nestedSvgClip = true;
            }

            String vbStr = el.getAttribute("viewBox");
            if (!vbStr.isEmpty() && svgW > 0 && svgH > 0) {
                double[] vb = parseNumbers(vbStr);
                if (vb.length >= 4 && vb[2] > 0 && vb[3] > 0) {
                    String par = el.getAttribute("preserveAspectRatio").trim();
                    if (par.isEmpty()) par = "xMidYMid meet";
                    double uniformScale;
                    double offsetX = 0, offsetY = 0;
                    if (par.contains("none")) {
                        uniformScale = svgW / vb[2];
                    } else {
                        boolean isMeet = !par.contains("slice");
                        double sx = svgW / vb[2], sy = svgH / vb[3];
                        uniformScale = isMeet ? Math.min(sx, sy) : Math.max(sx, sy);
                        double contentW = vb[2] * uniformScale, contentH = vb[3] * uniformScale;
                        if      (par.contains("xMid")) offsetX = (svgW - contentW) / 2.0;
                        else if (par.contains("xMax")) offsetX = svgW - contentW;
                        if      (par.contains("YMid")) offsetY = (svgH - contentH) / 2.0;
                        else if (par.contains("YMax")) offsetY = svgH - contentH;
                    }
                    double[] vbMtx = new double[]{
                            uniformScale, 0, 0, uniformScale,
                            svgX + offsetX - uniformScale * vb[0],
                            svgY + offsetY - uniformScale * vb[1]};
                    newCtm = composeMtx(newCtm, vbMtx);
                } else {
                    double[] tMtx = new double[]{1, 0, 0, 1, svgX, svgY};
                    newCtm = composeMtx(newCtm, tMtx);
                }
            } else if (svgW > 0 || svgH > 0) {
                double[] tMtx = new double[]{1, 0, 0, 1, svgX, svgY};
                newCtm = composeMtx(newCtm, tMtx);
            }
        }

        // Inherit presentation attributes
        InheritedAttrs ni = inh.copy();
        if (!el.getAttribute("fill").isEmpty())        ni.fill       = el.getAttribute("fill");
        if (!el.getAttribute("stroke").isEmpty())      ni.stroke     = el.getAttribute("stroke");
        if (!el.getAttribute("text-anchor").isEmpty()) ni.textAnchor = el.getAttribute("text-anchor");
        if (!el.getAttribute("font-size").isEmpty())
            ni.fontSize = parseDouble(el.getAttribute("font-size"), ni.fontSize);
        if (!el.getAttribute("font-family").isEmpty())
            ni.fontFamily = el.getAttribute("font-family").trim();
        else {
            Matcher ffm = Pattern.compile("font-family\\s*:\\s*([^;]+)").matcher(el.getAttribute("style"));
            if (ffm.find()) ni.fontFamily = ffm.group(1).trim();
        }
        {
            double sw = parseDouble(el.getAttribute("stroke-width"), -1);
            if (sw < 0) sw = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);
            if (sw >= 0) ni.strokeWidth = sw;
        }
        {
            String fw = el.getAttribute("font-weight").trim();
            if (fw.isEmpty()) {
                Matcher fwm = Pattern.compile("font-weight\\s*:\\s*([^;]+)").matcher(el.getAttribute("style"));
                if (fwm.find()) fw = fwm.group(1).trim();
            }
            if (!fw.isEmpty()) ni.fontWeight = fw;
        }
        {
            String tm = el.getAttribute("data-texmode").trim();
            if (tm.isEmpty()) tm = el.getAttribute("texmode").trim();
            if (!tm.isEmpty()) ni.texMode = tm.toLowerCase();
        }

        // Open clip scopes
        StringBuilder body = ctx.body;
        if (nestedSvgClip) {
            body.append("\\begin{scope}\n");
            body.append(String.format(Locale.US,
                    "\\clip (%.4f, %.4f) rectangle (%.4f, %.4f);\n",
                    ctx.toX(clipX1), ctx.toY(clipY2), ctx.toX(clipX2), ctx.toY(clipY1)));
        }

        boolean clipPathScope = false;
        String clipPathAttr = el.getAttribute("clip-path");
        if (!clipPathAttr.isEmpty()) {
            Matcher cm = Pattern.compile("url\\(#([^)]+)\\)").matcher(clipPathAttr);
            if (cm.find()) {
                double[] rect = ctx.clipRects.get(cm.group(1));
                if (rect != null) {
                    double[] p00 = applyMtxAbs(newCtm, rect[0],           rect[1]);
                    double[] p11 = applyMtxAbs(newCtm, rect[0] + rect[2], rect[1] + rect[3]);
                    double cx1 = ctx.toX(Math.min(p00[0], p11[0]));
                    double cy1 = ctx.toY(Math.max(p00[1], p11[1]));
                    double cx2 = ctx.toX(Math.max(p00[0], p11[0]));
                    double cy2 = ctx.toY(Math.min(p00[1], p11[1]));
                    body.append("\\begin{scope}\n");
                    body.append(String.format(Locale.US,
                            "\\clip (%.4f, %.4f) rectangle (%.4f, %.4f);\n", cx1, cy1, cx2, cy2));
                    clipPathScope = true;
                }
            }
        }

        // Blurred group handling: force all children to render as a uniform
        // silhouette by suppressing strokes and overriding fills to black.
        // This prevents internal detail (handle wraps, guard outlines, etc.)
        // from showing through what should be a soft shadow.
        // Also honour the group's own CSS opacity.
        boolean blurGroupScope = false;
        if (groupBlurStdDev > 0) {
            ni.forceNoStroke = true;      // suppress ALL strokes, even element-level
            ni.forceFill = "#000000";     // uniform black silhouette
            ni.strokeWidth = 0;
            double groupOpacity = parseOpacity(el);
            double opBase = (groupOpacity >= 0 && groupOpacity < 1) ? groupOpacity : 1.0;
            double blurReduction = 1.0 / (1.0 + groupBlurStdDev / 6.0);
            body.append(String.format(Locale.US,
                    "\\begin{scope}[opacity=%.2f]\n", opBase * blurReduction));
            blurGroupScope = true;
        }

        // Recurse into children
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
            processNode(kids.item(i), newCtm, ni);

        // Close clip scopes in reverse order
        if (clipPathScope)  body.append("\\end{scope}\n");
        if (nestedSvgClip)  body.append("\\end{scope}\n");
        if (blurGroupScope) body.append("\\end{scope}\n");
    }

    // -----------------------------------------------------------------------
    // <use>
    // -----------------------------------------------------------------------

    private void processUse(Element el, double[] ctm, InheritedAttrs inh) {
        String href = el.getAttribute("xlink:href");
        if (href.isEmpty()) href = el.getAttribute("href");
        if (href.isEmpty() || !href.startsWith("#")) return;
        String id = href.substring(1);
        Element target = ctx.defsMap.get(id);
        if (target == null) return;

        double ux = parseDouble(el.getAttribute("x"), 0);
        double uy = parseDouble(el.getAttribute("y"), 0);

        double[] useMtx = parseTransformMtx(el.getAttribute("transform"));
        double[] tMtx   = new double[]{1, 0, 0, 1, ux, uy};
        double[] newCtm = composeMtx(ctm, composeMtx(tMtx, useMtx));

        InheritedAttrs ni = inh.copy();
        if (!el.getAttribute("fill").isEmpty())        ni.fill       = el.getAttribute("fill");
        if (!el.getAttribute("stroke").isEmpty())      ni.stroke     = el.getAttribute("stroke");
        if (!el.getAttribute("text-anchor").isEmpty()) ni.textAnchor = el.getAttribute("text-anchor");

        processNode(target, newCtm, ni);
    }

    // -----------------------------------------------------------------------
    // <rect>
    // -----------------------------------------------------------------------

    private void processRect(Element el, double[] ctm, InheritedAttrs inh) {
        double lx = parseDouble(el.getAttribute("x"), 0);
        double ly = parseDouble(el.getAttribute("y"), 0);
        double lw = parseDouble(el.getAttribute("width"),  0);
        double lh = parseDouble(el.getAttribute("height"), 0);
        double lrx = parseDouble(el.getAttribute("rx"), -1);
        double lry = parseDouble(el.getAttribute("ry"), -1);
        if (lrx < 0 && lry < 0) { lrx = 0; lry = 0; }
        else if (lrx < 0) lrx = lry;
        else if (lry < 0) lry = lrx;

        String elemTransformStr = el.getAttribute("transform");
        double[] effectiveCtm = elemTransformStr.isEmpty() ? ctm
                : composeMtx(ctm, parseTransformMtx(elemTransformStr));

        ColorManager colors = ctx.colors;
        String stroke  = colors.resolveStroke(el, inh);
        String rawFill = colors.getRawFill(el, inh);
        double opacity = parseOpacity(el);

        String gradOpts = gradients.buildGradientOpts(rawFill, lx, ly, lw, lh);
        String fill     = gradOpts != null ? null : colors.resolveFill(el, inh);

        List<String> opts = new ArrayList<>();
        if (gradOpts != null)          opts.add(gradOpts);
        else if (fill != null && !fill.equals("none")) opts.add("fill=" + fill);
        if (!stroke.equals("none"))    opts.add("draw=" + stroke);
        if (opacity >= 0 && opacity < 1)
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));
        if (isEvenOdd(el)) opts.add("even odd rule");
        String optStr = String.join(", ", opts);

        double sa = scaleApprox(effectiveCtm);

        if (hasRotation(effectiveCtm)) {
            double[][] corners = {{lx,ly},{lx+lw,ly},{lx+lw,ly+lh},{lx,ly+lh}};
            StringBuilder pg = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                double[] w = applyMtxAbs(effectiveCtm, corners[i][0], corners[i][1]);
                pg.append(i == 0 ? "" : " -- ")
                        .append(String.format(Locale.US, "(%.4f, %.4f)", ctx.toX(w[0]), ctx.toY(w[1])));
            }
            ctx.body.append(String.format("\\path[%s] %s -- cycle;\n", optStr, pg));
            return;
        }

        double[] p1 = applyMtxAbs(effectiveCtm, lx,      ly);
        double[] p2 = applyMtxAbs(effectiveCtm, lx + lw, ly + lh);
        double tikzX1 = ctx.toX(Math.min(p1[0], p2[0]));
        double tikzY1 = ctx.toY(Math.min(p1[1], p2[1]));
        double tikzX2 = ctx.toX(Math.max(p1[0], p2[0]));
        double tikzY2 = ctx.toY(Math.max(p1[1], p2[1]));

        if (lrx > 0 || lry > 0) {
            double rCm = sa * (lrx + lry) / 2.0 * PX_TO_CM;
            ctx.body.append(String.format(Locale.US,
                    "\\path[%s, rounded corners=%.4fcm] (%.4f, %.4f) rectangle (%.4f, %.4f);\n",
                    optStr, rCm, tikzX1, tikzY1, tikzX2, tikzY2));
        } else {
            ctx.body.append(String.format(Locale.US,
                    "\\path[%s] (%.4f, %.4f) rectangle (%.4f, %.4f);\n",
                    optStr, tikzX1, tikzY1, tikzX2, tikzY2));
        }
    }

    // -----------------------------------------------------------------------
    // <circle>
    // -----------------------------------------------------------------------

    private void processCircle(Element el, double[] ctm, InheritedAttrs inh) {
        double lcx = parseDouble(el.getAttribute("cx"), 0);
        double lcy = parseDouble(el.getAttribute("cy"), 0);
        double lr  = parseDouble(el.getAttribute("r"),  0);

        double[] wc = applyMtxAbs(ctm, lcx, lcy);
        double sa = scaleApprox(ctm);
        double r = sa * lr;

        double strokeWidth = parseDouble(el.getAttribute("stroke-width"), -1);
        if (strokeWidth < 0) strokeWidth = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);
        if (strokeWidth < 0) strokeWidth = inh.strokeWidth;

        ColorManager colors = ctx.colors;
        String rawFill  = colors.getRawFill(el, inh);
        String gradOpts = gradients.buildGradientOpts(rawFill, lcx - lr, lcy - lr, 2*lr, 2*lr);
        String fill     = gradOpts != null ? null : colors.resolveFill(el, inh);
        String stroke   = colors.resolveStroke(el, inh);

        List<String> opts = new ArrayList<>();
        if (gradOpts != null)                          opts.add(gradOpts);
        else if (fill != null && !fill.equals("none")) opts.add("fill=" + fill);
        if (!stroke.equals("none")) {
            opts.add("draw=" + stroke);
            double sw = (strokeWidth < 0 ? 1.0 : strokeWidth) * sa * PX_TO_CM;
            opts.add(String.format(Locale.US, "line width=%.4fcm", sw));
        }
        if (opts.isEmpty()) opts.add("draw=c000000");

        double opacity = parseOpacity(el);
        if (opacity >= 0 && opacity < 1)
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));

        ctx.body.append(String.format(Locale.US,
                "\\path[%s] (%.4f, %.4f) circle (%.4fcm);\n",
                String.join(", ", opts), ctx.toX(wc[0]), ctx.toY(wc[1]), r * PX_TO_CM));
    }

    // -----------------------------------------------------------------------
    // <ellipse>
    // -----------------------------------------------------------------------

    private void processEllipse(Element el, double[] ctm, InheritedAttrs inh) {
        String elemTransformStr = el.getAttribute("transform");
        double[] ellCtm = elemTransformStr.isEmpty() ? ctm
                : composeMtx(ctm, parseTransformMtx(elemTransformStr));

        double lcx = parseDouble(el.getAttribute("cx"), 0);
        double lcy = parseDouble(el.getAttribute("cy"), 0);
        double lrx = parseDouble(el.getAttribute("rx"), 0);
        double lry = parseDouble(el.getAttribute("ry"), 0);

        double[] wc = applyMtxAbs(ellCtm, lcx, lcy);
        double sa = scaleApprox(ellCtm);

        double strokeWidth = parseDouble(el.getAttribute("stroke-width"), -1);
        if (strokeWidth < 0) strokeWidth = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);
        if (strokeWidth < 0) strokeWidth = inh.strokeWidth;

        ColorManager colors = ctx.colors;
        String rawFill  = colors.getRawFill(el, inh);
        String gradOpts = gradients.buildGradientOpts(rawFill, lcx - lrx, lcy - lry, 2*lrx, 2*lry);
        String fill     = gradOpts != null ? null : colors.resolveFill(el, inh);
        String stroke   = colors.resolveStroke(el, inh);

        List<String> opts = new ArrayList<>();
        if (gradOpts != null)                          opts.add(gradOpts);
        else if (fill != null && !fill.equals("none")) opts.add("fill=" + fill);
        if (!stroke.equals("none")) {
            opts.add("draw=" + stroke);
            double sw = (strokeWidth < 0 ? 1.0 : strokeWidth) * sa * PX_TO_CM;
            opts.add(String.format(Locale.US, "line width=%.4fcm", sw));
        }
        if (opts.isEmpty()) opts.add("draw=c000000");

        double opacity = parseOpacity(el);
        if (opacity >= 0 && opacity < 1)
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));

        double rotateDeg = Math.toDegrees(Math.atan2(ellCtm[1], ellCtm[0]));
        double rxWorld = lrx * sa * PX_TO_CM;
        double ryWorld = lry * sa * PX_TO_CM;

        if (Math.abs(rotateDeg) > 0.01) {
            ctx.body.append("\\begin{scope}\n");
            ctx.body.append(String.format(Locale.US,
                    "\\pgftransformrotate{%.4f}\n", -rotateDeg));
            ctx.body.append(String.format(Locale.US,
                    "\\path[%s] (%.4f, %.4f) ellipse (%.4fcm and %.4fcm);\n",
                    String.join(", ", opts), ctx.toX(wc[0]), ctx.toY(wc[1]), rxWorld, ryWorld));
            ctx.body.append("\\end{scope}\n");
        } else {
            ctx.body.append(String.format(Locale.US,
                    "\\path[%s] (%.4f, %.4f) ellipse (%.4fcm and %.4fcm);\n",
                    String.join(", ", opts), ctx.toX(wc[0]), ctx.toY(wc[1]), rxWorld, ryWorld));
        }
    }

    // -----------------------------------------------------------------------
    // <path>
    // -----------------------------------------------------------------------

    private void processPath(Element el, double[] ctm, InheritedAttrs inh) {
        String d = el.getAttribute("d").trim();
        if (d.isEmpty()) return;

        String elemTransformStr = el.getAttribute("transform");
        double[] pathCtm = elemTransformStr.isEmpty() ? ctm
                : composeMtx(ctm, parseTransformMtx(elemTransformStr));

        ColorManager colors = ctx.colors;
        String fill   = colors.resolveFill(el, inh);
        String stroke = colors.resolveStroke(el, inh);

        double strokeWidth = parseDouble(el.getAttribute("stroke-width"), -1);
        if (strokeWidth < 0) strokeWidth = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);
        if (strokeWidth < 0) strokeWidth = inh.strokeWidth;
        double sa = scaleApprox(pathCtm);

        String pathStr = paths.buildTikzPath(d, pathCtm);
        if (pathStr.isEmpty()) return;

        String rawFill  = colors.getRawFill(el, inh);
        String gradOpts = gradients.buildGradientOpts(rawFill, 0, 0, 0, 0);

        // Gaussian blur handling.
        // IMPORTANT: when inside a blurred group (forceNoStroke=true), children
        // must NOT get their own blur halos — the group already handles the
        // visual softening.  Rendering child blurs as halos creates texture.
        double blurStd = inh.forceNoStroke ? -1 : getGaussianBlurStdDev(el);
        if (blurStd > 0) {
            if (!fill.equals("none")) {
                // Blurred filled shape (shadow or glow)
                emitBlurredPath(pathStr, fill, gradOpts, blurStd, sa, el);
                return;
            }
            if (!stroke.equals("none")) {
                // Blurred stroke-only path (e.g. blade highlight glow).
                // Blur dilutes a bright stroke into a barely-visible haze.
                double baseOpacity = parseOpacity(el);
                double opMul = (baseOpacity >= 0 && baseOpacity < 1) ? baseOpacity : 1.0;
                double sw = strokeWidth < 0 ? (inh.strokeWidth < 0 ? 1.0 : inh.strokeWidth) : strokeWidth;
                List<String> bopts = new ArrayList<>();
                bopts.add("draw=" + stroke);
                bopts.add(String.format(Locale.US, "line width=%.4fcm", sw * sa * PX_TO_CM));
                bopts.add(String.format(Locale.US, "opacity=%.4f", opMul * 0.06));
                bopts.add("line join=round");
                bopts.add("line cap=round");
                ctx.body.append(String.format("\\path[%s] %s;\n",
                        String.join(", ", bopts), pathStr));
                return;
            }
        }

        List<String> opts = new ArrayList<>();
        if (gradOpts != null) {
            opts.add(gradOpts);
        } else if (!fill.equals("none")) {
            opts.add("fill=" + fill);
        }
        if (!stroke.equals("none")) {
            opts.add("draw=" + stroke);
            strokes.addStrokeOpts(opts, el, inh, strokeWidth, sa);
        }
        if (opts.isEmpty()) opts.add("draw=c000000");

        double opacity = parseOpacity(el);
        if (opacity >= 0 && opacity < 1)
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));

        if (isEvenOdd(el)) opts.add("even odd rule");

        ctx.body.append(String.format("\\path[%s] %s;\n", String.join(", ", opts), pathStr));
    }

    /**
     * Emit a blurred filled path.  Distinguishes between two visual effects:
     * <ul>
     *   <li><b>Shadow</b> (dark fill): edge-softening with concentric halo layers
     *       at Gaussian-profile opacity.  The core stays at moderate opacity.</li>
     *   <li><b>Glow</b> (light fill, e.g. white stars): Gaussian blur dilutes
     *       the brightness drastically.  Rendered as a single faint fill with
     *       no halo layers.</li>
     * </ul>
     */
    private void emitBlurredPath(String pathStr, String fillColor,
                                 String gradOpts, double blurStd,
                                 double scaleApprox, Element el) {
        double blurCm = blurStd * scaleApprox * PX_TO_CM;
        double baseOpacity = parseOpacity(el);
        double opMul = (baseOpacity >= 0 && baseOpacity < 1) ? baseOpacity : 1.0;

        // Determine if this is a dark shadow or a light glow by sampling the fill colour brightness
        boolean isGlow = isBrightColor(fillColor);

        if (isGlow) {
            // Glow: blur dilutes a bright shape into a soft sparkle.
            // Render at reduced opacity — enough to be visible but subtle.
            double glowOp = opMul * 0.25;
            if (glowOp < 0.003) return;      // invisible, skip entirely
            String fillOpt = (gradOpts != null) ? gradOpts : "fill=" + fillColor;
            ctx.body.append(String.format(Locale.US,
                    "\\path[%s, opacity=%.4f] %s;\n", fillOpt, glowOp, pathStr));
            return;
        }

        // Shadow: concentric halo layers create soft edge fade.
        // 10 layers from 1.5σ (outermost) down to 0.15σ, tight Gaussian falloff.
        int numLayers = 10;
        double sigmaMax = 1.5;
        double sigmaMin = 0.15;

        for (int i = 0; i < numLayers; i++) {
            double t = (double) i / (numLayers - 1);
            double sigma = sigmaMax + t * (sigmaMin - sigmaMax);
            double lwCm = sigma * blurCm;
            // Tight Gaussian profile (σ_scale=0.7) for rapid falloff
            double op = Math.exp(-0.5 * (sigma * sigma) / (0.7 * 0.7)) * opMul;
            if (op < 0.003) continue;
            ctx.body.append(String.format(Locale.US,
                    "\\path[draw=%s, line width=%.4fcm, opacity=%.4f, "
                            + "line join=round, line cap=round] %s;\n",
                    fillColor, lwCm, op, pathStr));
        }

        // Solid core
        String fillOpt = (gradOpts != null) ? gradOpts : "fill=" + fillColor;
        ctx.body.append(String.format(Locale.US,
                "\\path[%s, opacity=%.4f] %s;\n",
                fillOpt, 0.95 * opMul, pathStr));
    }

    /**
     * Returns true if a TikZ colour name corresponds to a bright colour
     * (average RGB channel > 50%).  Used to distinguish shadow fills from
     * glow/sparkle fills.
     */
    private boolean isBrightColor(String tikzColorName) {
        // Reverse-lookup the hex from the TikZ colour name in the colour defs
        for (Map.Entry<String, String> e : ctx.colors.getColorDefs().entrySet()) {
            if (e.getValue().equals(tikzColorName)) {
                int[] rgb = ColorManager.hexToRgb(e.getKey());
                double brightness = (rgb[0] + rgb[1] + rgb[2]) / (3.0 * 255.0);
                return brightness > 0.5;
            }
        }
        // Unknown colour — assume dark (shadow)
        return false;
    }

    // -----------------------------------------------------------------------
    // <line>
    // -----------------------------------------------------------------------

    private void processLine(Element el, double[] ctm, InheritedAttrs inh) {
        double lx1 = parseDouble(el.getAttribute("x1"), 0);
        double ly1 = parseDouble(el.getAttribute("y1"), 0);
        double lx2 = parseDouble(el.getAttribute("x2"), 0);
        double ly2 = parseDouble(el.getAttribute("y2"), 0);
        double[] w1 = applyMtxAbs(ctm, lx1, ly1);
        double[] w2 = applyMtxAbs(ctm, lx2, ly2);
        String stroke = ctx.colors.resolveStroke(el, inh);

        double strokeWidth = parseDouble(el.getAttribute("stroke-width"), -1);
        if (strokeWidth < 0) strokeWidth = parseStyleDouble(el.getAttribute("style"), "stroke-width", -1);
        if (strokeWidth < 0) strokeWidth = inh.strokeWidth;
        double sa = scaleApprox(ctm);

        List<String> opts = new ArrayList<>();
        opts.add("draw=" + stroke);
        strokes.addStrokeOpts(opts, el, inh, strokeWidth, sa);

        ctx.body.append(String.format(Locale.US,
                "\\draw[%s] (%.4f, %.4f) -- (%.4f, %.4f);\n",
                String.join(", ", opts),
                ctx.toX(w1[0]), ctx.toY(w1[1]),
                ctx.toX(w2[0]), ctx.toY(w2[1])));
    }
}