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
 * medians back through the harness's own solver, so the numbers in the paper cannot drift away
 * from the code that produces them.
 */
class CostModelTest {

    /** Table 1's SVG column: {W, artefacts actually returned, median wall time in ms}. */
    private static final List<double[]> SVG = List.of(
            new double[]{5, 3, 343},    new double[]{6, 7, 476},
            new double[]{8, 6, 392},    new double[]{10, 19, 576},
            new double[]{14, 43, 748},  new double[]{18, 59, 1022},
            new double[]{22, 34, 789},  new double[]{20, 92, 1251},
            new double[]{38, 168, 2095}, new double[]{32, 200, 2415},
            new double[]{46, 56, 1089}, new double[]{40, 161, 2119});

    /** View initialisation, each pipeline's runs pooled across all five formats: {W, U, ms}. */
    private static final List<double[]> VIEW_INIT = List.of(
            new double[]{5, 5, 256},    new double[]{6, 14, 259},
            new double[]{8, 8, 288},    new double[]{10, 25, 319},
            new double[]{14, 50, 366},  new double[]{18, 66, 439},
            new double[]{22, 40, 390},  new double[]{20, 100, 502},
            new double[]{38, 178, 667}, new double[]{32, 212, 683},
            new double[]{46, 60, 546},  new double[]{40, 168, 732});

    @Test
    void svgRowOfTheCostModelTableIsReproducible() {
        double[] fit = BatchExportEvaluationIT.ols2(SVG);

        assertEquals(316, fit[0], 1.0, "intercept a");
        assertEquals(5.51, fit[1], 0.02, "b, ms per widget");
        assertEquals(9.55, fit[2], 0.02, "c, ms per artefact");
    }

    @Test
    void viewInitialisationSlopeIsTheOnePooledAcrossFormats() {
        double[] fit = BatchExportEvaluationIT.ols2(VIEW_INIT);

        // The paper quotes 5.3 ms/widget. That figure is specific to pooling all five formats:
        // view initialisation does not depend on the requested export format, so a per-format or
        // single-format fit answers a narrower question and gives a different number.
        assertEquals(5.32, fit[1], 0.02, "b, ms per widget");
        assertEquals(225, fit[0], 2.0, "intercept a");
    }

    @Test
    void aUnivariateSlopeOverstatesThePerWidgetCost() {
        // Same data, regressed on W alone. W then absorbs the artefact effect too, because the
        // two are correlated, and the per-widget cost comes out roughly double. The
        // two-predictor fit exists to avoid this error.
        double meanW = VIEW_INIT.stream().mapToDouble(r -> r[0]).average().orElseThrow();
        double meanY = VIEW_INIT.stream().mapToDouble(r -> r[2]).average().orElseThrow();
        double cov = VIEW_INIT.stream().mapToDouble(r -> (r[0] - meanW) * (r[2] - meanY)).sum();
        double varW = VIEW_INIT.stream().mapToDouble(r -> (r[0] - meanW) * (r[0] - meanW)).sum();
        double naive = cov / varW;

        double correct = BatchExportEvaluationIT.ols2(VIEW_INIT)[1];
        assertEquals(10.8, naive, 0.2, "W-only slope");
        assertTrue(naive > correct * 1.5,
                "the univariate fit should inflate the per-widget cost well beyond the joint one: "
                        + naive + " vs " + correct);
    }
}
