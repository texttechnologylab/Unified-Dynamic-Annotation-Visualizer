package org.texttechnologylab.udav.widgets.svgtolatex;

/**
 * Container for inherited SVG presentation attributes that propagate
 * from parent to child elements during tree traversal.
 * <p>
 * Only the subset of attributes used by the converter is tracked.
 */
public class InheritedAttrs {

    /**
     * SVG fill value (hex, named colour, "none", or url(...)).
     * The initial value is black, the SVG default for {@code fill}.
     * <p>
     * Always a resolved paint: {@code currentColor} is substituted where the fill is
     * specified, not where it is painted, because SVG resolves it against the
     * {@code color} in force at the point of specification. So
     * {@code <g fill="currentColor" color="lime"><rect color="red"/></g>} is green.
     */
    public String fill = ColorManager.CURRENT_COLOR_HEX;

    /** SVG stroke value. Default per SVG spec is "none". */
    public String stroke = "none";

    /** SVG text-anchor: "start", "middle", or "end". */
    public String textAnchor = "start";

    /**
     * Font size in SVG user-units (px).
     * Browser default for SVG text is 16 px.
     */
    public double fontSize = 16;

    /** CSS font-family list. Browsers default SVG text to sans-serif. */
    public String fontFamily = "sans-serif";

    /** Font weight: "normal", "bold", "bolder", "lighter", or a numeric string. */
    public String fontWeight = "normal";

    /**
     * Inherited stroke-width in SVG user-units.
     * -1 means "not set" (fallback to SVG default of 1 px).
     */
    public double strokeWidth = -1;

    /**
     * LaTeX text rendering mode propagated from draw.io {@code data-texmode}
     * (or Inkscape {@code texmode}) attributes on ancestor {@code <g>} elements.
     * <ul>
     *   <li>{@code ""}     — normal: escape special characters (default)</li>
     *   <li>{@code "raw"}  — pass text verbatim as LaTeX source</li>
     *   <li>{@code "math"} — wrap text in {@code $…$} without escaping</li>
     * </ul>
     */
    public String texMode = "";

    /**
     * When {@code true}, ALL strokes are suppressed — even those explicitly
     * set on child elements via attributes or {@code style}.  Used inside
     * blurred groups so internal detail (handle wraps, guard outlines, etc.)
     * merges into a uniform silhouette.
     */
    public boolean forceNoStroke = false;

    /**
     * When non-null, overrides every child element's fill with this colour
     * value.  Used inside blurred groups so overlapping shapes of different
     * colours merge into a single-colour shadow silhouette.
     */
    public String forceFill = null;

    /**
     * The CSS {@code color} property as a hex value, the value
     * {@code currentColor} resolves to.
     * <p>
     * Inherited, and meaningful on the element that sets it as well, including one
     * that says {@code fill="currentColor" color="..."}; it is therefore updated
     * before the paint is resolved.
     */
    public String color = ColorManager.CURRENT_COLOR_HEX;

    /**
     * CSS {@code visibility}: "visible", "hidden" or "collapse".
     * <p>
     * Inherited, unlike {@code display}, and a descendant can set it back to
     * visible. That is why it travels here while {@code display:none} is handled by
     * not descending at all.
     */
    public String visibility = "visible";

    /** Create a shallow copy with all fields duplicated. */
    public InheritedAttrs copy() {
        InheritedAttrs c = new InheritedAttrs();
        c.fill          = fill;
        c.stroke        = stroke;
        c.textAnchor    = textAnchor;
        c.fontSize      = fontSize;
        c.fontFamily    = fontFamily;
        c.fontWeight    = fontWeight;
        c.visibility    = visibility;
        c.color         = color;
        c.strokeWidth   = strokeWidth;
        c.texMode       = texMode;
        c.forceNoStroke = forceNoStroke;
        c.forceFill     = forceFill;
        return c;
    }
}
