package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;

import java.util.List;
import java.util.Locale;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Builds TikZ stroke-style options: line width, dash pattern, line cap/join,
 * and arrow markers.
 */
public class StrokeHelper {

    private final ConversionContext ctx;
    private final MarkerHandler markers;

    public StrokeHelper(ConversionContext ctx, MarkerHandler markers) {
        this.ctx = ctx;
        this.markers = markers;
    }

    /**
     * Collect all stroke-style options (line width, dash, cap, join, arrows)
     * into {@code opts}.
     */
    public void addStrokeOpts(List<String> opts, Element el, InheritedAttrs inh,
                              double strokeWidth, double scaleApprox) {
        // Line width
        double sw = strokeWidth < 0 ? (inh.strokeWidth < 0 ? 1.0 : inh.strokeWidth) : strokeWidth;
        opts.add(String.format(Locale.US, "line width=%.4fcm", sw * scaleApprox * PX_TO_CM));
        // Dash array
        String dash = resolveDashArray(el, scaleApprox);
        if (!dash.isEmpty()) opts.add(dash);
        // Line cap / join
        String lc = resolveLinecap(el);
        if (!lc.isEmpty()) opts.add(lc);
        String lj = resolveLinejoin(el);
        if (!lj.isEmpty()) opts.add(lj);
        // Arrows
        String arrow = markers.buildArrowSpec(el);
        if (!arrow.isEmpty()) {
            opts.add(arrow);

            // Extend the path at each end to match SVG marker extent
            String msId = MarkerHandler.markerIdFromUrl(getStyleOrAttr(el, "marker-start"));
            String meId = MarkerHandler.markerIdFromUrl(getStyleOrAttr(el, "marker-end"));
            if (msId != null) {
                double[] ext = ctx.markerExtensions.get(msId);
                if (ext != null && ext[0] > 0) {
                    double extCm = ext[0] * sw * scaleApprox * PX_TO_CM;
                    opts.add(String.format(Locale.US, "shorten <=-%.4fcm", extCm));
                }
            }
            if (meId != null) {
                double[] ext = ctx.markerExtensions.get(meId);
                if (ext != null && ext[1] > 0) {
                    double extCm = ext[1] * sw * scaleApprox * PX_TO_CM;
                    opts.add(String.format(Locale.US, "shorten >=-%.4fcm", extCm));
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Dash array
    // -----------------------------------------------------------------------

    /**
     * Convert an SVG {@code stroke-dasharray} value to a TikZ
     * {@code dash pattern} option string.
     */
    private String resolveDashArray(Element el, double scale) {
        String da = getStyleOrAttr(el, "stroke-dasharray");
        if (da == null || da.isEmpty() || "none".equalsIgnoreCase(da.trim())) return "";
        double[] values = parseNumbers(da);
        if (values.length == 0) return "";
        int len = (values.length % 2 == 0) ? values.length : values.length * 2;
        StringBuilder sb = new StringBuilder("dash pattern=");
        for (int i = 0; i < len; i++) {
            double v = values[i % values.length] * scale * PX_TO_CM;
            sb.append(i % 2 == 0 ? "on " : "off ");
            sb.append(String.format(Locale.US, "%.4fcm", v));
            if (i < len - 1) sb.append(" ");
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Line cap / join
    // -----------------------------------------------------------------------

    private String resolveLinecap(Element el) {
        String lc = getStyleOrAttr(el, "stroke-linecap");
        if (lc == null) return "";
        switch (lc.trim().toLowerCase()) {
            case "round":  return "line cap=round";
            case "square": return "line cap=rect";
            default:       return "";
        }
    }

    private String resolveLinejoin(Element el) {
        String lj = getStyleOrAttr(el, "stroke-linejoin");
        if (lj == null) return "";
        switch (lj.trim().toLowerCase()) {
            case "round": return "line join=round";
            case "bevel": return "line join=bevel";
            default:      return "";
        }
    }
}
