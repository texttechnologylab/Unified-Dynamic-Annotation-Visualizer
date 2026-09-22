package org.texttechnologylab.udav.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Measurement harness for the "Batch Export API" evaluation section of the paper.
 *
 * This class does not test correctness of the build. It drives a separately running UDAV
 * instance over HTTP and produces the numbers that fill the paper's tables; every figure the
 * evaluation section quotes comes out of one of the methods below.
 *
 * How to run (one command, whole campaign):
 *
 *   tools/run-evaluation.sh            (or: mvn test -Dtest=BatchExportEvaluationIT
 *                                            -DUDAV_BASE_URL=http://localhost:8080)
 *
 * That runs every phase in order and always ends with the report, even if a phase fails. The
 * report has two parts:
 *   1. PROBLEMS  - every case where the API did not return what was expected. If this section
 *                  is not empty, the numbers below it are not trustworthy; it is printed first.
 *   2. The LaTeX-ready rows for each table of the paper.
 *
 * main() runs the same campaign without JUnit (e.g. straight from the IDE).
 *
 * Prerequisites: UDAV running with its database and EXPORT_METRICS=true, on one quiet machine
 * with a fixed clock and turbo off. The pipeline_eval-*.json pipelines and their JSON sources are
 * bundled and import automatically. The whole campaign is long by design (see the STEADY_* /
 * ANALYSIS_RUNS constants); lower them for a smoke run. The printed output states which settings
 * produced it.
 *
 * What every export is checked for:
 *   Each response is opened and verified before its timings are used: file count against the
 *   archive's own _summary.json, every entry's extension against the requested format, every
 *   artefact parsed as that format, and the reported failure count against the number of
 *   widgets that genuinely cannot produce it. Anything unexpected lands in PROBLEMS.
 *
 * What the server must expose:
 *   Per-request measurements are read from the X-UDAV-Export-Metrics response header, which
 *   BrowserExportController adds when app.export.metrics.enabled (EXPORT_METRICS=true) is set.
 */
@Tag("evaluation")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatchExportEvaluationIT {

    // --- Campaign configuration ---

    private static final String BASE_URL =
            System.getProperty("UDAV_BASE_URL", System.getenv().getOrDefault("UDAV_BASE_URL", "http://localhost:8080"));

    /** Formats under test. The paper reports all five for latency and resources. */
    private static final List<String> FORMATS = List.of("svg", "png", "json", "csv", "tex");

    /** Formats used for the batch-vs-individual baseline (paper restricts this to three). */
    private static final List<String> BASELINE_FORMATS = List.of("svg", "json", "tex");

    /**
     * Bug-hunt mode: -Dudav.eval.bugHunt=true
     *
     * Runs every pipeline in every format exactly once, with no warm-up and no steady-state
     * search, so the whole surface is exercised in minutes instead of hours. The verification
     * (file counts, extensions, artefact parsing and failure classification) is unchanged, so it
     * finds the same defects as a full campaign; it just does not measure anything.
     * The report is stamped accordingly.
     */
    private static final boolean BUG_HUNT =
            Boolean.parseBoolean(System.getProperty("udav.eval.bugHunt", "false"));

    // Steady-state detection, as described in the paper's Method paragraph. Overridable for a
    // quick end-to-end smoke test, e.g.
    //   -Dudav.eval.steadyWindow=2 -Dudav.eval.steadyMaxSearch=3 -Dudav.eval.analysisRuns=2
    // The report records the settings that produced a run.
    private static final int STEADY_WINDOW = intProperty("udav.eval.steadyWindow", 15);
    private static final double STEADY_TOLERANCE = doubleProperty("udav.eval.steadyTolerance", 0.10);
    private static final int STEADY_MAX_SEARCH = intProperty("udav.eval.steadyMaxSearch", 30);
    private static final int ANALYSIS_RUNS = intProperty("udav.eval.analysisRuns", BUG_HUNT ? 1 : 15);

    /** Untimed warm-up requests per condition, so nothing is measured on a cold service. */
    private static final int WARMUP_RUNS = intProperty("udav.eval.warmupRuns", BUG_HUNT ? 0 : 2);

    /** Restrict the sweep to a subset of pipelines, e.g. -Dudav.eval.pipelines=P1,P2 */
    private static final String PIPELINE_FILTER = System.getProperty("udav.eval.pipelines", "");

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double doubleProperty(String key, double fallback) {
        try {
            return Double.parseDouble(System.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** Fixed seed: format order is randomised but the campaign stays reproducible. */
    private static final long SHUFFLE_SEED = 20260825L;

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    // --- The sampled pipelines ---
    //
    // The JSON definitions live in src/main/resources/pipelines/pipeline_eval-*.json and are
    // imported automatically at startup (app.pipeline-json-import.enabled=true), together with
    // their JSON sources in src/main/resources/sourcefilesJSON/ (app.json-data-import.enabled=true).
    // Regenerate all of them with tools/make_eval_pipelines.py. Nothing is patched at runtime.
    //
    // W = widgets with a generator (what batch export selects).
    // U = files a bulk export produces = sum over widgets of the widget's generator group size.
    //
    // How U is achieved: a generator with "generatorGroup": true and "@ID@" in its id expands
    // into one sub-generator per top-level key of its JSON source (DataService.loadSubSourceIds
    // -> SourceJsonN). The eval-src-pagesNN.json slices carry exactly NN keys, so group size,
    // and therefore U, is exact. Non-grouped generators contribute 1.
    //
    // Composition: every pipeline (except PAG) carries seven widget types.
    //   ScrollTable    - MapCoordinates-backed, renders no SVG
    //   HighlightText  - TextFormatting over eval-annotations-text.json: renders no SVG, and by far
    //                    the most expensive of the set to produce (20k characters, 1500 annotated
    //                    spans, 3 layers)
    //   BarChart /     - CategoryNumber over eval-annotations-categories.json: 12 categories over
    //   PieChart         4 synthetic documents
    //   VoronoiDiagram / MedialAxis / LineChart - MapCoordinates, point-only renderers
    //
    // ScrollTable and HighlightText cannot export svg or png at all, so those two produce the
    // expected, legitimate refusals in the failure column. Without them the column is uniformly
    // zero and the correctness check has nothing to exercise; HighlightText is also the widget
    // that stalls for the full widget timeout if the svg pre-check regresses, so it is the one
    // that keeps that column meaningful.
    //
    // The W_EXPECTED / U_EXPECTED values below are asserted at run time, so a pipeline that
    // failed to import or whose sources did not build is caught before it pollutes the campaign.

    private record Pipeline(String label, String id, String sizeClass, int expectedW, int expectedU) {}

    /** The pipelines actually swept, after applying PIPELINE_FILTER. */
    private static List<Pipeline> pipelines() {
        if (PIPELINE_FILTER.isBlank()) {
            return PIPELINES;
        }
        List<String> wanted = List.of(PIPELINE_FILTER.split(","));
        return PIPELINES.stream().filter(p -> wanted.contains(p.label())).toList();
    }

    private static final List<Pipeline> PIPELINES = List.of(
            // ---- SMALL: W <= 10 and U <= 25 -------------------------------------------------
            new Pipeline("P1", "e0000001-0000-4000-8000-000000000001", "small", 5, 5),
            new Pipeline("P2", "e0000002-0000-4000-8000-000000000002", "small", 6, 14),
            new Pipeline("P3", "e0000003-0000-4000-8000-000000000003", "small", 8, 8),
            new Pipeline("P4", "e0000004-0000-4000-8000-000000000004", "small", 10, 25),

            // ---- MEDIUM: neither small nor large --------------------------------------------
            new Pipeline("P5", "e0000005-0000-4000-8000-000000000005", "medium", 14, 50),
            new Pipeline("P6", "e0000006-0000-4000-8000-000000000006", "medium", 18, 66),
            new Pipeline("P7", "e0000007-0000-4000-8000-000000000007", "medium", 22, 40),
            new Pipeline("P8", "e0000008-0000-4000-8000-000000000008", "medium", 20, 100),

            // ---- LARGE: W > 30 or U > 150 ---------------------------------------------------
            new Pipeline("P9", "e0000009-0000-4000-8000-000000000009", "large", 38, 178),
            // P10 and P11 are a deliberate pair. Across a naive sample W, U and output size rise
            // together, so no slope can be attributed to any one of them. P10 is deep and narrow
            // (few widgets, large groups) and P11 is wide and shallow (many widgets, groups of 2).
            // They pull W and U apart, so the Table 3 slopes stay interpretable.
            new Pipeline("P10", "e0000010-0000-4000-8000-000000000010", "large", 32, 212),
            new Pipeline("P11", "e0000011-0000-4000-8000-000000000011", "large", 46, 60),
            new Pipeline("P12", "e0000012-0000-4000-8000-000000000012", "large", 40, 168)
    );

    /**
     * Pipeline for the controlled U-only experiment.
     *
     * Every one of its 16 widgets sits on a group generator of size 8, so bulk=false produces
     * exactly 16 files and bulk=true produces 121. Widget set, composition and layout are
     * byte-identical between the two runs, so the only thing that changes is how many files are
     * exported; the resulting slope is therefore attributable to U rather than to W or to
     * anything correlated with it.
     */
    private static final Pipeline PAGINATION_PIPELINE =
            new Pipeline("PAG", "e00000aa-0000-4000-8000-0000000000aa", "medium", 16, 121);

    // --- Collected results ---

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One measured request. */
    record Sample(long wallMs, long browserInitMs, long pageReadyMs, long exportPhaseMs,
                          long jvmCpuMs, long browserCpuMs, long allocatedBytes, long gcPauseMs,
                          long outputBytes, long zipBytes, int widgetsOk, int widgetsFailed,
                          int filesProduced, int invalidFiles,
                          int runIndex, long atSecondsIntoCampaign, boolean sessionReused) {}

    /**
     * Position of each export in the whole campaign.
     *
     * A failure that only appears after several hundred requests points at something that
     * accumulates (browser memory, a leaked context, a degrading pooled session); one that
     * appears immediately does not. The run index is what lets the report tell those apart.
     */
    private static final java.util.concurrent.atomic.AtomicInteger RUN_COUNTER =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final long CAMPAIGN_START_NANOS = System.nanoTime();

    private static long secondsIntoCampaign() {
        return (System.nanoTime() - CAMPAIGN_START_NANOS) / 1_000_000_000L;
    }

    /** Workload descriptors per pipeline (Table 1). */
    static final Map<String, int[]> WORKLOAD = new LinkedHashMap<>(); // label -> {W, U}

    /** Analysis samples keyed "pipelineLabel|format" (Tables 2 and 3). */
    static final Map<String, List<Sample>> CAMPAIGN = new LinkedHashMap<>();

    /** Conditions that never reached steady state, reported separately. */
    private static final List<String> NON_STEADY = new ArrayList<>();

    /** Baseline results keyed "sizeClass|format" -> {batchMedianMs, individualMedianMs} (Table 4). */
    private static final Map<String, long[]> BASELINE = new LinkedHashMap<>();

    /** Pagination experiment: ms per additional exported file. */
    private static double paginationSlopeMsPerFile = Double.NaN;

    /**
     * Every case where the API returned something other than what was expected.
     *
     * Collected instead of thrown, so one bad condition does not abort the campaign.
     * Printed at the top of the final report.
     */
    static final List<String> PROBLEMS = new ArrayList<>();

    /** Widgets per pipeline that cannot render SVG, i.e. the expected svg/png failures. */
    static final Map<String, Integer> NON_SVG_WIDGETS = new LinkedHashMap<>();

    /** Formats every widget can produce; for these a run is expected to have zero failures. */
    private static final List<String> LOSSLESS_FORMATS = List.of("json", "csv", "tex");

    /** Widget types that render no SVG, so svg/png failures from them are correct behaviour. */
    private static final List<String> NON_SVG_TYPES = List.of("ScrollTable", "HighlightText");

    /**
     * Every distinct failure seen, keyed by "condition :: signature", with how often it occurred
     * and whether it is the expected consequence of a widget that cannot render SVG.
     */
    record FailureRecord(String condition, String format, Failure failure, boolean expected,
                         int count, int firstRunIndex, int lastRunIndex) {}

    static final Map<String, FailureRecord> FAILURES = new LinkedHashMap<>();

    private static void recordFailure(String condition, String format, Failure failure,
                                      boolean expected, int runIndex) {
        String key = condition + " :: " + failure.signature();
        FAILURES.merge(key,
                new FailureRecord(condition, format, failure, expected, 1, runIndex, runIndex),
                (existing, added) -> new FailureRecord(existing.condition(), existing.format(),
                        existing.failure(), existing.expected(), existing.count() + 1,
                        Math.min(existing.firstRunIndex(), runIndex),
                        Math.max(existing.lastRunIndex(), runIndex)));
    }

    /** Best-effort environment capture for the report header. */
    private static final Map<String, String> ENVIRONMENT = new LinkedHashMap<>();

    private static void problem(String context, String detail) {
        String line = context + ": " + detail;
        PROBLEMS.add(line);
        System.out.println("  !! " + line);
    }

    /** Reads whatever the server will tell us about itself, for the report header. */
    private static void captureEnvironment() {
        ENVIRONMENT.put("baseUrl", BASE_URL);
        ENVIRONMENT.put("startedAt", java.time.Instant.now().toString());
        ENVIRONMENT.put("harness.steadyWindow", String.valueOf(STEADY_WINDOW));
        ENVIRONMENT.put("harness.steadyTolerance", String.valueOf(STEADY_TOLERANCE));
        ENVIRONMENT.put("harness.steadyMaxSearch", String.valueOf(STEADY_MAX_SEARCH));
        ENVIRONMENT.put("harness.analysisRuns", String.valueOf(ANALYSIS_RUNS));
        ENVIRONMENT.put("harness.warmupRuns", String.valueOf(WARMUP_RUNS));
        ENVIRONMENT.put("harness.pipelineFilter", PIPELINE_FILTER.isBlank() ? "(all)" : PIPELINE_FILTER);
        ENVIRONMENT.put("harness.bugHuntMode", String.valueOf(BUG_HUNT));
        ENVIRONMENT.put("harness.isSmokeRun",
                String.valueOf(BUG_HUNT || STEADY_WINDOW < 15 || ANALYSIS_RUNS < 15
                        || !PIPELINE_FILTER.isBlank()));
        ENVIRONMENT.put("jvm", System.getProperty("java.version"));
        ENVIRONMENT.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        ENVIRONMENT.put("processors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        for (String endpoint : List.of("/actuator/info", "/actuator/health")) {
            try {
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                ENVIRONMENT.put("server" + endpoint, response.body().replaceAll("\\s+", " "));
            } catch (Exception ignored) {
                // best effort only
            }
        }
    }

    @BeforeAll
    static void requireRunningServer() {
        captureEnvironment();
        assumeTrue(serverIsUp(), "UDAV is not reachable at " + BASE_URL + " - start it before running the campaign");
        assumeTrue(pipelineExists(PIPELINES.getFirst().id()),
                "Evaluation pipelines are not imported - check that pipeline_eval-*.json reached the pipelines table");
    }

    // -----------------------------------------------------------------------------------------
    // TEST 1 - Workload characterisation  ->  paper Table 1 (tab:workload)
    // -----------------------------------------------------------------------------------------

    /**
     * Determines W and U for every sampled pipeline, which the paper reports as its workload
     * table and uses as the x-axis for every scaling claim.
     *
     * W  = number of generator-backed widgets in the pipeline. Read from the stored pipeline
     *      definition, counting widgets that carry a non-empty generator.id. This is exactly the
     *      set BrowserExportService selects for export, so W matches what the API will attempt.
     * U  = number of artefacts a bulk export produces. Counted as the number of ZIP entries
     *      excluding the two bookkeeping entries (_summary.json, _errors.json).
     *
     * API calls:
     *   GET /api/pipelines/{id}                            -> pipeline definition, for W
     *   GET /api/batch/export/pipeline/{id}/json?bulk=true  -> archive, for U
     * JSON is used to count U because it is the cheapest format and produces exactly one file
     * per dataset, so U is a property of the pipeline rather than of the format.
     */
    @Test
    @Order(1)
    void measureWorkloadDescriptors() {
        guard("workload descriptors", this::measureWorkloadDescriptorsImpl);
    }

    private void measureWorkloadDescriptorsImpl() throws Exception {
        for (Pipeline pipeline : pipelines()) {
            List<Map<String, Object>> widgets = generatorWidgets(pipeline.id());
            int w = widgets.size();

            // ScrollTable and HighlightText render no SVG, so they are the widgets that must
            // fail an svg or png export. Counting them here turns "some exports failed" into a
            // precise expectation the verifier can check every run.
            int nonSvg = (int) widgets.stream()
                    .map(widget -> String.valueOf(widget.get("type")))
                    .filter(type -> type.equals("ScrollTable") || type.equals("HighlightText"))
                    .count();
            NON_SVG_WIDGETS.put(pipeline.label(), nonSvg);

            // U is measured with json, which every widget can produce, so it is the full count.
            int u = exportPipeline(pipeline.id(), "json", true).filesProduced();
            WORKLOAD.put(pipeline.label(), new int[]{w, u});

            System.out.printf("[workload] %-4s %-6s W=%-3d U=%-4d non-SVG=%d  (expected W=%d U=%d)%n",
                    pipeline.label(), pipeline.sizeClass(), w, u, nonSvg,
                    pipeline.expectedW(), pipeline.expectedU());

            // A mismatch means the pipeline did not import, its sources did not build, or the
            // generator groups did not expand. Any of those makes the whole campaign meaningless,
            // so record it prominently, but keep going so one bad pipeline does not cost the
            // runs of the others.
            if (w != pipeline.expectedW()) {
                problem(pipeline.label(), "expected W=" + pipeline.expectedW() + " but found " + w
                        + " widgets - pipeline not imported, or its sources did not build");
            }
            if (u != pipeline.expectedU()) {
                problem(pipeline.label(), "expected U=" + pipeline.expectedU() + " but a bulk json"
                        + " export produced " + u + " files - generator groups probably did not expand");
            }
            if (nonSvg == 0) {
                problem(pipeline.label(), "no ScrollTable/HighlightText widget - the svg/png failure"
                        + " column will be uniformly zero and the correctness check has nothing to exercise");
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // TEST 2 - Primary campaign  ->  paper Tables 2 and 3 (tab:latency, tab:resources)
    // -----------------------------------------------------------------------------------------

    /**
     * The main measurement: every pipeline in every format, under the paper's steady-state rule.
     *
     * For each (pipeline, format) condition this
     *   1. issues WARMUP_RUNS untimed requests so nothing is measured on a cold service,
     *   2. keeps issuing requests until STEADY_WINDOW consecutive runs lie within
     *      +/- STEADY_TOLERANCE of their own median (searching at most STEADY_MAX_SEARCH runs),
     *   3. then issues ANALYSIS_RUNS further requests, and only those are analysed.
     * Detection runs and analysis runs are therefore disjoint, as the paper states. A condition
     * that never settles is recorded in NON_STEADY and reported separately rather than pooled.
     *
     * Format order is shuffled with a fixed seed so ordering effects do not align with format,
     * while the campaign stays reproducible.
     *
     * Each request yields, from the X-UDAV-Export-Metrics header: total wall time, the
     * browser-start / view-init / export-phase split (RQ2's fixed-versus-per-widget question),
     * server and browser CPU (RQ3), allocated bytes, GC pause, and output size. From the archive
     * body it yields the file count and the number of structurally invalid artefacts.
     *
     * API call (per run):
     *   GET /api/batch/export/pipeline/{id}/{format}?bulk=true
     *
     * Pipeline requirements: all twelve pipelines described in PIPELINES. Every size class needs
     * at least one non-SVG widget, otherwise the failure column is uniformly zero for svg/png and
     * the correctness check has nothing to exercise.
     */
    @Test
    @Order(2)
    void measurePrimaryCampaign() {
        guard("primary campaign", this::measurePrimaryCampaignImpl);
    }

    private void measurePrimaryCampaignImpl() throws Exception {
        List<String> order = new ArrayList<>(FORMATS);
        Collections.shuffle(order, new Random(SHUFFLE_SEED));

        for (Pipeline pipeline : pipelines()) {
            for (String format : order) {
                String condition = pipeline.label() + "|" + format;

                for (int i = 0; i < WARMUP_RUNS; i++) {
                    exportPipeline(pipeline.id(), format, true);
                }

                // The steady-state search is what makes a real campaign take hours; bug-hunt
                // mode skips it because nothing is being timed anyway.
                if (!BUG_HUNT && !reachedSteadyState(pipeline, format)) {
                    NON_STEADY.add(condition);
                    System.out.printf("[campaign] %s NEVER SETTLED - reported separately%n", condition);
                }

                List<Sample> analysis = new ArrayList<>();
                for (int i = 0; i < ANALYSIS_RUNS; i++) {
                    analysis.add(exportPipeline(pipeline.id(), format, true));
                }
                CAMPAIGN.put(condition, analysis);

                System.out.printf("[campaign] %-12s median=%dms IQR=%dms ok=%d failed=%d%n",
                        condition,
                        median(analysis.stream().map(Sample::wallMs).toList()),
                        iqr(analysis.stream().map(Sample::wallMs).toList()),
                        analysis.getFirst().widgetsOk(),
                        analysis.getFirst().widgetsFailed());
            }
        }
    }

    /** Runs up to STEADY_MAX_SEARCH requests looking for a stable window. */
    private boolean reachedSteadyState(Pipeline pipeline, String format) throws Exception {
        List<Long> trace = new ArrayList<>();
        for (int i = 0; i < STEADY_MAX_SEARCH; i++) {
            trace.add(exportPipeline(pipeline.id(), format, true).wallMs());
            if (trace.size() >= STEADY_WINDOW) {
                List<Long> window = trace.subList(trace.size() - STEADY_WINDOW, trace.size());
                long m = median(window);
                boolean stable = window.stream()
                        .allMatch(v -> Math.abs(v - m) <= STEADY_TOLERANCE * m);
                if (stable) return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------------------------
    // TEST 3 - Batch versus individual requests  ->  paper Table 4 (tab:baseline)
    // -----------------------------------------------------------------------------------------

    /**
     * Answers RQ2's client-visible half: is one batch request cheaper than W single-widget
     * requests that produce the same artefacts?
     *
     * For one pipeline per size class and three formats, this compares
     *   batch      : a single GET /api/batch/export/pipeline/{id}/{format}?bulk=true
     *   individual : W sequential POST /api/batch/export/{format} calls, one per widget,
     *                each with the same bulk=true flag so the artefacts match the batch run
     * and reports the median total wall time of each over ANALYSIS_RUNS repetitions.
     *
     * Note on interpretation: browsers are pooled, so an individual request does not pay a
     * browser launch. What it pays W times over is loading and initialising the pipeline
     * view. The difference between the two columns is therefore the cost of repeated view
     * initialisation, not of repeated browser start-up.
     *
     * API calls:
     *   GET  /api/pipelines/{id}                             -> widget list to iterate
     *   GET  /api/batch/export/pipeline/{id}/{format}?bulk=true
     *   POST /api/batch/export/{format}
     *        body: {"pipeline":"<id>","widget":{"id":..,"type":..,"generator":{"id":..}},"bulk":true}
     *
     * Pipeline requirements: exactly one pipeline per size class, taken from PIPELINES (the
     * first small, first medium, first large). No new pipelines needed. Fewer repetitions are
     * used here than in the primary campaign because one individual run issues W full requests.
     */
    @Test
    @Order(3)
    void measureBatchVersusIndividualRequests() {
        guard("batch vs individual", this::measureBatchVersusIndividualRequestsImpl);
    }

    private void measureBatchVersusIndividualRequestsImpl() throws Exception {
        for (String sizeClass : List.of("small", "medium", "large")) {
            Pipeline pipeline = pipelines().stream()
                    .filter(p -> p.sizeClass().equals(sizeClass))
                    .findFirst()
                    .orElse(null);
            if (pipeline == null) {
                continue; // filtered out of this run
            }

            List<Map<String, Object>> widgets = generatorWidgets(pipeline.id());

            for (String format : BASELINE_FORMATS) {
                exportPipeline(pipeline.id(), format, true); // warm-up, untimed

                List<Long> batch = new ArrayList<>();
                List<Long> individual = new ArrayList<>();

                for (int run = 0; run < ANALYSIS_RUNS; run++) {
                    long batchStart = System.nanoTime();
                    exportPipeline(pipeline.id(), format, true);
                    batch.add((System.nanoTime() - batchStart) / 1_000_000);

                    long individualStart = System.nanoTime();
                    for (Map<String, Object> widget : widgets) {
                        exportSingleWidget(pipeline.id(), widget, format, true);
                    }
                    individual.add((System.nanoTime() - individualStart) / 1_000_000);
                }

                BASELINE.put(sizeClass + "|" + format, new long[]{median(batch), median(individual)});
                System.out.printf("[baseline] %-6s %-4s batch=%dms individual=%dms (W=%d)%n",
                        sizeClass, format, median(batch), median(individual), widgets.size());
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // TEST 4 - Controlled U-only experiment  ->  the paragraph after Table 4
    // -----------------------------------------------------------------------------------------

    /**
     * Separates the effect of U from the effects of W and output size.
     *
     * Across the twelve pipelines W, U and output size all rise together, so a slope against U
     * cannot show that U is what drives runtime. This experiment holds the pipeline, its widget
     * set and its composition completely fixed and changes only how many files are exported, by
     * contrasting bulk=false (exactly one artefact per widget) with bulk=true (every page).
     *
     * The resulting within-pipeline slope, in ms per additional file, is compared in the paper
     * against the cross-pipeline slope from Table 3. A large gap means runtime is driven by
     * something correlated with U rather than by U itself.
     *
     * API calls:
     *   GET /api/batch/export/pipeline/{id}/{format}?bulk=false
     *   GET /api/batch/export/pipeline/{id}/{format}?bulk=true
     * Run in svg, which is the format with the least server-side work, so the measured slope
     * reflects export and transport cost rather than conversion cost.
     *
     * Pipeline requirement: PAGINATION_PIPELINE above - a medium pipeline over a corpus deep
     * enough that bulk export yields clearly more than one file per widget. If U(bulk=true)
     * equals U(bulk=false) the pipeline is unsuitable and the experiment reports nothing.
     */
    @Test
    @Order(4)
    void measurePaginationDepthEffect() {
        guard("pagination depth", this::measurePaginationDepthEffectImpl);
    }

    private void measurePaginationDepthEffectImpl() throws Exception {
        String id = PAGINATION_PIPELINE.id();

        exportPipeline(id, "svg", false); // warm-up, untimed

        List<Long> shallowMs = new ArrayList<>();
        List<Long> deepMs = new ArrayList<>();
        int shallowFiles = 0;
        int deepFiles = 0;

        for (int run = 0; run < ANALYSIS_RUNS; run++) {
            Sample shallow = exportPipeline(id, "svg", false);
            Sample deep = exportPipeline(id, "svg", true);
            shallowMs.add(shallow.wallMs());
            deepMs.add(deep.wallMs());
            shallowFiles = shallow.filesProduced();
            deepFiles = deep.filesProduced();
        }

        int extraFiles = deepFiles - shallowFiles;
        assumeTrue(extraFiles > 0,
                "bulk export produced no extra files - PAGINATION_PIPELINE needs a deeper corpus");

        paginationSlopeMsPerFile = (double) (median(deepMs) - median(shallowMs)) / extraFiles;
        System.out.printf("[pagination] %d -> %d files, %dms -> %dms, slope=%.2f ms/file%n",
                shallowFiles, deepFiles, median(shallowMs), median(deepMs), paginationSlopeMsPerFile);
    }

    // -----------------------------------------------------------------------------------------
    // HTTP + parsing
    // -----------------------------------------------------------------------------------------

    /**
     * Issues one pipeline export and parses both the measurement header and the archive.
     *
     * The frontend's data layer silently substitutes bundled demo data when /api/data fails, so
     * an export can succeed while containing the wrong thing. Checking that each artefact at
     * least parses as its own format catches the grossest form of that; it is not a fidelity
     * check.
     */
    private Sample exportPipeline(String pipelineId, String format, boolean bulk) throws Exception {
        return exportPipeline(labelFor(pipelineId), pipelineId, format, bulk);
    }

    private static String labelFor(String pipelineId) {
        if (PAGINATION_PIPELINE.id().equals(pipelineId)) {
            return PAGINATION_PIPELINE.label();
        }
        return PIPELINES.stream()
                .filter(p -> p.id().equals(pipelineId))
                .map(Pipeline::label)
                .findFirst()
                .orElse(pipelineId);
    }

    private Sample exportPipeline(String label, String pipelineId, String format, boolean bulk)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/batch/export/pipeline/" + pipelineId + "/" + format
                        + "?bulk=" + bulk))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        String context = label + "/" + format + (bulk ? "" : " (bulk=false)");

        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("export failed: HTTP " + response.statusCode()
                    + " for " + pipelineId + "/" + format);
        }

        JsonNode metrics = MAPPER.readTree(response.headers()
                .firstValue("X-UDAV-Export-Metrics")
                .orElseThrow(() -> new IllegalStateException(
                        "X-UDAV-Export-Metrics header missing - start UDAV with EXPORT_METRICS=true")));

        String disposition = response.headers().firstValue("Content-Disposition").orElse("");
        if (!disposition.contains(".zip")) {
            problem(context, "expected a zip attachment but Content-Disposition was: " + disposition);
        }

        Archive archive = readArchive(response.body(), format);

        Sample sample = new Sample(
                metrics.path("wallMs").asLong(),
                metrics.path("browserInitMs").asLong(),
                metrics.path("pageReadyMs").asLong(),
                metrics.path("exportPhaseMs").asLong(),
                metrics.path("jvmCpuMs").asLong(),
                metrics.path("browserCpuMs").asLong(),
                metrics.path("allocatedBytes").asLong(),
                metrics.path("gcPauseMs").asLong(),
                metrics.path("outputBytes").asLong(),
                metrics.path("zipBytes").asLong(),
                metrics.path("widgetsOk").asInt(),
                metrics.path("widgetsFailed").asInt(),
                archive.files(),
                archive.invalid(),
                RUN_COUNTER.incrementAndGet(),
                secondsIntoCampaign(),
                metrics.path("sessionReused").asBoolean(false));

        verify(context, label, format, bulk, archive, sample);
        return sample;
    }

    /** One single-widget export. Used only by the individual-request baseline. */
    private void exportSingleWidget(String pipelineId, Map<String, Object> widget, String format, boolean bulk)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pipeline", pipelineId);
        body.put("widget", widget);
        body.put("bulk", bulk);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/batch/export/" + format))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        // A widget that cannot render the format (HighlightText as svg) answers 500 by design.
        // That is a legitimate outcome here; the baseline measures elapsed time, not success.
        HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * What one export response actually contained.
     *
     * @param files       artefacts, excluding the _summary.json / _errors.json bookkeeping entries
     * @param invalid     artefacts that did not parse as their own format
     * @param declaredOk  the archive's own _summary.json "exported" count, -1 if absent
     * @param declaredBad the archive's own _summary.json "failed" count, -1 if absent
     * @param errorEntries entries listed in _errors.json, -1 if absent
     * @param badNames    entry names whose extension did not match the requested format
     */
    record Archive(int files, int invalid, int declaredOk, int declaredBad,
                           int errorEntries, List<String> badNames, List<Failure> failures,
                           List<String> invalidNames) {}

    /**
     * One widget-level failure as reported in the archive's _errors.json.
     *
     * Captured in full, not just counted, so the report can name the widget that failed and
     * the error it reported.
     */
    record Failure(String widgetType, String widgetId, String widgetTitle,
                   String generatorId, String error) {

        /** Stable key for grouping the same failure across runs. */
        String signature() {
            return widgetType + "|" + generatorId + "|" + error;
        }
    }

    /**
     * Opens the response, counts the artefacts, and checks each one.
     *
     * Three independent things have to agree: the number of entries actually in the archive,
     * the count the archive reports about itself in _summary.json, and the number of entries in
     * _errors.json. Cross-checking them catches a silently truncated or mis-assembled archive.
     */
    Archive readArchive(byte[] body, String format) throws Exception {
        int files = 0;
        int invalid = 0;
        int declaredOk = -1;
        int declaredBad = -1;
        int errorEntries = -1;
        List<String> badNames = new ArrayList<>();
        List<String> invalidNames = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();

        // A single-file export is returned raw rather than zipped.
        if (!(body.length > 1 && body[0] == 'P' && body[1] == 'K')) {
            return new Archive(1, isStructurallyValid(body, format) ? 0 : 1, -1, -1, -1,
                    List.of(), List.of(), List.of());
        }

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] content = zip.readAllBytes();

                if (name.equals("_summary.json")) {
                    JsonNode summary = MAPPER.readTree(content);
                    declaredOk = summary.path("exported").asInt(-1);
                    declaredBad = summary.path("failed").asInt(-1);
                    continue;
                }
                if (name.equals("_errors.json")) {
                    JsonNode errors = MAPPER.readTree(content);
                    errorEntries = errors.isArray() ? errors.size() : -1;
                    if (errors.isArray()) {
                        for (JsonNode failure : errors) {
                            JsonNode widget = failure.path("widget");
                            failures.add(new Failure(
                                    widget.path("type").asText("?"),
                                    widget.path("id").asText("?"),
                                    widget.path("title").asText(""),
                                    widget.path("generatorId").asText("?"),
                                    failure.path("error").asText("")));
                        }
                    }
                    continue;
                }

                files++;
                // Entries are named "%03d-<safe filename>", so the extension is the last segment.
                if (!name.toLowerCase().endsWith("." + format)) {
                    badNames.add(name);
                }
                if (!isStructurallyValid(content, format)) {
                    invalid++;
                    invalidNames.add(name);
                }
            }
        }
        return new Archive(files, invalid, declaredOk, declaredBad, errorEntries, badNames,
                failures, invalidNames);
    }

    /**
     * Cheap per-format structural check: the artefact is a well-formed file of the requested
     * type. Says nothing about whether its contents are right.
     */
    private static boolean isStructurallyValid(byte[] content, String format) {
        if (content.length == 0) return false;
        try {
            switch (format) {
                case "svg" -> {
                    var factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
                    return "svg".equals(doc.getDocumentElement().getLocalName());
                }
                case "png" -> {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                    return image != null && image.getWidth() > 0 && image.getHeight() > 0;
                }
                case "json" -> {
                    MAPPER.readTree(content);
                    return true;
                }
                case "csv" -> {
                    String text = new String(content, StandardCharsets.UTF_8);
                    return !text.lines().findFirst().orElse("").isBlank();
                }
                case "tex" -> {
                    return !new String(content, StandardCharsets.UTF_8).strip().isEmpty();
                }
                default -> {
                    return true;
                }
            }
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Checks one export response against what the API was supposed to return, and records any
     * discrepancy in PROBLEMS.
     *
     * Expected artefact count depends on the format. Every widget can produce json, csv and tex,
     * so those must yield exactly U files and no failures. ScrollTable and HighlightText render
     * no SVG, so svg and png must fail for exactly those widgets - a run where they unexpectedly
     * succeed, or where something else fails too, is just as wrong as a crash.
     */
    void verify(String context, String pipelineLabel, String format,
                        boolean bulk, Archive archive, Sample sample) {
        int runIndex = sample.runIndex();

        if (!archive.badNames().isEmpty()) {
            problem(context, "entries with the wrong extension (expected ." + format + "): "
                    + archive.badNames().stream().limit(5).toList()
                    + (archive.badNames().size() > 5 ? " and " + (archive.badNames().size() - 5) + " more" : ""));
        }

        if (archive.declaredOk() >= 0 && archive.declaredOk() != archive.files()) {
            problem(context, "archive holds " + archive.files() + " artefacts but its _summary.json"
                    + " reports " + archive.declaredOk());
        }

        if (archive.declaredBad() >= 0 && sample.widgetsFailed() != archive.declaredBad()) {
            problem(context, "_summary.json reports " + archive.declaredBad()
                    + " failures but the metrics header reports " + sample.widgetsFailed());
        }

        if (archive.declaredBad() > 0 && archive.errorEntries() >= 0
                && archive.errorEntries() != archive.declaredBad()) {
            problem(context, "_errors.json lists " + archive.errorEntries()
                    + " entries but _summary.json reports " + archive.declaredBad() + " failures");
        }

        if (archive.declaredBad() > 0 && archive.errorEntries() < 0) {
            problem(context, archive.declaredBad() + " failures reported but no _errors.json in the archive");
        }

        // Classify every failure. A widget that renders no SVG failing svg/png is correct;
        // anything else is a defect and is surfaced with its full context.
        for (Failure failure : archive.failures()) {
            boolean expected = !LOSSLESS_FORMATS.contains(format)
                    && NON_SVG_TYPES.contains(failure.widgetType());
            recordFailure(context, format, failure, expected, runIndex);
            if (!expected) {
                problem(context, "unexpected failure on campaign run #" + runIndex + " ("
                        + sample.atSecondsIntoCampaign() + "s in): " + failure.widgetType()
                        + " (" + failure.widgetId() + ", generator " + failure.generatorId() + ") -> "
                        + failure.error());
            }
        }

        if (!archive.invalidNames().isEmpty()) {
            problem(context, "artefacts that did not parse: "
                    + archive.invalidNames().stream().limit(5).toList());
        }

        // Format-specific expectations, only checkable once the workload phase has run.
        Integer nonSvg = NON_SVG_WIDGETS.get(pipelineLabel);
        int[] workload = WORKLOAD.get(pipelineLabel);

        if (LOSSLESS_FORMATS.contains(format)) {
            if (sample.widgetsFailed() != 0) {
                problem(context, "every widget can produce " + format + " but "
                        + sample.widgetsFailed() + " failed");
            }
            if (bulk && workload != null && archive.files() != workload[1]) {
                problem(context, "expected U=" + workload[1] + " artefacts but got " + archive.files());
            }
        } else if (nonSvg != null) {
            if (sample.widgetsFailed() != nonSvg) {
                problem(context, "expected exactly " + nonSvg + " widgets to fail " + format
                        + " (the ones that render no SVG) but " + sample.widgetsFailed() + " failed");
            }
        }
    }

    /** Reads the stored pipeline and returns the widgets batch export would select. */
    private List<Map<String, Object>> generatorWidgets(String pipelineId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/pipelines/" + pipelineId))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode pipeline = MAPPER.readTree(response.body());

        List<Map<String, Object>> widgets = new ArrayList<>();
        for (JsonNode widget : pipeline.path("widgets")) {
            String generatorId = widget.path("generator").path("id").asText(null);
            if (generatorId == null || generatorId.isBlank()) {
                continue; // static widget: never exported, does not count towards W
            }
            Map<String, Object> selection = new LinkedHashMap<>();
            selection.put("id", widget.path("id").asText(null));
            selection.put("type", widget.path("type").asText(null));
            selection.put("generator", Map.of("id", generatorId));
            widgets.add(selection);
        }
        return widgets;
    }

    private static boolean pipelineExists(String pipelineId) {
        try {
            return HTTP.send(HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/pipelines/" + pipelineId))
                            .timeout(Duration.ofSeconds(30))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean serverIsUp() {
        try {
            HttpResponse<String> response = HTTP.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/actuator/health"))
                            .timeout(Duration.ofSeconds(10))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception ex) {
            return false;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------------------------

    private static long median(List<Long> values) {
        if (values.isEmpty()) return -1;
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private static long iqr(List<Long> values) {
        if (values.size() < 4) return 0;
        List<Long> sorted = values.stream().sorted().toList();
        return percentile(sorted, 0.75) - percentile(sorted, 0.25);
    }

    private static long percentile(List<Long> sorted, double p) {
        int index = (int) Math.round(p * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    /**
     * Ordinary least squares for {@code y = a + b*x1 + c*x2}, solved via the normal equations
     * with partial pivoting. Returns {@code {a, b, c}}, or null if the system is singular.
     *
     * A slope against one predictor alone absorbs the other whenever the two are correlated,
     * which W and the artefact count are; a univariate per-widget cost would come out roughly
     * twice as high.
     */
    static double[] ols2(List<double[]> rows) {
        int n = rows.size();
        double sx1 = 0, sx2 = 0, sy = 0, s11 = 0, s22 = 0, s12 = 0, s1y = 0, s2y = 0;
        for (double[] r : rows) {
            sx1 += r[0]; sx2 += r[1]; sy += r[2];
            s11 += r[0] * r[0]; s22 += r[1] * r[1]; s12 += r[0] * r[1];
            s1y += r[0] * r[2]; s2y += r[1] * r[2];
        }
        double[][] a = {{n, sx1, sx2}, {sx1, s11, s12}, {sx2, s12, s22}};
        double[] b = {sy, s1y, s2y};
        for (int i = 0; i < 3; i++) {
            int piv = i;
            for (int r = i; r < 3; r++) if (Math.abs(a[r][i]) > Math.abs(a[piv][i])) piv = r;
            double[] tmp = a[i]; a[i] = a[piv]; a[piv] = tmp;
            double t = b[i]; b[i] = b[piv]; b[piv] = t;
            if (Math.abs(a[i][i]) < 1e-12) return null;
            for (int r = 0; r < 3; r++) {
                if (r == i) continue;
                double f = a[r][i] / a[i][i];
                for (int c = 0; c < 3; c++) a[r][c] -= f * a[i][c];
                b[r] -= f * b[i];
            }
        }
        return new double[]{b[0] / a[0][0], b[1] / a[1][1], b[2] / a[2][2]};
    }

    /**
     * Theil-Sen slope: the median of the pairwise slopes. Used instead of a least-squares fit
     * so a single unusual pipeline cannot dominate the result.
     */
    private static double theilSen(List<double[]> points) {
        List<Double> slopes = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double dx = points.get(j)[0] - points.get(i)[0];
                if (dx != 0) {
                    slopes.add((points.get(j)[1] - points.get(i)[1]) / dx);
                }
            }
        }
        if (slopes.isEmpty()) return Double.NaN;
        Collections.sort(slopes);
        int n = slopes.size();
        return n % 2 == 1 ? slopes.get(n / 2) : (slopes.get(n / 2 - 1) + slopes.get(n / 2)) / 2;
    }

    // -----------------------------------------------------------------------------------------
    // FINAL OUTPUT - console summary and report file
    // -----------------------------------------------------------------------------------------

    @AfterAll
    static void printPaperNumbers() {
        String rule = "=".repeat(96);

        // ---- Problems first, so a broken campaign cannot be mistaken for a good one ---------
        System.out.println();
        System.out.println(rule);
        if (PROBLEMS.isEmpty()) {
            System.out.println(" PROBLEMS: none - every export returned what was expected");
        } else {
            System.out.println(" PROBLEMS: " + PROBLEMS.size()
                    + " - THE NUMBERS BELOW ARE NOT TRUSTWORTHY UNTIL THESE ARE RESOLVED");
            System.out.println(rule);
            Map<String, Integer> grouped = new LinkedHashMap<>();
            for (String entry : PROBLEMS) {
                grouped.merge(entry, 1, Integer::sum);
            }
            grouped.forEach((entry, count) ->
                    System.out.printf("  %s%s%n", entry, count > 1 ? "   [x" + count + "]" : ""));
        }
        System.out.println(rule);

        System.out.println();
        System.out.println(rule);
        System.out.println(" NUMBERS FOR THE PAPER'S TABLES");
        System.out.printf ("  settings: steady window=%d tolerance=%.0f%% max search=%d analysis runs=%d warmup=%d%n",
                STEADY_WINDOW, STEADY_TOLERANCE * 100, STEADY_MAX_SEARCH, ANALYSIS_RUNS, WARMUP_RUNS);
        System.out.println(rule);

        // ---- Table 1: workload -------------------------------------------------------------
        System.out.println();
        System.out.println("-- tab:workload -----------------------------------------------------------------");
        for (Pipeline pipeline : pipelines()) {
            int[] wu = WORKLOAD.getOrDefault(pipeline.label(), new int[]{-1, -1});
            System.out.printf("%-4s & %-6s & %d & %d \\\\%n",
                    pipeline.label(), pipeline.sizeClass(), wu[0], wu[1]);
        }

        System.out.println();
        System.out.println("-- artefact counts actually returned (not a table; a sanity check) ---------------");
        System.out.printf("%-6s %-6s %8s %8s %8s %10s%n", "pipe", "format", "files", "U", "failed", "invalid");
        CAMPAIGN.forEach((condition, samples) -> {
            if (samples.isEmpty()) return;
            String[] parts = condition.split("\\|");
            int[] wu = WORKLOAD.getOrDefault(parts[0], new int[]{-1, -1});
            Sample first = samples.getFirst();
            System.out.printf("%-6s %-6s %8d %8d %8d %10d%n",
                    parts[0], parts[1], first.filesProduced(), wu[1],
                    first.widgetsFailed(), first.invalidFiles());
        });

        // ---- Table 2: latency, cost split, failures ----------------------------------------
        System.out.println();
        System.out.println("-- tab:latency ------------------------------------------------------------------");
        System.out.println("Format & Median(s) & IQR(s) & BrowserStart(ms) & ViewInit(ms) & ExportPhase(ms) & Fail(%)");
        for (String format : FORMATS) {
            List<Sample> all = samplesFor(format);
            if (all.isEmpty()) continue;

            int attempted = all.stream().mapToInt(s -> s.widgetsOk() + s.widgetsFailed()).sum();
            int failed = all.stream().mapToInt(Sample::widgetsFailed).sum()
                    + all.stream().mapToInt(Sample::invalidFiles).sum();

            System.out.printf("%-4s & %.2f & %.2f & %d & %d & %d & %.1f \\\\%n",
                    format.toUpperCase(),
                    median(all.stream().map(Sample::wallMs).toList()) / 1000.0,
                    iqr(all.stream().map(Sample::wallMs).toList()) / 1000.0,
                    median(all.stream().map(Sample::browserInitMs).toList()),
                    median(all.stream().map(Sample::pageReadyMs).toList()),
                    median(all.stream().map(Sample::exportPhaseMs).toList()),
                    attempted == 0 ? 0.0 : 100.0 * failed / attempted);
        }

        // ---- Table 3: resources and scaling ------------------------------------------------
        System.out.println();
        System.out.println("-- tab:resources ----------------------------------------------------------------");
        System.out.println("Format & ServerCPU(ms) & BrowserCPU(ms) & Allocated(MB) & GCPause(ms) & Output(MB) & Slope(ms/file)");
        for (String format : FORMATS) {
            List<Sample> all = samplesFor(format);
            if (all.isEmpty()) continue;

            // Slope of per-pipeline median runtime against per-pipeline U.
            List<double[]> points = new ArrayList<>();
            for (Pipeline pipeline : pipelines()) {
                List<Sample> perPipeline = CAMPAIGN.get(pipeline.label() + "|" + format);
                int[] wu = WORKLOAD.get(pipeline.label());
                if (perPipeline != null && wu != null) {
                    points.add(new double[]{wu[1], median(perPipeline.stream().map(Sample::wallMs).toList())});
                }
            }

            System.out.printf("%-4s & %d & %d & %.1f & %d & %.1f & %.2f \\\\%n",
                    format.toUpperCase(),
                    median(all.stream().map(Sample::jvmCpuMs).toList()),
                    median(all.stream().map(Sample::browserCpuMs).toList()),
                    median(all.stream().map(Sample::allocatedBytes).toList()) / (1024.0 * 1024.0),
                    median(all.stream().map(Sample::gcPauseMs).toList()),
                    median(all.stream().map(Sample::outputBytes).toList()) / (1024.0 * 1024.0),
                    theilSen(points));
        }

        // ---- Table 4: batch versus individual ----------------------------------------------
        System.out.println();
        System.out.println("-- tab:baseline -----------------------------------------------------------------");
        System.out.println("Format & SmallBatch & SmallIndiv & MedBatch & MedIndiv & LargeBatch & LargeIndiv (seconds)");
        for (String format : BASELINE_FORMATS) {
            StringBuilder row = new StringBuilder(String.format("%-4s", format.toUpperCase()));
            for (String sizeClass : List.of("small", "medium", "large")) {
                long[] pair = BASELINE.getOrDefault(sizeClass + "|" + format, new long[]{-1, -1});
                row.append(String.format(" & %.2f & %.2f", pair[0] / 1000.0, pair[1] / 1000.0));
            }
            System.out.println(row + " \\\\");
        }

        // ---- Inline numbers ----------------------------------------------------------------
        System.out.println();
        System.out.println("-- inline -----------------------------------------------------------------------");
        System.out.printf("pagination experiment slope       : %.2f ms per additional file%n",
                paginationSlopeMsPerFile);
        List<double[]> pooled = new ArrayList<>();
        for (Pipeline pipeline : pipelines()) {
            int[] wu = WORKLOAD.get(pipeline.label());
            List<Sample> perPipeline = CAMPAIGN.get(pipeline.label() + "|svg");
            if (perPipeline != null && wu != null) {
                pooled.add(new double[]{wu[1], median(perPipeline.stream().map(Sample::wallMs).toList())});
            }
        }
        System.out.printf("cross-pipeline slope (svg)        : %.2f ms per file%n", theilSen(pooled));
        System.out.printf("conditions that never settled     : %s%n",
                NON_STEADY.isEmpty() ? "none" : String.join(", ", NON_STEADY));
        System.out.println(rule);

        writeReportFile();
    }

    // -----------------------------------------------------------------------------------------
    // REPORT FILE - paper numbers, problems, and full failure detail in one self-contained file
    // -----------------------------------------------------------------------------------------

    /** Resolved on use, not at class-load, so the destination can be set at runtime. */
    private static String reportPath() {
        return System.getProperty("UDAV_EVAL_REPORT",
                System.getenv().getOrDefault("UDAV_EVAL_REPORT", "evaluation-report.txt"));
    }

    static void writeReportFile() {
        StringBuilder out = new StringBuilder();
        String rule = "=".repeat(96);

        out.append(rule).append("\n");
        out.append(" UDAV BATCH EXPORT - EVALUATION REPORT\n");
        out.append(rule).append("\n\n");

        // ---- 1. verdict, first ------------------------------------------------------------
        out.append("## 1. VERDICT\n\n");
        if (BUG_HUNT) {
            out.append("*** BUG-HUNT RUN - one run per condition, no warm-up, no steady-state search.\n");
            out.append("*** Correctness findings below are valid. The TIMINGS ARE NOT MEASUREMENTS\n");
            out.append("*** and must not go into the paper; re-run without -Dudav.eval.bugHunt for those.\n\n");
        }
        long unexpected = FAILURES.values().stream().filter(f -> !f.expected()).count();
        if (PROBLEMS.isEmpty()) {
            out.append(BUG_HUNT
                    ? "OK - every export returned what was expected. No defects found.\n"
                    : "OK - every export returned what was expected. Numbers below are usable.\n");
        } else {
            out.append("NOT OK - ").append(PROBLEMS.size()).append(" problem(s), ")
                    .append(unexpected).append(" distinct unexpected failure(s).\n")
                    .append("The measurements below were still collected, but should not go into the\n")
                    .append("paper until these are resolved.\n");
        }
        out.append("\n");

        // ---- 2. environment ---------------------------------------------------------------
        out.append("## 2. ENVIRONMENT AND SETTINGS\n\n");
        ENVIRONMENT.forEach((key, value) -> out.append(String.format("  %-26s %s%n", key, value)));
        out.append("\n");

        // ---- 3. problems ------------------------------------------------------------------
        out.append("## 3. PROBLEMS\n\n");
        if (PROBLEMS.isEmpty()) {
            out.append("  none\n");
        } else {
            Map<String, Integer> grouped = new LinkedHashMap<>();
            PROBLEMS.forEach(entry -> grouped.merge(entry, 1, Integer::sum));
            grouped.forEach((entry, count) -> out.append("  - ").append(entry)
                    .append(count > 1 ? "   [x" + count + "]" : "").append("\n"));
        }
        out.append("\n");

        // ---- 4. failures, in full ---------------------------------------------------------
        out.append("## 4. WIDGET FAILURES\n\n");
        out.append("Expected failures are ScrollTable and HighlightText under svg/png: those widgets\n");
        out.append("render no SVG, so the export correctly refuses. Anything else is a defect.\n\n");

        List<FailureRecord> unexpectedFailures = FAILURES.values().stream()
                .filter(record -> !record.expected()).toList();
        List<FailureRecord> expectedFailures = FAILURES.values().stream()
                .filter(FailureRecord::expected).toList();

        out.append("Campaign runs are numbered in issue order. A failure whose run range starts in\n");
        out.append("the hundreds means something accumulates over the campaign; one starting at a low\n");
        out.append("number means it was broken from the start. Total exports issued: ")
                .append(RUN_COUNTER.get()).append(".\n\n");
        out.append("### 4a. UNEXPECTED (").append(unexpectedFailures.size()).append(" distinct)\n\n");
        if (unexpectedFailures.isEmpty()) {
            out.append("  none\n");
        } else {
            for (FailureRecord record : unexpectedFailures) {
                out.append("  condition   : ").append(record.condition()).append("\n");
                out.append("  widget type : ").append(record.failure().widgetType()).append("\n");
                out.append("  widget id   : ").append(record.failure().widgetId()).append("\n");
                out.append("  widget title: ").append(record.failure().widgetTitle()).append("\n");
                out.append("  generator   : ").append(record.failure().generatorId()).append("\n");
                out.append("  occurrences : ").append(record.count())
                        .append("  (campaign runs ").append(record.firstRunIndex())
                        .append("-").append(record.lastRunIndex()).append(")\n");
                out.append("  error       : ").append(record.failure().error()).append("\n\n");
            }
        }

        out.append("### 4b. EXPECTED (").append(expectedFailures.size()).append(" distinct)\n\n");
        if (expectedFailures.isEmpty()) {
            out.append("  none\n");
        } else {
            for (FailureRecord record : expectedFailures) {
                out.append(String.format("  %-22s %-14s x%-4d %s%n",
                        record.condition(), record.failure().widgetType(), record.count(),
                        truncate(record.failure().error(), 60)));
            }
        }
        out.append("\n");

        // ---- 5. paper tables --------------------------------------------------------------
        out.append("## 5. NUMBERS FOR THE PAPER'S TABLES\n\n");
        out.append("### tab:workload\n\n");
        for (Pipeline pipeline : pipelines()) {
            int[] wu = WORKLOAD.getOrDefault(pipeline.label(), new int[]{-1, -1});
            out.append(String.format("%-4s & %-6s & %d & %d \\\\%n",
                    pipeline.label(), pipeline.sizeClass(), wu[0], wu[1]));
        }

        out.append("\n### tab:latency\n\n");
        out.append("Format & Median(s) & IQR(s) & BrowserStart(ms) & ViewInit(ms) & ExportPhase(ms) & Fail(%)\n");
        for (String format : FORMATS) {
            List<Sample> all = samplesFor(format);
            if (all.isEmpty()) continue;
            int attempted = all.stream().mapToInt(x -> x.widgetsOk() + x.widgetsFailed()).sum();
            int failed = all.stream().mapToInt(Sample::widgetsFailed).sum()
                    + all.stream().mapToInt(Sample::invalidFiles).sum();
            out.append(String.format("%-4s & %.2f & %.2f & %d & %d & %d & %.1f \\\\%n",
                    format.toUpperCase(),
                    median(all.stream().map(Sample::wallMs).toList()) / 1000.0,
                    iqr(all.stream().map(Sample::wallMs).toList()) / 1000.0,
                    median(all.stream().map(Sample::browserInitMs).toList()),
                    median(all.stream().map(Sample::pageReadyMs).toList()),
                    median(all.stream().map(Sample::exportPhaseMs).toList()),
                    attempted == 0 ? 0.0 : 100.0 * failed / attempted));
        }

        out.append("\n### tab:resources\n\n");
        out.append("Format & ServerCPU(ms) & BrowserCPU(ms) & Allocated(MB) & GCPause(ms) & Output(MB) & Slope(ms/file)\n");
        for (String format : FORMATS) {
            List<Sample> all = samplesFor(format);
            if (all.isEmpty()) continue;
            List<double[]> points = new ArrayList<>();
            for (Pipeline pipeline : pipelines()) {
                List<Sample> perPipeline = CAMPAIGN.get(pipeline.label() + "|" + format);
                int[] wu = WORKLOAD.get(pipeline.label());
                if (perPipeline != null && wu != null) {
                    points.add(new double[]{wu[1], median(perPipeline.stream().map(Sample::wallMs).toList())});
                }
            }
            out.append(String.format("%-4s & %d & %d & %.1f & %d & %.1f & %.2f \\\\%n",
                    format.toUpperCase(),
                    median(all.stream().map(Sample::jvmCpuMs).toList()),
                    median(all.stream().map(Sample::browserCpuMs).toList()),
                    median(all.stream().map(Sample::allocatedBytes).toList()) / (1024.0 * 1024.0),
                    median(all.stream().map(Sample::gcPauseMs).toList()),
                    median(all.stream().map(Sample::outputBytes).toList()) / (1024.0 * 1024.0),
                    theilSen(points)));
        }

        out.append("\n### tab:baseline\n\n");
        out.append("Format & SmallBatch & SmallIndiv & MedBatch & MedIndiv & LargeBatch & LargeIndiv (seconds)\n");
        for (String format : BASELINE_FORMATS) {
            StringBuilder row = new StringBuilder(String.format("%-4s", format.toUpperCase()));
            for (String sizeClass : List.of("small", "medium", "large")) {
                long[] pair = BASELINE.getOrDefault(sizeClass + "|" + format, new long[]{-1, -1});
                row.append(String.format(" & %.2f & %.2f", pair[0] / 1000.0, pair[1] / 1000.0));
            }
            out.append(row).append(" \\\\\n");
        }

        out.append("\n### inline numbers\n\n");
        out.append(String.format("  pagination experiment slope : %.2f ms per additional file%n",
                paginationSlopeMsPerFile));
        List<double[]> pooled = new ArrayList<>();
        for (Pipeline pipeline : pipelines()) {
            int[] wu = WORKLOAD.get(pipeline.label());
            List<Sample> perPipeline = CAMPAIGN.get(pipeline.label() + "|svg");
            if (perPipeline != null && wu != null) {
                pooled.add(new double[]{wu[1], median(perPipeline.stream().map(Sample::wallMs).toList())});
            }
        }
        out.append(String.format("  cross-pipeline slope (svg)  : %.2f ms per file%n", theilSen(pooled)));

        // Cost model t = a + b*W + c*U per format, over the per-pipeline medians, so the paper's
        // coefficients can be reproduced from this file alone.
        out.append("\n  cost model t = a + b*W + c*U (per-pipeline medians, artefacts actually returned):\n");
        for (String format : FORMATS) {
            List<double[]> rows = new ArrayList<>();
            for (Pipeline pipeline : pipelines()) {
                List<Sample> runs = CAMPAIGN.get(pipeline.label() + "|" + format);
                int[] wu = WORKLOAD.get(pipeline.label());
                if (runs == null || wu == null) continue;
                rows.add(new double[]{wu[0],
                        median(runs.stream().map(s -> (long) s.filesProduced()).toList()),
                        median(runs.stream().map(Sample::wallMs).toList())});
            }
            double[] fit = rows.size() > 3 ? ols2(rows) : null;
            if (fit != null) {
                out.append(String.format("    %-5s a=%7.1f ms  b=%6.2f ms/widget  c=%6.2f ms/artefact%n",
                        format, fit[0], fit[1], fit[2]));
            }
        }

        // View initialisation is format-independent, so its slope is fitted on each pipeline's
        // runs pooled across all formats. The aggregation changes the answer: a per-format or
        // svg-only fit gives a visibly different per-widget cost.
        List<double[]> viewInit = new ArrayList<>();
        for (Pipeline pipeline : pipelines()) {
            List<Long> all = new ArrayList<>();
            for (String format : FORMATS) {
                List<Sample> runs = CAMPAIGN.get(pipeline.label() + "|" + format);
                if (runs != null) runs.forEach(s -> all.add(s.pageReadyMs()));
            }
            int[] wu = WORKLOAD.get(pipeline.label());
            if (!all.isEmpty() && wu != null) {
                viewInit.add(new double[]{wu[0], wu[1], median(all)});
            }
        }
        double[] viewFit = viewInit.size() > 3 ? ols2(viewInit) : null;
        if (viewFit != null) {
            out.append(String.format("%n  view init (all formats pooled per pipeline): a=%.0f ms  "
                            + "b=%.2f ms/widget  c=%.2f ms/artefact%n",
                    viewFit[0], viewFit[1], viewFit[2]));
        }

        out.append("  conditions that never settled: ")
                .append(NON_STEADY.isEmpty() ? "none" : String.join(", ", NON_STEADY)).append("\n\n");

        // ---- 6. per-condition detail ------------------------------------------------------
        out.append("## 6. PER-CONDITION DETAIL\n\n");
        out.append(String.format("%-6s %-6s %6s %6s %8s %8s %9s %9s %10s %10s%n",
                "pipe", "format", "files", "U", "ok", "failed", "invalid", "medianMs", "browserMs", "exportMs"));
        CAMPAIGN.forEach((condition, samples) -> {
            if (samples.isEmpty()) return;
            String[] parts = condition.split("\\|");
            int[] wu = WORKLOAD.getOrDefault(parts[0], new int[]{-1, -1});
            Sample first = samples.getFirst();
            out.append(String.format("%-6s %-6s %6d %6d %8d %8d %9d %9d %10d %10d%n",
                    parts[0], parts[1], first.filesProduced(), wu[1],
                    first.widgetsOk(), first.widgetsFailed(), first.invalidFiles(),
                    median(samples.stream().map(Sample::wallMs).toList()),
                    median(samples.stream().map(Sample::browserInitMs).toList()),
                    median(samples.stream().map(Sample::exportPhaseMs).toList())));
        });

        // ---- 7. raw data ------------------------------------------------------------------
        out.append("\n## 7. RAW DATA (JSON)\n\n");
        try {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("environment", ENVIRONMENT);
            raw.put("problems", PROBLEMS);
            raw.put("nonSteady", NON_STEADY);
            Map<String, Object> workload = new LinkedHashMap<>();
            WORKLOAD.forEach((label, wu) -> workload.put(label, Map.of("W", wu[0], "U", wu[1],
                    "nonSvgWidgets", NON_SVG_WIDGETS.getOrDefault(label, -1))));
            raw.put("workload", workload);
            raw.put("failures", FAILURES.values().stream().map(record -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("condition", record.condition());
                entry.put("format", record.format());
                entry.put("expected", record.expected());
                entry.put("count", record.count());
                entry.put("firstRunIndex", record.firstRunIndex());
                entry.put("lastRunIndex", record.lastRunIndex());
                entry.put("widgetType", record.failure().widgetType());
                entry.put("widgetId", record.failure().widgetId());
                entry.put("widgetTitle", record.failure().widgetTitle());
                entry.put("generatorId", record.failure().generatorId());
                entry.put("error", record.failure().error());
                return entry;
            }).toList());
            raw.put("samples", CAMPAIGN);
            raw.put("baseline", BASELINE.entrySet().stream().collect(
                    LinkedHashMap::new,
                    (map, entry) -> map.put(entry.getKey(),
                            Map.of("batchMs", entry.getValue()[0], "individualMs", entry.getValue()[1])),
                    LinkedHashMap::putAll));
            raw.put("paginationSlopeMsPerFile", paginationSlopeMsPerFile);
            out.append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(raw)).append("\n");
        } catch (Exception ex) {
            out.append("  (failed to serialise raw data: ").append(ex).append(")\n");
        }

        try {
            java.nio.file.Path path = java.nio.file.Path.of(reportPath()).toAbsolutePath();
            java.nio.file.Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
            System.out.println();
            System.out.println("Full report written to: " + path);
            if (!PROBLEMS.isEmpty()) {
                System.out.println("It contains " + PROBLEMS.size() + " problem(s) with full failure detail.");
            }
        } catch (Exception ex) {
            System.out.println("FAILED to write the report file to " + reportPath() + ": " + ex);
            System.out.println(out);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "...";
    }

    /**
     * Central entry point. Runs every phase in order and always prints the report, even if a
     * phase fails: the failure is recorded as a problem and the completed runs are kept.
     *
     * Equivalent to running the class under JUnit; provided so the campaign can also be started
     * straight from an IDE.
     */
    public static void main(String[] args) {
        if (!serverIsUp()) {
            System.out.println("UDAV is not reachable at " + BASE_URL
                    + " - start it (and set -DUDAV_BASE_URL if it is elsewhere) before running the campaign");
            return;
        }
        if (!pipelineExists(PIPELINES.getFirst().id())) {
            System.out.println("Evaluation pipelines are not imported - check that the pipeline_eval-*.json"
                    + " fixtures reached the pipeline table");
            return;
        }

        BatchExportEvaluationIT campaign = new BatchExportEvaluationIT();
        runPhase("workload descriptors", campaign::measureWorkloadDescriptorsImpl);
        runPhase("primary campaign", campaign::measurePrimaryCampaignImpl);
        runPhase("batch vs individual", campaign::measureBatchVersusIndividualRequestsImpl);
        runPhase("pagination depth", campaign::measurePaginationDepthEffectImpl);
        printPaperNumbers();
    }

    @FunctionalInterface
    private interface Phase {
        void run() throws Exception;
    }

    /** Used by main(): records the failure and carries on with the remaining phases. */
    private static void runPhase(String name, Phase phase) {
        System.out.println();
        System.out.println("### phase: " + name);
        try {
            phase.run();
        } catch (Exception ex) {
            problem("phase " + name, "aborted: " + ex);
        }
    }

    /**
     * Used by the JUnit entry points: records the failure, then rethrows so JUnit also marks
     * the phase red. Without the recording step the report would not mention a phase that threw.
     */
    private static void guard(String name, Phase phase) {
        try {
            phase.run();
        } catch (Exception ex) {
            problem("phase " + name, "aborted: " + ex);
            throw new IllegalStateException("phase " + name + " failed", ex);
        }
    }

    private static List<Sample> samplesFor(String format) {
        List<Sample> all = new ArrayList<>();
        CAMPAIGN.forEach((condition, samples) -> {
            if (condition.endsWith("|" + format)) all.addAll(samples);
        });
        return all;
    }
}
