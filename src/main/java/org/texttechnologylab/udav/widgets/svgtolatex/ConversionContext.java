package org.texttechnologylab.udav.widgets.svgtolatex;

import org.w3c.dom.Element;
import java.nio.file.Path;
import java.util.*;

/**
 * Shared mutable state for a single SVG → LaTeX conversion pass.
 * <p>
 * This object is created once per {@link VecTikZConverter#convert(String)}
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

    /** id to Element for every element carrying an id; used by {@code <use>} and paint servers. */
    public final Map<String, Element> defsMap = new LinkedHashMap<>();

    /** gradient id → list of parsed stops (sorted by offset). */
    public final Map<String, List<GradientHandler.GradStop>> gradStops = new LinkedHashMap<>();

    /** marker id → TikZ arrow-head name. */
    public final Map<String, String> markerMap = new LinkedHashMap<>();

    /** marker id → [ext_as_start, ext_as_end] for path extension. */
    public final Map<String, double[]> markerExtensions = new LinkedHashMap<>();

    /** shading name → {@code \pgfdeclarehorizontalshading} declaration. */
    public final Map<String, String> pendingShadings = new LinkedHashMap<>();

    /**
     * Directory the SVG was loaded from, used to resolve relative {@code <image>} hrefs.
     * Null when the caller supplied only a string, in which case external references
     * are skipped.
     */
    public Path baseDir;

    /**
     * Raster files the generated document needs alongside it, keyed by file name.
     * TikZ cannot inline a bitmap, so an {@code <image>} becomes an
     * {@code \includegraphics} of a file written next to the {@code .tex}. A caller
     * that wants the images has to write them out; see
     * {@link VecTikZConverter#getAssets()}.
     */
    public final Map<String, byte[]> assets = new LinkedHashMap<>();

    /** pattern name to {@code \pgfdeclarepatternformonly} declaration for the preamble. */
    public final Map<String, String> pendingPatterns = new LinkedHashMap<>();

    /** True once a TikZ pattern is used, so the preamble loads the patterns library. */
    public boolean usesPatternLibrary = false;

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
        usesPatternLibrary = false;
        pendingPatterns.clear();
        assets.clear();
        baseDir = null;
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
