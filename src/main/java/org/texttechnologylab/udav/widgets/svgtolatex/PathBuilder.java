package org.texttechnologylab.udav.widgets.svgtolatex;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.texttechnologylab.udav.widgets.svgtolatex.ParseUtils.*;
import static org.texttechnologylab.udav.widgets.svgtolatex.TransformUtils.*;

/**
 * Converts SVG path {@code d} strings into TikZ path strings.
 * <p>
 * Supported commands: M m  L l  H h  V v  Z z  C c  S s  A a
 */
public class PathBuilder {

    private final ConversionContext ctx;

    public PathBuilder(ConversionContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Converts an SVG path "d" string into a TikZ path string.
     * All coordinates are in the element's local space; the CTM maps them to
     * world SVG space, then toX/toY converts to TikZ space.
     */
    public String buildTikzPath(String d, double[] ctm) {
        StringBuilder sb = new StringBuilder();
        double cx = 0, cy = 0;
        double startX = 0, startY = 0;
        double lastCtrlX = 0, lastCtrlY = 0;
        boolean lastWasCubic = false;
        boolean firstSeg = true;

        Matcher m = Pattern.compile("([MmLlHhVvZzCcSsAa])([^MmLlHhVvZzCcSsAa]*)").matcher(d);
        while (m.find()) {
            char cmd  = m.group(1).charAt(0);
            double[] a = parseNumbers(m.group(2));

            switch (cmd) {
                case 'M': {
                    cx = a[0]; cy = a[1];
                    startX = cx; startY = cy;
                    if (!firstSeg) sb.append(" ");
                    sb.append(tikzPt(ctm, cx, cy));
                    firstSeg = false;
                    for (int i = 2; i + 1 < a.length; i += 2) {
                        cx = a[i]; cy = a[i+1];
                        sb.append(" -- ").append(tikzPt(ctm, cx, cy));
                    }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'm': {
                    cx += a[0]; cy += a[1];
                    startX = cx; startY = cy;
                    if (!firstSeg) sb.append(" ");
                    sb.append(tikzPt(ctm, cx, cy));
                    firstSeg = false;
                    for (int i = 2; i + 1 < a.length; i += 2) {
                        cx += a[i]; cy += a[i+1];
                        sb.append(" -- ").append(tikzPt(ctm, cx, cy));
                    }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'L': {
                    for (int i = 0; i + 1 < a.length; i += 2) {
                        cx = a[i]; cy = a[i+1];
                        sb.append(" -- ").append(tikzPt(ctm, cx, cy));
                    }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'l': {
                    for (int i = 0; i + 1 < a.length; i += 2) {
                        cx += a[i]; cy += a[i+1];
                        sb.append(" -- ").append(tikzPt(ctm, cx, cy));
                    }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'H': {
                    for (double v : a) { cx = v; sb.append(" -- ").append(tikzPt(ctm, cx, cy)); }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'h': {
                    for (double v : a) { cx += v; sb.append(" -- ").append(tikzPt(ctm, cx, cy)); }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'V': {
                    for (double v : a) { cy = v; sb.append(" -- ").append(tikzPt(ctm, cx, cy)); }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'v': {
                    for (double v : a) { cy += v; sb.append(" -- ").append(tikzPt(ctm, cx, cy)); }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'Z': case 'z': {
                    sb.append(" -- cycle");
                    cx = startX; cy = startY;
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'C': {
                    for (int i = 0; i + 5 < a.length; i += 6) {
                        double x1 = a[i], y1 = a[i+1];
                        double x2 = a[i+2], y2 = a[i+3];
                        double x  = a[i+4], y  = a[i+5];
                        double[] w1 = applyMtxAbs(ctm,x1,y1), w2 = applyMtxAbs(ctm,x2,y2), we = applyMtxAbs(ctm,x,y);
                        sb.append(String.format(Locale.US,
                                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                                ctx.toX(w1[0]),ctx.toY(w1[1]), ctx.toX(w2[0]),ctx.toY(w2[1]), ctx.toX(we[0]),ctx.toY(we[1])));
                        lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y;
                    }
                    lastWasCubic = true;
                    break;
                }
                case 'c': {
                    for (int i = 0; i + 5 < a.length; i += 6) {
                        double x1 = cx+a[i], y1 = cy+a[i+1];
                        double x2 = cx+a[i+2], y2 = cy+a[i+3];
                        double x  = cx+a[i+4], y  = cy+a[i+5];
                        double[] w1 = applyMtxAbs(ctm,x1,y1), w2 = applyMtxAbs(ctm,x2,y2), we = applyMtxAbs(ctm,x,y);
                        sb.append(String.format(Locale.US,
                                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                                ctx.toX(w1[0]),ctx.toY(w1[1]), ctx.toX(w2[0]),ctx.toY(w2[1]), ctx.toX(we[0]),ctx.toY(we[1])));
                        lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y;
                    }
                    lastWasCubic = true;
                    break;
                }
                case 'S': {
                    for (int i = 0; i + 3 < a.length; i += 4) {
                        double x1 = lastWasCubic ? 2*cx - lastCtrlX : cx;
                        double y1 = lastWasCubic ? 2*cy - lastCtrlY : cy;
                        double x2 = a[i], y2 = a[i+1];
                        double x  = a[i+2], y = a[i+3];
                        double[] w1 = applyMtxAbs(ctm,x1,y1), w2 = applyMtxAbs(ctm,x2,y2), we = applyMtxAbs(ctm,x,y);
                        sb.append(String.format(Locale.US,
                                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                                ctx.toX(w1[0]),ctx.toY(w1[1]), ctx.toX(w2[0]),ctx.toY(w2[1]), ctx.toX(we[0]),ctx.toY(we[1])));
                        lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y; lastWasCubic = true;
                    }
                    break;
                }
                case 's': {
                    for (int i = 0; i + 3 < a.length; i += 4) {
                        double x1 = lastWasCubic ? 2*cx - lastCtrlX : cx;
                        double y1 = lastWasCubic ? 2*cy - lastCtrlY : cy;
                        double x2 = cx+a[i], y2 = cy+a[i+1];
                        double x  = cx+a[i+2], y = cy+a[i+3];
                        double[] w1 = applyMtxAbs(ctm,x1,y1), w2 = applyMtxAbs(ctm,x2,y2), we = applyMtxAbs(ctm,x,y);
                        sb.append(String.format(Locale.US,
                                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                                ctx.toX(w1[0]),ctx.toY(w1[1]), ctx.toX(w2[0]),ctx.toY(w2[1]), ctx.toX(we[0]),ctx.toY(we[1])));
                        lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y; lastWasCubic = true;
                    }
                    break;
                }
                case 'A': {
                    for (int i = 0; i + 6 < a.length; i += 7) {
                        double scaleA = scaleApprox(ctm);
                        double rx2 = scaleA * a[i], ry2 = scaleA * a[i+1];
                        boolean la = a[i+3] != 0, sw = a[i+4] != 0;
                        double ex = a[i+5], ey = a[i+6];
                        double[] ws = applyMtxAbs(ctm, cx, cy);
                        double[] we = applyMtxAbs(ctm, ex, ey);
                        sb.append(svgArcToBezier(ws[0], ws[1], rx2, ry2, a[i+2], la, sw, we[0], we[1]));
                        cx = ex; cy = ey;
                    }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
                case 'a': {
                    for (int i = 0; i + 6 < a.length; i += 7) {
                        double scaleA = scaleApprox(ctm);
                        double rx2 = scaleA * a[i], ry2 = scaleA * a[i+1];
                        boolean la = a[i+3] != 0, sw = a[i+4] != 0;
                        double ex = cx + a[i+5], ey = cy + a[i+6];
                        double[] ws = applyMtxAbs(ctm, cx, cy);
                        double[] we = applyMtxAbs(ctm, ex, ey);
                        sb.append(svgArcToBezier(ws[0], ws[1], rx2, ry2, a[i+2], la, sw, we[0], we[1]));
                        cx = ex; cy = ey;
                    }
                    lastWasCubic = false; lastCtrlX = cx; lastCtrlY = cy;
                    break;
                }
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Coordinate formatting
    // -----------------------------------------------------------------------

    private String tikzPt(double[] ctm, double lx, double ly) {
        double[] w = applyMtxAbs(ctm, lx, ly);
        return String.format(Locale.US, "(%.4f, %.4f)", ctx.toX(w[0]), ctx.toY(w[1]));
    }

    // -----------------------------------------------------------------------
    // SVG arc → cubic Bézier conversion
    // -----------------------------------------------------------------------

    private String svgArcToBezier(double x1, double y1,
                                  double rx, double ry, double xRotDeg,
                                  boolean largeArc, boolean sweep,
                                  double x2, double y2) {
        if (x1 == x2 && y1 == y2) return "";
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0) {
            return String.format(Locale.US, " -- (%.4f, %.4f)", ctx.toX(x2), ctx.toY(y2));
        }

        double phi    = Math.toRadians(xRotDeg);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx2 = (x1 - x2) / 2.0;
        double dy2 = (y1 - y2) / 2.0;
        double x1p =  cosPhi * dx2 + sinPhi * dy2;
        double y1p = -sinPhi * dx2 + cosPhi * dy2;

        double x1pSq = x1p * x1p, y1pSq = y1p * y1p;
        double rxSq  = rx * rx,    rySq  = ry * ry;
        double lambda = x1pSq / rxSq + y1pSq / rySq;
        if (lambda > 1) {
            double sqrtL = Math.sqrt(lambda);
            rx *= sqrtL; ry *= sqrtL;
            rxSq = rx * rx; rySq = ry * ry;
        }

        double num = Math.max(0, rxSq * rySq - rxSq * y1pSq - rySq * x1pSq);
        double den = rxSq * y1pSq + rySq * x1pSq;
        double sq  = (den == 0) ? 0 : Math.sqrt(num / den);
        if (largeArc == sweep) sq = -sq;
        double cxp =  sq * rx * y1p / ry;
        double cyp = -sq * ry * x1p / rx;

        double ccx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0;
        double ccy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0;

        double ux =  (x1p - cxp) / rx,  uy =  (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx,  vy = (-y1p - cyp) / ry;
        double theta1 = svgVectorAngle(1, 0, ux, uy);
        double dTheta  = svgVectorAngle(ux, uy, vx, vy);
        if (!sweep && dTheta > 0) dTheta -= 2 * Math.PI;
        if ( sweep && dTheta < 0) dTheta += 2 * Math.PI;

        int nSegs = Math.max(1, (int) Math.ceil(Math.abs(dTheta) / (Math.PI / 2)));
        double dThetaSeg = dTheta / nSegs;
        StringBuilder sb = new StringBuilder();
        double t = theta1;
        for (int i = 0; i < nSegs; i++) {
            sb.append(arcSegmentToBezier(ccx, ccy, rx, ry, cosPhi, sinPhi, t, t + dThetaSeg));
            t += dThetaSeg;
        }
        return sb.toString();
    }

    private double svgVectorAngle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double len = Math.sqrt((ux*ux + uy*uy) * (vx*vx + vy*vy));
        double a = Math.acos(Math.max(-1, Math.min(1, dot / len)));
        if (ux * vy - uy * vx < 0) a = -a;
        return a;
    }

    private String arcSegmentToBezier(double cx, double cy,
                                      double rx, double ry,
                                      double cosPhi, double sinPhi,
                                      double theta, double thetaEnd) {
        double dTheta = thetaEnd - theta;
        double alpha = (4.0 / 3.0) * Math.tan(dTheta / 4.0);

        double cosT  = Math.cos(theta),    sinT  = Math.sin(theta);
        double cosT2 = Math.cos(thetaEnd), sinT2 = Math.sin(thetaEnd);

        double ex1 = rx * cosT,  ey1 = ry * sinT;
        double ex2 = rx * cosT2, ey2 = ry * sinT2;
        double dx1 = -rx * sinT,  dy1 = ry * cosT;
        double dx2 = -rx * sinT2, dy2 = ry * cosT2;

        double[] cp1 = arcToWorld(cx, cy, cosPhi, sinPhi, ex1 + alpha * dx1, ey1 + alpha * dy1);
        double[] cp2 = arcToWorld(cx, cy, cosPhi, sinPhi, ex2 - alpha * dx2, ey2 - alpha * dy2);
        double[] ep  = arcToWorld(cx, cy, cosPhi, sinPhi, ex2, ey2);

        return String.format(Locale.US,
                ".. controls (%.4f, %.4f) and (%.4f, %.4f) .. (%.4f, %.4f)",
                this.ctx.toX(cp1[0]), this.ctx.toY(cp1[1]),
                this.ctx.toX(cp2[0]), this.ctx.toY(cp2[1]),
                this.ctx.toX(ep[0]),  this.ctx.toY(ep[1]));
    }

    private double[] arcToWorld(double cx, double cy, double cosPhi, double sinPhi,
                                double px, double py) {
        return new double[]{ cosPhi * px - sinPhi * py + cx,
                             sinPhi * px + cosPhi * py + cy };
    }
}
