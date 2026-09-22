package org.texttechnologylab.udav.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the evaluation harness's verification checks actually fire.
 *
 * These build archives shaped like real export responses, including deliberately malformed
 * ones, and assert that each defect is reported. No running server involved.
 */
class EvaluationVerifierTest {

    private BatchExportEvaluationIT harness;

    @BeforeEach
    void reset() {
        harness = new BatchExportEvaluationIT();
        BatchExportEvaluationIT.PROBLEMS.clear();
        BatchExportEvaluationIT.WORKLOAD.clear();
        BatchExportEvaluationIT.NON_SVG_WIDGETS.clear();
        BatchExportEvaluationIT.FAILURES.clear();
        // A pipeline with 10 artefacts expected and 2 widgets that cannot render SVG.
        BatchExportEvaluationIT.WORKLOAD.put("P1", new int[]{8, 10});
        BatchExportEvaluationIT.NON_SVG_WIDGETS.put("P1", 2);
    }

    /** Builds a zip the way BrowserExportController does. */
    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** Builds an _errors.json in the shape BrowserExportService actually serialises. */
    private static String errors(String... typeIdGeneratorTriples) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < typeIdGeneratorTriples.length; i += 3) {
            if (i > 0) json.append(",");
            json.append("{\"widget\":{\"id\":\"").append(typeIdGeneratorTriples[i + 1])
                    .append("\",\"type\":\"").append(typeIdGeneratorTriples[i])
                    .append("\",\"title\":\"").append(typeIdGeneratorTriples[i])
                    .append("\",\"generatorId\":\"").append(typeIdGeneratorTriples[i + 2])
                    .append("\"},\"error\":\"Widget type '").append(typeIdGeneratorTriples[i])
                    .append("' does not support svg export\"}");
        }
        return json.append("]").toString();
    }

    private static byte[] summary(int exported, int failed) {
        return bytes("{\"pipeline\":\"P1\",\"exported\":" + exported + ",\"failed\":" + failed + "}");
    }

    private static Map<String, byte[]> jsonArchive(int files, int exported, int failed) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < files; i++) {
            entries.put(String.format("%03d-chart.json", i), bytes("{\"data\":[1,2,3]}"));
        }
        entries.put("_summary.json", summary(exported, failed));
        return entries;
    }

    private BatchExportEvaluationIT.Sample sample(int ok, int failed, int files, int invalid) {
        return new BatchExportEvaluationIT.Sample(
                1000, 10, 100, 800, 500, 400, 0, 0, 0, 0, ok, failed, files, invalid,
                1, 0L, true);
    }

    @Test
    void aWellFormedArchiveProducesNoProblems() throws Exception {
        byte[] body = zip(jsonArchive(10, 10, 0));

        var archive = harness.readArchive(body, "json");
        assertEquals(10, archive.files());
        assertEquals(0, archive.invalid());

        harness.verify("P1/json", "P1", "json", true, archive, sample(10, 0, 10, 0));
        assertEquals(java.util.List.of(), BatchExportEvaluationIT.PROBLEMS);
    }

    @Test
    void entriesWithTheWrongExtensionAreReported() throws Exception {
        Map<String, byte[]> entries = jsonArchive(9, 10, 0);
        entries.put("009-chart.txt", bytes("{\"data\":[]}")); // wrong ending
        byte[] body = zip(entries);

        var archive = harness.readArchive(body, "json");
        harness.verify("P1/json", "P1", "json", true, archive, sample(10, 0, 10, 0));

        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("wrong extension")),
                "expected an extension problem, got: " + BatchExportEvaluationIT.PROBLEMS);
    }

    @Test
    void aFileCountThatDisagreesWithTheArchivesOwnSummaryIsReported() throws Exception {
        // 7 artefacts present, but the archive claims it exported 10
        byte[] body = zip(jsonArchive(7, 10, 0));

        var archive = harness.readArchive(body, "json");
        assertEquals(7, archive.files());

        harness.verify("P1/json", "P1", "json", true, archive, sample(10, 0, 7, 0));

        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("_summary.json")
                                && problem.contains("7")),
                "expected a count mismatch, got: " + BatchExportEvaluationIT.PROBLEMS);
    }

    @Test
    void aShortArchiveIsCaughtAgainstTheExpectedFileCount() throws Exception {
        // internally consistent, but only 6 of the 10 files U says there should be
        byte[] body = zip(jsonArchive(6, 6, 0));

        var archive = harness.readArchive(body, "json");
        harness.verify("P1/json", "P1", "json", true, archive, sample(6, 0, 6, 0));

        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("expected U=10")),
                "expected a U mismatch, got: " + BatchExportEvaluationIT.PROBLEMS);
    }

    @Test
    void malformedArtefactContentIsReported() throws Exception {
        Map<String, byte[]> entries = jsonArchive(9, 10, 0);
        entries.put("009-chart.json", bytes("{ this is not json")); // right name, broken content
        byte[] body = zip(entries);

        var archive = harness.readArchive(body, "json");
        assertEquals(1, archive.invalid());

        harness.verify("P1/json", "P1", "json", true, archive, sample(10, 0, 10, 1));
        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("did not parse")
                                && problem.contains("009-chart.json")),
                "expected a parse problem naming the file, got: " + BatchExportEvaluationIT.PROBLEMS);
    }

    @Test
    void unexpectedFailureCountsForSvgAreReported() throws Exception {
        // P1 has exactly 2 widgets that cannot render SVG, so 2 failures is correct...
        Map<String, byte[]> good = new LinkedHashMap<>();
        good.put("000-chart.svg", bytes("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"));
        good.put("_summary.json", summary(1, 2));
        good.put("_errors.json", bytes(errors(
                "ScrollTable", "ScrollTable-eval000", "MapCoordinates-eval-points",
                "HighlightText", "HighlightText-eval-annotations", "TextFormatting-eval-annotations")));

        var archive = harness.readArchive(zip(good), "svg");
        harness.verify("P1/svg", "P1", "svg", true, archive, sample(1, 2, 1, 0));
        assertEquals(java.util.List.of(), BatchExportEvaluationIT.PROBLEMS,
                "2 failures is exactly what P1 should produce for svg");

        // ...but 5 is not, and must be flagged.
        BatchExportEvaluationIT.PROBLEMS.clear();
        Map<String, byte[]> bad = new LinkedHashMap<>();
        bad.put("000-chart.svg", bytes("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"));
        bad.put("_summary.json", summary(1, 5));
        bad.put("_errors.json", bytes(errors(
                "ScrollTable", "ScrollTable-eval000", "MapCoordinates-eval-points",
                "HighlightText", "HighlightText-eval-annotations", "TextFormatting-eval-annotations",
                "VoronoiDiagram", "VoronoiDiagram-eval003", "MapCoordinates-eval-p05-@ID@",
                "MedialAxis", "MedialAxis-eval004", "MapCoordinates-eval-p05-@ID@",
                "LineChart", "LineChart-eval005", "MapCoordinates-eval-p05-@ID@")));

        var badArchive = harness.readArchive(zip(bad), "svg");
        harness.verify("P1/svg", "P1", "svg", true, badArchive, sample(1, 5, 1, 0));
        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("expected exactly 2 widgets to fail")),
                "expected a failure-count problem, got: " + BatchExportEvaluationIT.PROBLEMS);

        // the three SVG-capable widgets must be singled out as defects, the two others not
        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("unexpected failure")
                                && problem.contains("VoronoiDiagram")),
                "the VoronoiDiagram failure should be flagged as unexpected");
        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .noneMatch(problem -> problem.contains("unexpected failure")
                                && problem.contains("ScrollTable")),
                "ScrollTable failing svg is correct and must not be flagged");
        // the run number is what tells an accumulating problem from an immediate one
        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("campaign run #")),
                "problems should say which run they happened on");
    }

    @Test
    void errorsFileDisagreeingWithTheSummaryIsReported() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("000-chart.svg", bytes("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"));
        entries.put("_summary.json", summary(1, 2));
        entries.put("_errors.json", bytes(errors(
                "ScrollTable", "ScrollTable-eval000", "MapCoordinates-eval-points")));

        var archive = harness.readArchive(zip(entries), "svg");
        harness.verify("P1/svg", "P1", "svg", true, archive, sample(1, 2, 1, 0));

        assertTrue(BatchExportEvaluationIT.PROBLEMS.stream()
                        .anyMatch(problem -> problem.contains("_errors.json lists 1")),
                "expected an _errors.json mismatch, got: " + BatchExportEvaluationIT.PROBLEMS);
    }

    @Test
    void aSingleFileResponseIsAcceptedWithoutZipBookkeeping() throws Exception {
        byte[] raw = bytes("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>");

        var archive = harness.readArchive(raw, "svg");
        assertEquals(1, archive.files());
        assertEquals(0, archive.invalid());
        assertEquals(-1, archive.declaredOk(), "a raw single file carries no summary");
    }
}
