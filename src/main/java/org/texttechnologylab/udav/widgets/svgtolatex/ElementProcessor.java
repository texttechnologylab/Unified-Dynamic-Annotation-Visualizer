package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.*;
import java.util.regex.*;

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

    /** Ids currently being expanded by {@code <use>}, so a cyclic reference terminates. */
    private final Set<String> expanding = new HashSet<>();

    /** External documents already loaded, so a file referenced many times is parsed once. */
    private final Map<String, org.w3c.dom.Document> externalDocuments = new HashMap<>();

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

    /** Containers open their own clip scope inside {@link #processGroup}. */
    private static final Set<String> CONTAINERS = Set.of("svg", "g", "a");

    /**
     * Elements that put ink on the page themselves. Only these consult
     * {@code visibility}: it is inherited, but a descendant may set it back to
     * {@code visible}, so a hidden container still has to be walked. {@code <use>}
     * and {@code <switch>} count as containers here because they render another
     * subtree.
     */
    private static final Set<String> PAINTING = Set.of(
            "rect", "circle", "ellipse", "line", "polyline", "polygon", "path",
            "text", "image", "foreignobject");

    public void processNode(Node node, double[] ctm, InheritedAttrs inh) {
        if (!(node instanceof Element)) return;
        Element el = (Element) node;
        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();

        // display:none removes the element and everything below it from the render
        // tree. It is not inherited and cannot be overridden from inside: a child
        // saying display="inline" under a display="none" parent still draws nothing,
        // because the parent's subtree is never entered.
        String display = getStyleOrAttr(el, "display");
        if (display != null && "none".equals(display.trim().toLowerCase(Locale.ROOT))) return;

        // The CSS color property, the value currentColor resolves to. Read before
        // the paint is resolved, because an element may set both at once:
        // fill="currentColor" color="#2563EB" paints blue.
        String cssColor = getStyleOrAttr(el, "color");
        if (cssColor != null) {
            String c = cssColor.trim().toLowerCase(Locale.ROOT);
            if (!c.isEmpty() && !"inherit".equals(c) && !"currentcolor".equals(c)) {
                inh = inh.copy();
                inh.color = ctx.colors.resolveColorHex(cssColor.trim(), inh.color);
            }
        }

        // visibility is inherited, so it travels in inh. "inherit" says nothing, so
        // inh is left alone.
        String visibility = getStyleOrAttr(el, "visibility");
        if (visibility != null) {
            String v = visibility.trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty() && !"inherit".equals(v)) {
                inh = inh.copy();
                inh.visibility = v;
            }
        }
        if (PAINTING.contains(tag)
                && ("hidden".equals(inh.visibility) || "collapse".equals(inh.visibility))) {
            return;
        }

        // clip-path applies to any element, not only containers. The clip geometry lives
        // in the referencing element's own user space, so its transform is composed in
        // here; the dispatch below applies that transform to the geometry separately.
        int scopes = 0;
        if (!CONTAINERS.contains(tag)) {
            double[] ownCtm = composeMtx(ctm, parseTransformMtx(el.getAttribute("transform")));
            if (openClipScope(el, getStyleOrAttr(el, "clip-path"), ownCtm)) scopes++;
            if (openMaskScope(el, getStyleOrAttr(el, "mask"), ownCtm)) scopes++;
        }
        try {
            dispatch(el, tag, ctm, inh);
        } finally {
            for (int i = 0; i < scopes; i++) ctx.body.append("\\end{scope}\n");
        }
    }

    /** Substitutes the {@code color} in force for the {@code currentColor} keyword. */
    private static String resolveCurrentColor(String paint, String currentHex) {
        return "currentcolor".equals(paint.trim().toLowerCase(Locale.ROOT)) ? currentHex : paint;
    }

    private void dispatch(Element el, String tag, double[] ctm, InheritedAttrs inh) {
        switch (tag) {
            case "svg":      processGroup(el, ctm, inh);   break;
            case "g":        processGroup(el, ctm, inh);   break;
            // <a> is a container: it takes a transform, inherits presentation
            // attributes and renders its children like <g>.
            case "a":        processGroup(el, ctm, inh);   break;
            case "rect":     processRect(el, ctm, inh);    break;
            case "circle":   processCircle(el, ctm, inh);  break;
            case "ellipse":  processEllipse(el, ctm, inh); break;
            case "path":     processPath(el, ctm, inh);    break;
            case "polyline": processPoly(el, ctm, inh, false); break;
            case "polygon":  processPoly(el, ctm, inh, true);  break;
            case "line":     processLine(el, ctm, inh);    break;
            case "text":     text.processText(el, ctm, inh); break;
            case "tspan":    text.processTspan(el, ctm, inh); break;
            case "foreignobject": text.processForeignObject(el, ctm, inh); break;
            case "defs":     break;
            case "clippath": break;
            case "use":      processUse(el, ctm, inh);     break;
            case "switch":   processSwitch(el, ctm, inh);  break;
            case "image":    processImage(el, ctm, inh);   break;
            case "symbol":   break;   // drawn only where <use> instances it
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

            double[] vbMtx = viewBoxFit(el, svgX, svgY, svgW, svgH);
            if (vbMtx != null) {
                newCtm = composeMtx(newCtm, vbMtx);
            } else if (svgW > 0 || svgH > 0) {
                double[] tMtx = new double[]{1, 0, 0, 1, svgX, svgY};
                newCtm = composeMtx(newCtm, tMtx);
            }
        }

        // Inherit presentation attributes. Every one of these goes through
        // getStyleOrAttr, never getAttribute: a style declaration outranks a
        // presentation attribute of the same name (SVG 1.1 section 6.1), and
        // StyleSheet has flattened the document's CSS into that same attribute.
        InheritedAttrs ni = inh.copy();
        String fill       = getStyleOrAttr(el, "fill");
        String stroke     = getStyleOrAttr(el, "stroke");
        String textAnchor = getStyleOrAttr(el, "text-anchor");
        String fontSize   = getStyleOrAttr(el, "font-size");
        String fontFamily = getStyleOrAttr(el, "font-family");
        String strokeWidth = getStyleOrAttr(el, "stroke-width");
        String fontWeight = getStyleOrAttr(el, "font-weight");

        // Resolved here rather than where it is painted: SVG resolves currentColor
        // against the color in force at the point the paint is specified, and what
        // descends is that resolved value. Deferring it would make
        // <g fill="currentColor" color="lime"><rect color="red"/></g> red.
        if (fill       != null) ni.fill       = resolveCurrentColor(fill, ni.color);
        if (stroke     != null) ni.stroke     = resolveCurrentColor(stroke, ni.color);
        if (textAnchor != null) ni.textAnchor = textAnchor;
        if (fontSize   != null) ni.fontSize   = resolveFontSize(fontSize, ni.fontSize);
        if (fontFamily != null) ni.fontFamily = fontFamily.trim();
        if (fontWeight != null) ni.fontWeight = fontWeight.trim();
        if (strokeWidth != null) {
            double sw = parseDouble(strokeWidth, -1);
            if (sw >= 0) ni.strokeWidth = sw;
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

        int elementScopes = openClipScope(el, getStyleOrAttr(el, "clip-path"), newCtm) ? 1 : 0;
        if (openMaskScope(el, getStyleOrAttr(el, "mask"), newCtm)) elementScopes++;

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

        // Close clip scopes in reverse order, one \\end per scope opened: a clip and a
        // mask on the same container open two.
        for (int i = 0; i < elementScopes; i++) body.append("\\end{scope}\n");
        if (nestedSvgClip)  body.append("\\end{scope}\n");
        if (blurGroupScope) body.append("\\end{scope}\n");
    }

    // -----------------------------------------------------------------------
    // <use>
    // -----------------------------------------------------------------------

    private void processUse(Element el, double[] ctm, InheritedAttrs inh) {
        String href = el.getAttribute("xlink:href");
        if (href.isEmpty()) href = el.getAttribute("href");
        href = href.trim();
        if (href.isEmpty()) return;

        // The key for the cycle guard is the whole reference, so the same fragment in two
        // different files is two different expansions.
        String id = href;
        Element target;
        if (href.startsWith("#")) {
            id = href.substring(1);
            target = ctx.defsMap.get(id);
        } else {
            target = resolveExternalTarget(href);
        }
        if (target == null) return;

        // A <use> that reaches its own ancestor would recurse for ever.
        if (!expanding.add(id)) return;
        try {
            double ux = parseDouble(el.getAttribute("x"), 0);
            double uy = parseDouble(el.getAttribute("y"), 0);

            // SVG defines <use> as a <g> carrying the use's transform, wrapping a
            // translate(x, y), so the transform is the outer one; composing it the
            // other way round scales the offset along with the content.
            double[] useMtx = parseTransformMtx(el.getAttribute("transform"));
            double[] tMtx   = new double[]{1, 0, 0, 1, ux, uy};
            double[] newCtm = composeMtx(ctm, composeMtx(useMtx, tMtx));

            // Same precedence rule as in processGroup: style declaration before attribute.
            InheritedAttrs ni = inh.copy();
            String useFill       = getStyleOrAttr(el, "fill");
            String useStroke     = getStyleOrAttr(el, "stroke");
            String useTextAnchor = getStyleOrAttr(el, "text-anchor");
            if (useFill       != null) ni.fill       = resolveCurrentColor(useFill, ni.color);
            if (useStroke     != null) ni.stroke     = resolveCurrentColor(useStroke, ni.color);
            if (useTextAnchor != null) ni.textAnchor = useTextAnchor;

            String targetTag = target.getTagName().replaceFirst(".*:", "").toLowerCase();
            if (targetTag.equals("symbol") || targetTag.equals("svg")) {
                processSymbolInstance(el, target, newCtm, ni);
            } else {
                processNode(target, newCtm, ni);
            }
        } finally {
            expanding.remove(id);
        }
    }

    /**
     * Matrix mapping an element's {@code viewBox} into the viewport box
     * {@code (x, y, w, h)}, honouring {@code preserveAspectRatio}.
     * <p>
     * Shared by nested {@code <svg>} and by {@code <use>} on a {@code <symbol>},
     * because SVG defines the second in terms of the first: instancing a symbol creates
     * an {@code <svg>} carrying the symbol's viewBox and the use's width and height.
     *
     * @return the matrix, or null when there is no usable viewBox to fit
     */
    private static double[] viewBoxFit(Element el, double x, double y, double w, double h) {
        String vbStr = el.getAttribute("viewBox");
        if (vbStr.isEmpty() || w <= 0 || h <= 0) return null;
        double[] vb = parseNumbers(vbStr);
        if (vb.length < 4 || vb[2] <= 0 || vb[3] <= 0) return null;

        String par = el.getAttribute("preserveAspectRatio").trim();
        if (par.isEmpty()) par = "xMidYMid meet";

        double sx, sy;
        double offsetX = 0, offsetY = 0;
        if (par.contains("none")) {
            // "none" is the one value that does not preserve the ratio: the axes scale
            // independently and the content is stretched to fill the viewport.
            sx = w / vb[2];
            sy = h / vb[3];
        } else {
            boolean meet = !par.contains("slice");
            double fitX = w / vb[2], fitY = h / vb[3];
            // meet fits the whole viewBox inside and leaves margins; slice fills the
            // viewport and lets the overflow be clipped by the viewport itself.
            double scale = meet ? Math.min(fitX, fitY) : Math.max(fitX, fitY);
            sx = scale;
            sy = scale;
            double contentW = vb[2] * scale, contentH = vb[3] * scale;
            if      (par.contains("xMid")) offsetX = (w - contentW) / 2.0;
            else if (par.contains("xMax")) offsetX = w - contentW;
            if      (par.contains("YMid")) offsetY = (h - contentH) / 2.0;
            else if (par.contains("YMax")) offsetY = h - contentH;
        }
        return new double[]{sx, 0, 0, sy,
                x + offsetX - sx * vb[0],
                y + offsetY - sy * vb[1]};
    }

    /**
     * Renders a {@code <use>} whose target is a {@code <symbol>} or an {@code <svg>}.
     * <p>
     * Such a target establishes a new viewport. Its size comes from the
     * {@code <use>}'s width/height, falling back to the target's own, then to the
     * viewBox, and its viewBox is fitted into that box. A {@code <symbol>} is drawn
     * only where it is instanced, which is why {@link #dispatch} skips it.
     */
    private void processSymbolInstance(Element use, Element target,
                                       double[] ctm, InheritedAttrs inh) {
        double[] vb = parseNumbers(target.getAttribute("viewBox"));
        boolean hasViewBox = vb.length >= 4 && vb[2] > 0 && vb[3] > 0;

        double w = parseDouble(use.getAttribute("width"), -1);
        if (w <= 0) w = parseDouble(target.getAttribute("width"), -1);
        if (w <= 0) w = hasViewBox ? vb[2] : ctx.svgWidth;
        double h = parseDouble(use.getAttribute("height"), -1);
        if (h <= 0) h = parseDouble(target.getAttribute("height"), -1);
        if (h <= 0) h = hasViewBox ? vb[3] : ctx.svgHeight;

        double[] contentCtm = ctm;
        double[] fit = viewBoxFit(target, 0, 0, w, h);
        if (fit != null) contentCtm = composeMtx(ctm, fit);

        // The instance clips to its viewport, as a nested <svg> does.
        double[] p00 = applyMtxAbs(ctm, 0, 0);
        double[] p11 = applyMtxAbs(ctm, w, h);
        ctx.body.append("\\begin{scope}\n");
        ctx.body.append(String.format(Locale.US,
                "\\clip (%.4f, %.4f) rectangle (%.4f, %.4f);\n",
                ctx.toX(Math.min(p00[0], p11[0])), ctx.toY(Math.max(p00[1], p11[1])),
                ctx.toX(Math.max(p00[0], p11[0])), ctx.toY(Math.min(p00[1], p11[1]))));

        NodeList kids = target.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            processNode(kids.item(i), contentCtm, inh.copy());
        }
        ctx.body.append("\\end{scope}\n");
    }

    // -----------------------------------------------------------------------
    // <image>
    // -----------------------------------------------------------------------

    /** Raster formats pdflatex can include directly. */
    private static final Map<String, String> RASTER_TYPES = Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/jpg", "jpg", "image/gif", "png");

    /**
     * Renders an {@code <image>} as an {@code \includegraphics} of a file written beside
     * the generated document.
     * <p>
     * TikZ cannot inline a bitmap, so the payload (a {@code data:} URI or a file next
     * to the SVG) is collected into {@link ConversionContext#assets} for the caller to
     * write out. Remote URLs are never fetched, so a document cannot make the
     * converter reach the network.
     * <p>
     * An {@code <image>} referencing another SVG is skipped; pdflatex cannot include
     * one, and converting it recursively is not implemented.
     */
    private void processImage(Element el, double[] ctm, InheritedAttrs inh) {
        String href = el.getAttribute("xlink:href");
        if (href.isEmpty()) href = el.getAttribute("href");
        href = href.trim();
        if (href.isEmpty()) return;

        double x = parseDouble(el.getAttribute("x"), 0);
        double y = parseDouble(el.getAttribute("y"), 0);
        double w = parseDouble(el.getAttribute("width"), 0);
        double h = parseDouble(el.getAttribute("height"), 0);
        if (w <= 0 || h <= 0) return;   // SVG renders nothing for a zero-sized image

        byte[] payload;
        String extension;
        if (href.startsWith("data:")) {
            int comma = href.indexOf(',');
            if (comma < 0) return;
            String header = href.substring(5, comma).toLowerCase();
            if (!header.contains(";base64")) return;   // only base64 payloads are supported
            extension = RASTER_TYPES.get(header.split(";")[0].trim());
            if (extension == null) return;
            try {
                payload = Base64.getMimeDecoder().decode(href.substring(comma + 1));
            } catch (IllegalArgumentException malformed) {
                return;
            }
        } else {
            payload = readLocalImage(href);
            if (payload == null) return;
            extension = extensionOf(href);
            if (extension == null) return;
        }

        String name = String.format("image-%03d.%s", ctx.assets.size() + 1, extension);
        ctx.assets.put(name, payload);

        double[] elemMtx = parseTransformMtx(el.getAttribute("transform"));
        double[] imgCtm  = composeMtx(ctm, elemMtx);
        double scale     = scaleApprox(imgCtm);
        double[] centre  = applyMtxAbs(imgCtm, x + w / 2, y + h / 2);

        // preserveAspectRatio defaults to "meet", which letterboxes the image inside the
        // box; only "none" is allowed to distort it.
        boolean stretch = el.getAttribute("preserveAspectRatio").contains("none");
        String sizing = String.format(Locale.US,
                stretch ? "width=%.4fcm, height=%.4fcm" : "width=%.4fcm, height=%.4fcm, keepaspectratio",
                w * scale * PX_TO_CM, h * scale * PX_TO_CM);

        List<String> opts = new ArrayList<>();
        opts.add("inner sep=0pt");
        opts.add("anchor=center");
        if (hasRotation(imgCtm)) {
            opts.add(String.format(Locale.US, "rotate=%.2f", -rotationDeg(imgCtm)));
        }
        double opacity = parseOpacity(el);
        if (opacity >= 0 && opacity < 1) {
            opts.add(String.format(Locale.US, "opacity=%.2f", opacity));
        }

        ctx.body.append(String.format(Locale.US,
                "\\node[%s] at (%.4f, %.4f) {\\includegraphics[%s]{%s}};%n",
                String.join(", ", opts), ctx.toX(centre[0]), ctx.toY(centre[1]), sizing, name));
    }

    /** Reads an image referenced relative to the document, or null if it is unavailable. */
    private byte[] readLocalImage(String href) {
        if (ctx.baseDir == null) return null;
        // Anything with a scheme is remote or opaque; only plain relative paths are read.
        if (href.matches("(?i)[a-z][a-z0-9+.-]*:.*")) return null;
        if (href.startsWith("#")) return null;
        try {
            java.nio.file.Path file = ctx.baseDir.resolve(href).normalize();
            if (!java.nio.file.Files.isRegularFile(file)) return null;
            return java.nio.file.Files.readAllBytes(file);
        } catch (Exception unavailable) {
            return null;
        }
    }

    /** pdflatex-includable extension for a file name, or null when it is not a raster. */
    private static String extensionOf(String href) {
        String path = href.split("[?#]")[0].toLowerCase();
        int dot = path.lastIndexOf('.');
        if (dot < 0) return null;
        return switch (path.substring(dot + 1)) {
            case "png", "gif" -> "png";
            case "jpg", "jpeg" -> "jpg";
            case "pdf" -> "pdf";
            default -> null;      // .svg and friends: pdflatex cannot include them
        };
    }

    // -----------------------------------------------------------------------
    // External document references
    // -----------------------------------------------------------------------

    /**
     * Resolves {@code <use href="other.svg#id">} against a file beside the document.
     * <p>
     * The referenced document is parsed once and its definitions (ids, gradients,
     * colours and stylesheet) are folded into the conversion's own registries, so the
     * borrowed element renders with the paint servers it was written against rather
     * than falling back to flat black. Local definitions win a name collision, since
     * the referencing document is the one being converted.
     * <p>
     * Only relative paths are followed. Anything carrying a scheme is remote or
     * opaque and is refused, for the same reason {@code <image>} refuses it.
     *
     * @return the referenced element, the document root when no fragment is given, or
     *         null when the reference cannot be resolved
     */
    private Element resolveExternalTarget(String href) {
        if (ctx.baseDir == null) return null;
        if (href.matches("(?i)[a-z][a-z0-9+.-]*:.*")) return null;

        int hash = href.indexOf('#');
        String path = hash < 0 ? href : href.substring(0, hash);
        String fragment = hash < 0 ? "" : href.substring(hash + 1);
        if (path.isEmpty()) return null;

        org.w3c.dom.Document doc = externalDocuments.get(path);
        if (doc == null) {
            if (externalDocuments.containsKey(path)) return null;   // known-bad, cached
            doc = loadExternalDocument(path);
            externalDocuments.put(path, doc);
            if (doc != null) adoptExternalDefinitions(doc.getDocumentElement());
        }
        if (doc == null) return null;

        if (fragment.isEmpty()) return doc.getDocumentElement();
        Element found = findById(doc.getDocumentElement(), fragment);
        return found;
    }

    private org.w3c.dom.Document loadExternalDocument(String path) {
        try {
            java.nio.file.Path file = ctx.baseDir.resolve(path).normalize();
            if (!java.nio.file.Files.isRegularFile(file)) return null;
            javax.xml.parsers.DocumentBuilderFactory factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return factory.newDocumentBuilder().parse(file.toFile());
        } catch (Exception unavailable) {
            return null;
        }
    }

    /**
     * Folds an external document's definitions into this conversion so its content can
     * resolve its own references.
     */
    private void adoptExternalDefinitions(Element externalRoot) {
        StyleSheet.collect(externalRoot).applyTo(externalRoot);
        indexIds(externalRoot);
        gradients.collectGradientStops();
        ctx.colors.collectColors(externalRoot);
    }

    private void indexIds(Node node) {
        if (node instanceof Element el) {
            String id = el.getAttribute("id");
            // putIfAbsent: a local definition of the same name takes precedence.
            if (!id.isEmpty()) ctx.defsMap.putIfAbsent(id, el);
        }
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) indexIds(kids.item(i));
    }

    private static Element findById(Node node, String id) {
        if (node instanceof Element el && id.equals(el.getAttribute("id"))) return el;
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Element found = findById(kids.item(i), id);
            if (found != null) return found;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // clip-path
    // -----------------------------------------------------------------------

    /**
     * Opens a TikZ scope clipped to the geometry of a referenced {@code <clipPath>}.
     * <p>
     * All children are unioned into a single TikZ path, as SVG defines a clipping
     * path made of several shapes. TikZ intersects successive {@code \clip}
     * commands, so they have to go into one.
     *
     * @param ctm the referencing element's own user space, which
     *            {@code clipPathUnits="userSpaceOnUse"} (the default) refers to
     * @return true if a scope was opened and the caller must close it
     */
    private boolean openClipScope(Element referencing, String clipRef, double[] ctm) {
        if (clipRef == null) return false;
        Matcher ref = Pattern.compile("url\\(#([^)]+)\\)").matcher(clipRef);
        if (!ref.find()) return false;
        Element clipPath = ctx.defsMap.get(ref.group(1));
        if (clipPath == null) return false;
        if (!"clippath".equals(clipPath.getTagName().replaceFirst(".*:", "").toLowerCase())) return false;

        // clipPathUnits="objectBoundingBox" makes the geometry fractions of the
        // referencing element's bounding box, so the unit square maps onto that box.
        // Reading those fractions as user units would put the whole clip in a sub-pixel
        // region near the origin and erase the element, so an unmeasurable bounding box
        // declines the clip.
        double[] effectiveCtm = ctm;
        if ("objectBoundingBox".equals(clipPath.getAttribute("clipPathUnits"))) {
            double[] box = boundingBoxOf(referencing);
            if (box == null || box[2] <= 0 || box[3] <= 0) return false;
            effectiveCtm = composeMtx(ctm, new double[]{box[2], 0, 0, box[3], box[0], box[1]});
        }

        String clipTikz = buildClipGeometry(clipPath, effectiveCtm);
        if (clipTikz == null) return false;

        // A clipping path accepts no options of its own, so the fill rule that decides
        // what "inside" means has to be set on the surrounding scope.
        ctx.body.append(isEvenOdd(clipPath) ? "\\begin{scope}[even odd rule]\n" : "\\begin{scope}\n");
        ctx.body.append("\\clip ").append(clipTikz).append(";\n");
        return true;
    }

    /** Unions every drawable child of a clipPath into one TikZ path, or null if empty. */
    private String buildClipGeometry(Element clipPath, double[] ctm) {
        double[] clipCtm = composeMtx(ctm, parseTransformMtx(clipPath.getAttribute("transform")));
        StringBuilder out = new StringBuilder();
        NodeList kids = clipPath.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element child)) continue;
            Element shape = child;
            // A clipPath may reference its geometry rather than holding it.
            if ("use".equals(shape.getTagName().replaceFirst(".*:", "").toLowerCase())) {
                String href = shape.getAttribute("xlink:href");
                if (href.isEmpty()) href = shape.getAttribute("href");
                if (!href.startsWith("#")) continue;
                Element target = ctx.defsMap.get(href.substring(1));
                if (target == null) continue;
                shape = target;
            }
            String d = shapeAsPathData(shape);
            if (d == null) continue;
            double[] shapeCtm = composeMtx(clipCtm, parseTransformMtx(shape.getAttribute("transform")));
            String tikz = paths.buildTikzPath(d, shapeCtm);
            if (tikz.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(tikz);
        }
        return out.length() == 0 ? null : out.toString();
    }

    /**
     * Expresses a basic shape as path data, so one code path handles every kind of
     * clipping geometry. Returns null for anything that encloses no area.
     */
    private static String shapeAsPathData(Element el) {
        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
        switch (tag) {
            case "path" -> {
                String d = el.getAttribute("d").trim();
                return d.isEmpty() ? null : d;
            }
            case "rect" -> {
                double x = parseDouble(el.getAttribute("x"), 0);
                double y = parseDouble(el.getAttribute("y"), 0);
                double w = parseDouble(el.getAttribute("width"),  0);
                double h = parseDouble(el.getAttribute("height"), 0);
                if (w <= 0 || h <= 0) return null;
                double rx = parseDouble(el.getAttribute("rx"), -1);
                double ry = parseDouble(el.getAttribute("ry"), -1);
                if (rx < 0 && ry < 0) { rx = 0; ry = 0; }
                else if (rx < 0) rx = ry;
                else if (ry < 0) ry = rx;
                rx = Math.min(rx, w / 2);
                ry = Math.min(ry, h / 2);
                if (rx <= 0 || ry <= 0) {
                    return String.format(Locale.US, "M %f %f H %f V %f H %f Z", x, y, x + w, y + h, x);
                }
                return String.format(Locale.US,
                        "M %f %f H %f A %f %f 0 0 1 %f %f V %f A %f %f 0 0 1 %f %f "
                                + "H %f A %f %f 0 0 1 %f %f V %f A %f %f 0 0 1 %f %f Z",
                        x + rx, y, x + w - rx,
                        rx, ry, x + w, y + ry,
                        y + h - ry, rx, ry, x + w - rx, y + h,
                        x + rx, rx, ry, x, y + h - ry,
                        y + ry, rx, ry, x + rx, y);
            }
            case "circle" -> {
                double cx = parseDouble(el.getAttribute("cx"), 0);
                double cy = parseDouble(el.getAttribute("cy"), 0);
                double r  = parseDouble(el.getAttribute("r"),  0);
                return r <= 0 ? null : ellipsePathData(cx, cy, r, r);
            }
            case "ellipse" -> {
                double cx = parseDouble(el.getAttribute("cx"), 0);
                double cy = parseDouble(el.getAttribute("cy"), 0);
                double rx = parseDouble(el.getAttribute("rx"), 0);
                double ry = parseDouble(el.getAttribute("ry"), 0);
                return (rx <= 0 || ry <= 0) ? null : ellipsePathData(cx, cy, rx, ry);
            }
            case "polygon", "polyline" -> {
                double[] pts = parseNumbers(el.getAttribute("points"));
                if (pts.length < 6) return null;   // fewer than three points enclose nothing
                StringBuilder d = new StringBuilder();
                for (int i = 0; i + 1 < pts.length; i += 2) {
                    d.append(i == 0 ? "M " : "L ").append(pts[i]).append(' ').append(pts[i + 1]).append(' ');
                }
                return d.append('Z').toString();
            }
            default -> {
                return null;   // text and everything else: not supported as clip geometry
            }
        }
    }

    /**
     * Bounding box of an element in its own user space, or null when it cannot be
     * measured. Only needed to resolve {@code objectBoundingBox} units.
     * <p>
     * A path's box is taken from the extremes of its coordinates, control points
     * included, so it is an over-estimate rather than the tight box. Containers and text
     * return null: measuring them means laying out their whole subtree, and a wrong box
     * here deletes content.
     */
    private static double[] boundingBoxOf(Element el) {
        String tag = el.getTagName().replaceFirst(".*:", "").toLowerCase();
        switch (tag) {
            case "rect", "image" -> {
                return new double[]{
                        parseDouble(el.getAttribute("x"), 0), parseDouble(el.getAttribute("y"), 0),
                        parseDouble(el.getAttribute("width"), 0), parseDouble(el.getAttribute("height"), 0)};
            }
            case "circle" -> {
                double cx = parseDouble(el.getAttribute("cx"), 0);
                double cy = parseDouble(el.getAttribute("cy"), 0);
                double r  = parseDouble(el.getAttribute("r"), 0);
                return new double[]{cx - r, cy - r, 2 * r, 2 * r};
            }
            case "ellipse" -> {
                double cx = parseDouble(el.getAttribute("cx"), 0);
                double cy = parseDouble(el.getAttribute("cy"), 0);
                double rx = parseDouble(el.getAttribute("rx"), 0);
                double ry = parseDouble(el.getAttribute("ry"), 0);
                return new double[]{cx - rx, cy - ry, 2 * rx, 2 * ry};
            }
            case "line" -> {
                return boxOfPoints(new double[]{
                        parseDouble(el.getAttribute("x1"), 0), parseDouble(el.getAttribute("y1"), 0),
                        parseDouble(el.getAttribute("x2"), 0), parseDouble(el.getAttribute("y2"), 0)});
            }
            case "polygon", "polyline" -> {
                return boxOfPoints(parseNumbers(el.getAttribute("points")));
            }
            case "path" -> {
                // Arc parameters are radii and flags, not coordinates, so a path using
                // them would give a nonsense box.
                String d = el.getAttribute("d");
                if (d.matches("(?s).*[Aa].*")) return null;
                return boxOfPoints(parseNumbers(d));
            }
            default -> {
                return null;
            }
        }
    }

    /** Bounding box of a flat {x, y, x, y, ...} coordinate list. */
    private static double[] boxOfPoints(double[] coords) {
        if (coords.length < 4 || coords.length % 2 != 0) return null;
        double minX = coords[0], maxX = coords[0], minY = coords[1], maxY = coords[1];
        for (int i = 0; i + 1 < coords.length; i += 2) {
            minX = Math.min(minX, coords[i]);   maxX = Math.max(maxX, coords[i]);
            minY = Math.min(minY, coords[i + 1]); maxY = Math.max(maxY, coords[i + 1]);
        }
        return new double[]{minX, minY, maxX - minX, maxY - minY};
    }

    /** An ellipse as two half arcs, so the arc code handles it like any other path. */
    private static String ellipsePathData(double cx, double cy, double rx, double ry) {
        return String.format(Locale.US,
                "M %f %f A %f %f 0 1 0 %f %f A %f %f 0 1 0 %f %f Z",
                cx - rx, cy, rx, ry, cx + rx, cy, rx, ry, cx - rx, cy);
    }

    // -----------------------------------------------------------------------
    // mask
    // -----------------------------------------------------------------------

    /** Below this luminance a mask shape is a hole rather than a window. */
    private static final double MASK_DARK_THRESHOLD = 0.5;

    /**
     * Approximates a {@code <mask>} and opens a scope for it.
     * <p>
     * A mask multiplies alpha by the luminance of its content, which TikZ cannot do
     * directly. Two approximations cover what documents do: solid shapes (the common
     * white rectangle with a black shape punched out) become a clip, where light shapes
     * are windows, dark ones are holes, and the even-odd rule turns a dark shape inside
     * a light one into a hole; a mask containing a gradient cannot be a clip, so the
     * scope takes a uniform opacity of the mask's mean luminance instead, which gets a
     * fade right in weight if not in detail.
     * <p>
     * As with clipping, an unresolvable mask opens no scope: drawing the content
     * unmasked loses less than erasing it.
     */
    private boolean openMaskScope(Element referencing, String maskRef, double[] ctm) {
        if (maskRef == null) return false;
        Matcher ref = Pattern.compile("url\\(#([^)]+)\\)").matcher(maskRef);
        if (!ref.find()) return false;
        Element mask = ctx.defsMap.get(ref.group(1));
        if (mask == null) return false;
        if (!"mask".equals(mask.getTagName().replaceFirst(".*:", "").toLowerCase())) return false;

        List<Element> shapes = new ArrayList<>();
        NodeList kids = mask.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element child && shapeAsPathData(child) != null) {
                shapes.add(child);
            }
        }
        if (shapes.isEmpty()) return false;

        if (usesGradient(shapes)) {
            double luminance = meanLuminance(shapes);
            ctx.body.append("\\begin{scope}\n");
            ctx.body.append(String.format(Locale.US, "\\pgfsetfillopacity{%.3f}"
                    + "\\pgfsetstrokeopacity{%.3f}\n", luminance, luminance));
            return true;
        }

        // Windows first, holes after: with the even-odd rule a hole inside a window
        // cancels it, the meaning of a black shape on a white one.
        StringBuilder geometry = new StringBuilder();
        for (Element shape : shapes) {
            if (luminanceOf(shape) < MASK_DARK_THRESHOLD) continue;
            appendShapeGeometry(geometry, shape, ctm);
        }
        if (geometry.length() == 0) return false;   // an all-dark mask hides everything
        for (Element shape : shapes) {
            if (luminanceOf(shape) >= MASK_DARK_THRESHOLD) continue;
            appendShapeGeometry(geometry, shape, ctm);
        }

        ctx.body.append("\\begin{scope}[even odd rule]\n");
        ctx.body.append("\\clip ").append(geometry).append(";\n");
        return true;
    }

    private void appendShapeGeometry(StringBuilder out, Element shape, double[] ctm) {
        String d = shapeAsPathData(shape);
        if (d == null) return;
        String tikz = paths.buildTikzPath(d,
                composeMtx(ctm, parseTransformMtx(shape.getAttribute("transform"))));
        if (tikz.isEmpty()) return;
        if (out.length() > 0) out.append(' ');
        out.append(tikz);
    }

    private static boolean usesGradient(List<Element> shapes) {
        for (Element shape : shapes) {
            String fill = getStyleOrAttr(shape, "fill");
            if (fill != null && fill.startsWith("url(")) return true;
        }
        return false;
    }

    /** Perceptual luminance of a mask shape's fill, 0 (hides) to 1 (shows). */
    private double luminanceOf(Element shape) {
        String fill = getStyleOrAttr(shape, "fill");
        if (fill == null || fill.isBlank()) return 1.0;
        if (fill.equals("none")) return 0.0;
        if (fill.startsWith("url(")) return gradientLuminance(fill);
        return luminanceOfHex(ctx.colors.resolveColorHex(fill));
    }

    /** Mean luminance of a gradient's stops. */
    private double gradientLuminance(String fillRef) {
        Matcher ref = Pattern.compile("url\\(#([^)]+)\\)").matcher(fillRef);
        if (!ref.find()) return 1.0;
        List<GradientHandler.GradStop> stops = ctx.gradStops.get(ref.group(1));
        if (stops == null || stops.isEmpty()) return 1.0;
        double total = 0;
        for (GradientHandler.GradStop stop : stops) {
            total += luminanceOfHex(stop.hex) * stop.opacity;
        }
        return total / stops.size();
    }

    private static double luminanceOfHex(String hex) {
        int[] rgb = ColorManager.hexToRgb(hex);
        return (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255.0;
    }

    private double meanLuminance(List<Element> shapes) {
        double total = 0;
        for (Element shape : shapes) total += luminanceOf(shape);
        return Math.max(0.05, Math.min(1.0, total / shapes.size()));
    }

    // -----------------------------------------------------------------------
    // <switch>
    // -----------------------------------------------------------------------

    /**
     * Renders the first child of a {@code <switch>} whose conditions all pass, and only
     * that one.
     */
    private void processSwitch(Element el, double[] ctm, InheritedAttrs inh) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (!(kids.item(i) instanceof Element child)) continue;
            if (!isSwitchCandidate(child)) continue;
            if (!conditionsPass(child)) continue;
            processNode(child, ctm, inh);
            return;
        }
    }

    /** Elements a {@code <switch>} may choose between; {@code <desc>} and friends cannot. */
    private static final Set<String> SWITCH_CANDIDATES = Set.of(
            "a", "foreignobject", "g", "image", "svg", "switch", "text", "use",
            "circle", "ellipse", "line", "path", "polygon", "polyline", "rect");

    private static boolean isSwitchCandidate(Element el) {
        return SWITCH_CANDIDATES.contains(el.getTagName().replaceFirst(".*:", "").toLowerCase());
    }

    /**
     * Evaluates the three conditional-processing attributes.
     * <p>
     * An absent attribute passes. {@code requiredExtensions} names a foreign extension
     * and none is implemented, so any non-empty value fails, as in a browser.
     * {@code requiredFeatures} is accepted for the standard SVG feature strings, so
     * the same branch is chosen as in the browser the author previewed in.
     */
    private static boolean conditionsPass(Element el) {
        if (el.hasAttribute("requiredExtensions")
                && !el.getAttribute("requiredExtensions").isBlank()) {
            return false;
        }
        if (el.hasAttribute("requiredFeatures")) {
            String value = el.getAttribute("requiredFeatures").trim();
            if (value.isEmpty()) return false;   // an empty list is defined as false
            for (String feature : value.split("\\s+")) {
                if (!feature.startsWith("http://www.w3.org/TR/SVG11/feature#")
                        && !feature.startsWith("org.w3c.svg")
                        && !feature.startsWith("org.w3c.dom.svg")) {
                    return false;
                }
            }
        }
        if (el.hasAttribute("systemLanguage")) {
            String value = el.getAttribute("systemLanguage").trim();
            if (value.isEmpty()) return false;
            boolean matches = false;
            for (String tag : value.split("\\s*,\\s*")) {
                String primary = tag.trim().toLowerCase().split("-")[0];
                if (primary.equals(DOCUMENT_LANGUAGE)) { matches = true; break; }
            }
            if (!matches) return false;
        }
        return true;
    }

    /**
     * Language assumed when resolving {@code systemLanguage}. Batik and browsers
     * default to English unless told otherwise.
     */
    private static final String DOCUMENT_LANGUAGE = "en";

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
        // SVG 1.1 section 9.2 clamps each radius to half its own side, so a rect asking
        // for rx=200 on an 80-wide box is drawn with rx=40 rather than a distorted or
        // self-intersecting outline.
        lrx = Math.min(lrx, lw / 2.0);
        lry = Math.min(lry, lh / 2.0);

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
            // TikZ has one radius per corner where SVG has two, so the two are
            // averaged. TikZ handles an oversized radius itself, so the average is not
            // clamped again.
            double rUser = (lrx + lry) / 2.0;
            double rCm = sa * rUser * PX_TO_CM;
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
        paintPathData(el, d, ctm, inh);
    }

    /**
     * Paints path data on behalf of {@code el}, applying that element's fill, stroke,
     * gradient, markers, blur, fill-rule and opacity.
     * Split out of {@link #processPath} so {@code <polyline>} and {@code <polygon>}
     * can hand it an equivalent {@code d} string instead of duplicating all of that.
     *
     * @param d path data in SVG {@code d} syntax, already known to be non-empty
     */
    private void paintPathData(Element el, String d, double[] ctm, InheritedAttrs inh) {
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
        // must NOT get their own blur halos; the group already handles the
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
     *   <li>Shadow (dark fill): edge-softening with concentric halo layers
     *       at Gaussian-profile opacity.  The core stays at moderate opacity.</li>
     *   <li>Glow (light fill, e.g. white stars): Gaussian blur dilutes
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
            // Render at reduced opacity, visible but subtle.
            double glowOp = opMul * 0.25;
            if (glowOp < 0.003) return;      // invisible, skip entirely
            String fillOpt = (gradOpts != null) ? gradOpts : "fill=" + fillColor;
            ctx.body.append(String.format(Locale.US,
                    "\\path[%s, opacity=%.4f] %s;\n", fillOpt, glowOp, pathStr));
            return;
        }

        // Shadow: concentric halo layers create soft edge fade.
        // 10 layers from 1.5 sigma (outermost) down to 0.15 sigma, tight Gaussian falloff.
        int numLayers = 10;
        double sigmaMax = 1.5;
        double sigmaMin = 0.15;

        for (int i = 0; i < numLayers; i++) {
            double t = (double) i / (numLayers - 1);
            double sigma = sigmaMax + t * (sigmaMin - sigmaMax);
            double lwCm = sigma * blurCm;
            // Tight Gaussian profile (sigma scale 0.7) for rapid falloff
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
        // Unknown colour: assume dark (shadow)
        return false;
    }

    // -----------------------------------------------------------------------
    // <polyline> and <polygon>
    // -----------------------------------------------------------------------

    /**
     * Renders a {@code <polyline>} or {@code <polygon>} by translating its
     * {@code points} list into path data and reusing {@link #paintPathData}.
     * <p>
     * SVG defines both in terms of the equivalent path, so fill, stroke, markers,
     * gradients and fill-rule behave as they do for {@code <path>}. A
     * {@code <polyline>} is filled like a closed shape even though it is stroked open,
     * and both default to a solid black fill when no fill is given; emitting no
     * {@code Z} for a polyline gives that, since TikZ closes the path for filling but
     * not for stroking.
     *
     * @param close true for {@code <polygon>}, which appends the closing segment
     */
    private void processPoly(Element el, double[] ctm, InheritedAttrs inh, boolean close) {
        // parseNumbers accepts mixed comma and space separators ("20,90 60 60"),
        // which are legal in one attribute.
        double[] pts = parseNumbers(el.getAttribute("points"));

        // An odd trailing coordinate is an error; SVG says render up to the last
        // complete pair. Fewer than two points draws nothing at all.
        int pairs = pts.length / 2;
        if (pairs < 2) return;

        StringBuilder d = new StringBuilder(pairs * 16);
        for (int i = 0; i < pairs; i++) {
            d.append(i == 0 ? 'M' : 'L').append(' ')
             .append(pts[2 * i]).append(' ').append(pts[2 * i + 1]).append(' ');
        }
        if (close) d.append('Z');

        paintPathData(el, d.toString(), ctm, inh);
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
