package org.texttechnologylab.udav.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the report file is self-contained enough to diagnose a bad campaign without
 * access to the machine that produced it: a run showing "failed=3 when 2 was expected" must
 * name the offending widget, its generator, and the exact error. This simulates that
 * situation and asserts all of it survives into the file, in both the human-readable section
 * and the machine-readable JSON block.
 */
class EvaluationReportFileTest {

    @Test
    void reportCarriesFullDiagnosticDetailForAnUnexpectedFailure(@TempDir Path tempDir) throws Exception {
        Path report = tempDir.resolve("evaluation-report.txt");
        System.setProperty("UDAV_EVAL_REPORT", report.toString());
        try {
            BatchExportEvaluationIT.PROBLEMS.clear();
            BatchExportEvaluationIT.FAILURES.clear();
            BatchExportEvaluationIT.CAMPAIGN.clear();
            BatchExportEvaluationIT.WORKLOAD.clear();
            BatchExportEvaluationIT.NON_SVG_WIDGETS.clear();

            BatchExportEvaluationIT.WORKLOAD.put("P5", new int[]{14, 50});
            BatchExportEvaluationIT.NON_SVG_WIDGETS.put("P5", 2);
            BatchExportEvaluationIT.CAMPAIGN.put("P5|svg", List.of(
                    new BatchExportEvaluationIT.Sample(
                            4200, 15, 900, 3100, 2400, 1800, 64_000_000L, 120,
                            5_000_000L, 2_000_000L, 11, 3, 47, 0,
                            342, 1870L, true)));

            // two expected (no SVG) plus the third one that should not have happened
            BatchExportEvaluationIT.FAILURES.put("P5/svg :: expected-1", new BatchExportEvaluationIT.FailureRecord(
                    "P5/svg", "svg",
                    new BatchExportEvaluationIT.Failure("ScrollTable", "ScrollTable-eval000", "ScrollTable 0",
                            "MapCoordinates-eval-points", "Widget type 'ScrollTable' does not support svg export"),
                    true, 15, 300, 344));
            BatchExportEvaluationIT.FAILURES.put("P5/svg :: expected-2", new BatchExportEvaluationIT.FailureRecord(
                    "P5/svg", "svg",
                    new BatchExportEvaluationIT.Failure("HighlightText", "HighlightText-eval-annotations", "HighlightText (annotations)",
                            "TextFormatting-eval-annotations", "Widget type 'HighlightText' does not support svg export"),
                    true, 15, 300, 344));
            BatchExportEvaluationIT.FAILURES.put("P5/svg :: unexpected", new BatchExportEvaluationIT.FailureRecord(
                    "P5/svg", "svg",
                    new BatchExportEvaluationIT.Failure("VoronoiDiagram", "VoronoiDiagram-eval007", "VoronoiDiagram 7",
                            "MapCoordinates-eval-p06-@ID@", "Export timeout after 30000 ms."),
                    false, 15, 331, 344));
            BatchExportEvaluationIT.PROBLEMS.add(
                    "P5/svg: unexpected failure: VoronoiDiagram (VoronoiDiagram-eval007, "
                            + "generator MapCoordinates-eval-p06-@ID@) -> Export timeout after 30000 ms.");

            BatchExportEvaluationIT.writeReportFile();

            assertTrue(Files.exists(report), "report file was not written");
            String text = Files.readString(report);

            // the run must be flagged as unusable
            assertTrue(text.contains("NOT OK"), "verdict should flag the run as unusable");

            // everything needed to act on the failure without the machine
            assertTrue(text.contains("VoronoiDiagram-eval007"), "missing widget id");
            assertTrue(text.contains("MapCoordinates-eval-p06-@ID@"), "missing generator id");
            assertTrue(text.contains("Export timeout after 30000 ms."), "missing error text");
            assertTrue(text.contains("P5/svg"), "missing the condition it happened in");

            // expected failures must be separated so they are not mistaken for defects
            assertTrue(text.contains("### 4a. UNEXPECTED (1 distinct)"), "unexpected count wrong");
            assertTrue(text.contains("### 4b. EXPECTED (2 distinct)"), "expected count wrong");

            // the paper numbers have to be in the same file
            assertTrue(text.contains("tab:workload"), "missing workload table");
            assertTrue(text.contains("tab:latency"), "missing latency table");
            assertTrue(text.contains("tab:resources"), "missing resources table");
            assertTrue(text.contains("tab:baseline"), "missing baseline table");

            // and a machine-readable copy, so the whole run can be re-analysed later
            String json = text.substring(text.indexOf("## 7. RAW DATA (JSON)"));
            json = json.substring(json.indexOf('{'));
            JsonNode raw = new ObjectMapper().readTree(json);

            assertEquals(3, raw.get("failures").size());
            JsonNode unexpected = null;
            for (JsonNode failure : raw.get("failures")) {
                if (!failure.get("expected").asBoolean()) {
                    unexpected = failure;
                }
            }
            assertTrue(unexpected != null, "unexpected failure missing from raw data");
            assertEquals("VoronoiDiagram", unexpected.get("widgetType").asText());
            assertEquals(15, unexpected.get("count").asInt(), "occurrence count lost");
            assertEquals(331, unexpected.get("firstRunIndex").asInt(),
                    "the run the failure first appeared on is what distinguishes an accumulating"
                            + " problem from an immediate one");
            assertEquals(344, unexpected.get("lastRunIndex").asInt());
            assertTrue(text.contains("runs 331"), "run range missing from the readable section");
            assertEquals(14, raw.get("workload").get("P5").get("W").asInt());
            assertEquals(50, raw.get("workload").get("P5").get("U").asInt());
            assertTrue(raw.has("samples"), "raw per-run samples missing");
            assertTrue(raw.has("environment"), "environment block missing");
        } finally {
            System.clearProperty("UDAV_EVAL_REPORT");
        }
    }
}
