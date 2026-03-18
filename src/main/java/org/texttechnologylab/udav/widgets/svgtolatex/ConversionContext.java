package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;

import java.util.*;

/**
 * Shared mutable state for a single SVG → LaTeX conversion pass.
 * <p>
 * This object is created once per {@link SvgToLaTeXConverter#convert(String)}
 * call and passed to all sub-processors so they can read and append to the
 * shared body, colour definitions, defs map, etc.
 */
public class ConversionContext {

    // -----------------------------------------------------------------------
    // SVG viewport
    // -----------------------------------------------------------------------

    /** SVG document width in user-units (px). */
    public double svgWidth;

    /** SVG document height in user-units (px). */
    public double svgHeight;

    /** Root {@code <svg>} element (used to distinguish nested SVGs). */
    public Element rootElement;

    // -----------------------------------------------------------------------
    // Shared registries
    // -----------------------------------------------------------------------

    /** Colour manager: registers and resolves hex/named/rgb colours. */
    public final ColorManager colors = new ColorManager();

    {
        // Wire the back-reference so ColorManager can sample gradients for stroke resolution
        colors.setContext(this);
    }

    /** id → [x, y, width, height] of the first {@code <rect>} child of each {@code <clipPath>}. */
    public final Map<String, double[]> clipRects = new LinkedHashMap<>();

    /** id → Element, collected from all {@code <defs>} blocks; used by {@code <use>}. */
    public final Map<String, Element> defsMap = new LinkedHashMap<>();

    /** gradient id → list of parsed stops (sorted by offset). */
    public final Map<String, List<GradientHandler.GradStop>> gradStops = new LinkedHashMap<>();

    /** marker id → TikZ arrow-head name. */
    public final Map<String, String> markerMap = new LinkedHashMap<>();

    /** marker id → [ext_as_start, ext_as_end] for path extension. */
    public final Map<String, double[]> markerExtensions = new LinkedHashMap<>();

    /** shading name → {@code \pgfdeclarehorizontalshading} declaration. */
    public final Map<String, String> pendingShadings = new LinkedHashMap<>();

    /** Extra LaTeX packages needed by the body (e.g. "dejavu"). */
    public final Set<String> pendingPackages = new LinkedHashSet<>();

    /** Accumulated TikZ body commands. */
    public final StringBuilder body = new StringBuilder();

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /** Reset all state for a fresh conversion. */
    public void clear() {
        svgWidth = 0;
        svgHeight = 0;
        rootElement = null;
        colors.clear();
        clipRects.clear();
        defsMap.clear();
        gradStops.clear();
        markerMap.clear();
        markerExtensions.clear();
        pendingShadings.clear();
        pendingPackages.clear();
        body.setLength(0);
    }

    // -----------------------------------------------------------------------
    // Coordinate convenience (delegates to TransformUtils with svgHeight)
    // -----------------------------------------------------------------------

    public double toX(double svgX) {
        return TransformUtils.toX(svgX);
    }

    public double toY(double svgY) {
        return TransformUtils.toY(svgY, svgHeight);
    }
}
