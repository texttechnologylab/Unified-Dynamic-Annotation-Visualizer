package org.texttechnologylab.udav.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cost-model coefficients that the evaluation section quotes.
 *
 * The paper states a per-widget and a per-artefact cost. Those come from a two-predictor fit,
 * because a slope against one predictor alone absorbs the other whenever they are correlated,
 * which W and the artefact count are (r = 0.74). This test feeds the published per-pipeline
 * medians (the three JVM runs of 2026-10-31 pooled, 45 runs per condition) back through the
 * harness's own solver, so the numbers in the paper cannot drift away from the code that
 * produces them.
 */
class CostModelTest {

    /** The workload table's SVG column: {W, artefacts actually returned, median wall time in ms}. */
    private static final List<double[]> SVG = List.of(
            new double[]{5, 3, 395},     new double[]{6, 7, 416},
            new double[]{8, 6, 463},     new double[]{10, 19, 511},
            new double[]{14, 43, 625},   new double[]{18, 59, 697},
            new double[]{22, 34, 674},   new double[]{20, 92, 811},
            new double[]{38, 168, 1285}, new double[]{32, 200, 1358},
            new double[]{46, 56, 945},   new double[]{40, 161, 1194});

    /** View initialisation, each pipeline's runs pooled across all five formats: {W, U, ms}. */
    private static final List<double[]> VIEW_INIT = List.of(
            new double[]{5, 5, 296},     new double[]{6, 14, 290},
            new double[]{8, 8, 346},     new double[]{10, 25, 345},
            new double[]{14, 50, 384},   new double[]{18, 66, 397},
            new double[]{22, 40, 442},   new double[]{20, 100, 391},
            new double[]{38, 178, 547},  new double[]{32, 212, 499},
            new double[]{46, 60, 623},   new double[]{40, 168, 524});

    @Test
    void svgRowOfTheCostModelTableIsReproducible() {
        double[] fit = BatchExportEvaluationIT.ols2(SVG);

        assertEquals(353, fit[0], 1.0, "intercept a");
        assertEquals(8.38, fit[1], 0.02, "b, ms per widget");
        assertEquals(3.50, fit[2], 0.02, "c, ms per artefact");
    }

    @Test
    void viewInitialisationSlopeIsTheOnePooledAcrossFormats() {
        double[] fit = BatchExportEvaluationIT.ols2(VIEW_INIT);

        // The paper quotes 7.9 ms/widget and no artefact cost. The figure is specific to pooling
        // all five formats: view initialisation does not depend on the requested export format,
        // so a per-format or single-format fit answers a narrower question and gives a
        // different number.
        assertEquals(7.90, fit[1], 0.02, "b, ms per widget");
        assertEquals(0.0, fit[2], 0.5, "c, ms per artefact");
        assertEquals(266, fit[0], 2.0, "intercept a");
    }

    @Test
    void aUnivariateSlopeOverstatesThePerWidgetCost() {
        // The SVG wall times regressed on W alone. W then absorbs the artefact effect too,
        // because the two are correlated, and the per-widget cost comes out two and a half
        // times too large. The two-predictor fit exists to avoid this error.
        double meanW = SVG.stream().mapToDouble(r -> r[0]).average().orElseThrow();
        double meanY = SVG.stream().mapToDouble(r -> r[2]).average().orElseThrow();
        double cov = SVG.stream().mapToDouble(r -> (r[0] - meanW) * (r[2] - meanY)).sum();
        double varW = SVG.stream().mapToDouble(r -> (r[0] - meanW) * (r[0] - meanW)).sum();
        double naive = cov / varW;

        double correct = BatchExportEvaluationIT.ols2(SVG)[1];
        assertEquals(21.1, naive, 0.2, "W-only slope");
        assertTrue(naive > correct * 2,
                "the univariate fit should inflate the per-widget cost well beyond the joint one: "
                        + naive + " vs " + correct);
    }
}
