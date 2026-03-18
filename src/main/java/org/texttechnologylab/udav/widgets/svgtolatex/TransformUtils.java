package org.texttechnologylab.udav.widgets.svgtolatex;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utilities for 2D affine transform matrices.
 * <p>
 * Matrix convention: {@code [a,b,c,d,e,f]} means
 * <pre>
 *   x' = a*x + c*y + e
 *   y' = b*x + d*y + f
 * </pre>
 * This matches SVG's {@code matrix(a,b,c,d,e,f)} notation.
 */
public final class TransformUtils {

    private TransformUtils() {} // utility class

    /** 1 inch = 96 px; 1 inch = 2.54 cm → 1 px = 2.54/96 cm */
    public static final double PX_TO_CM = 2.54 / 96.0;

    // -----------------------------------------------------------------------
    // Identity / composition / application
    // -----------------------------------------------------------------------

    public static double[] identityMtx() { return new double[]{1, 0, 0, 1, 0, 0}; }

    /**
     * Compose two affine transforms: returns M1 * M2, meaning M2 is applied
     * first (child/local space), then M1 (parent/world space).
     */
    public static double[] composeMtx(double[] m1, double[] m2) {
        double a = m1[0]*m2[0] + m1[2]*m2[1];
        double b = m1[1]*m2[0] + m1[3]*m2[1];
        double c = m1[0]*m2[2] + m1[2]*m2[3];
        double d = m1[1]*m2[2] + m1[3]*m2[3];
        double e = m1[0]*m2[4] + m1[2]*m2[5] + m1[4];
        double f = m1[1]*m2[4] + m1[3]*m2[5] + m1[5];
        return new double[]{a, b, c, d, e, f};
    }

    /** Apply matrix to an absolute point. Returns {x', y'}. */
    public static double[] applyMtxAbs(double[] m, double x, double y) {
        return new double[]{m[0]*x + m[2]*y + m[4],
                            m[1]*x + m[3]*y + m[5]};
    }

    /** Apply only the linear part of a matrix to a relative delta. Returns {dx', dy'}. */
    public static double[] applyMtxLin(double[] m, double dx, double dy) {
        return new double[]{m[0]*dx + m[2]*dy, m[1]*dx + m[3]*dy};
    }

    /** Geometric-mean scale factor of the linear part. */
    public static double scaleApprox(double[] ctm) {
        return Math.sqrt(Math.abs(ctm[0]*ctm[3] - ctm[1]*ctm[2]));
    }

    /** True when the CTM has rotation (b or c component non-zero). */
    public static boolean hasRotation(double[] ctm) {
        return Math.abs(ctm[1]) > 1e-6 || Math.abs(ctm[2]) > 1e-6;
    }

    /** Rotation angle in degrees extracted from the CTM (atan2 of b/a). */
    public static double rotationDeg(double[] ctm) {
        return Math.toDegrees(Math.atan2(ctm[1], ctm[0]));
    }

    // -----------------------------------------------------------------------
    // SVG transform attribute parsing
    // -----------------------------------------------------------------------

    /**
     * Parse any SVG transform string (translate, scale, rotate, matrix, or
     * combinations thereof) into a single 6-element affine matrix.
     * Multiple transforms are composed left-to-right (first applied first).
     */
    public static double[] parseTransformMtx(String transform) {
        if (transform == null || transform.isEmpty()) return identityMtx();
        double[] result = identityMtx();
        Matcher m = Pattern.compile(
                        "(matrix|translate|scale|rotate|skewX|skewY)\\(([^)]+)\\)")
                .matcher(transform);
        while (m.find()) {
            String func = m.group(1);
            double[] n = ParseUtils.parseNumbers(m.group(2));
            double[] mtx;
            switch (func) {
                case "matrix":
                    mtx = (n.length >= 6)
                            ? new double[]{n[0],n[1],n[2],n[3],n[4],n[5]}
                            : identityMtx();
                    break;
                case "translate": {
                    double tx2 = n.length >= 1 ? n[0] : 0;
                    double ty2 = n.length >= 2 ? n[1] : 0;
                    mtx = new double[]{1, 0, 0, 1, tx2, ty2};
                    break;
                }
                case "scale": {
                    double sx = n.length >= 1 ? n[0] : 1;
                    double sy = n.length >= 2 ? n[1] : sx;
                    mtx = new double[]{sx, 0, 0, sy, 0, 0};
                    break;
                }
                case "rotate": {
                    double ang = n.length >= 1 ? Math.toRadians(n[0]) : 0;
                    double cos = Math.cos(ang), sin = Math.sin(ang);
                    if (n.length >= 3) {
                        double rcx = n[1], rcy = n[2];
                        mtx = new double[]{cos, sin, -sin, cos,
                                rcx*(1-cos)+rcy*sin, rcy*(1-cos)-rcx*sin};
                    } else {
                        mtx = new double[]{cos, sin, -sin, cos, 0, 0};
                    }
                    break;
                }
                default: mtx = identityMtx();
            }
            result = composeMtx(result, mtx);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Coordinate conversion  (SVG world → TikZ)
    // -----------------------------------------------------------------------

    /** Convert SVG world-space x to TikZ x (cm). */
    public static double toX(double svgX) {
        return svgX * PX_TO_CM;
    }

    /** Convert SVG world-space y to TikZ y (cm), flipping the y-axis. */
    public static double toY(double svgY, double svgHeight) {
        return (svgHeight - svgY) * PX_TO_CM;
    }

    /** Format one coordinate pair: local space → CTM → TikZ coordinate string. */
    public static String tikzPt(double[] ctm, double lx, double ly, double svgHeight) {
        double[] w = applyMtxAbs(ctm, lx, ly);
        return String.format(Locale.US, "(%.4f, %.4f)", toX(w[0]), toY(w[1], svgHeight));
    }
}
