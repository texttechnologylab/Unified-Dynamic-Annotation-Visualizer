package org.texttechnologylab.udav.api.export;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the injected export driver against a stand-in for the view runtime: chart objects
 * shaped like real widgets ({@code config}, optional {@code svg}, an {@code exports} handler whose
 * {@code startExport} routes through {@code downloadBlobs}/{@code downloadSingleBlob}).
 *
 * <p>This covers the parts of the export path that do not need a database: the capability
 * pre-check, concurrency, transport encoding, and manifest/file ordering.
 */
@Tag("browser")
class ExportScriptsTest {

    private static Playwright playwright;
    private static Browser browser;

    /** Mimics the widget/ExportHandler surface the driver monkey-patches. */
    private static final String FAKE_RUNTIME = """
            (() => {
              function makeChart(id, type, hasSvg, behaviour) {
                const chart = {
                  config: { id, type, title: id, generator: { id: 'gen-' + id } },
                  svg: hasSvg ? {} : undefined,
                };
                chart.exports = {
                  filename: id,
                  async startExport(format, bulk) {
                    if (behaviour === 'reject') {
                      throw new Error('boom ' + id);
                    }
                    if (behaviour === 'hang') {
                      return await new Promise(() => {});
                    }
                    await new Promise((r) => setTimeout(r, 20));
                    if (format === 'png') {
                      const bytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
                      await this.downloadBlobs([new Blob([bytes], { type: 'image/png' })], 'png');
                    } else if (bulk) {
                      await this.downloadBlobs([
                        new Blob(['<a>' + id + '-0</a>'], { type: 'image/svg+xml' }),
                        new Blob(['<a>' + id + '-1</a>'], { type: 'image/svg+xml' }),
                      ], format);
                    } else {
                      await this.downloadSingleBlob(
                        new Blob(['<a>' + id + '</a>'], { type: 'image/svg+xml' }), id + '.' + format);
                    }
                  },
                  downloadBlobs() {},
                  downloadSingleBlob() {},
                };
                return chart;
              }

              globalThis.__UDAV_VIEW_STATE__ = {
                charts: [
                  makeChart('alpha', 'BarChart', true, 'ok'),
                  makeChart('beta', 'HighlightText', false, 'ok'),
                  makeChart('gamma', 'PieChart', true, 'ok'),
                  makeChart('delta', 'LineChart', true, 'reject'),
                  makeChart('epsilon', 'BarChart', true, 'ok'),
                  makeChart('zeta', 'PieChart', true, 'ok'),
                  makeChart('eta', 'BarChart', true, 'hang'),
                ],
              };
            })();
            """;

    @BeforeAll
    static void setUp() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void tearDown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    private record Emitted(int index, int fileIndex, String name, String encoding, byte[] content) {}

    private record Run(List<Map<String, Object>> entries, List<Emitted> files) {}

    @SuppressWarnings("unchecked")
    private Run run(String format, boolean bulk, int concurrency, long timeoutMs, List<String> chartIds) {
        List<Emitted> emitted = new ArrayList<>();
        try (BrowserContext context = browser.newContext()) {
            context.exposeBinding("__udavEmitFile", (source, args) -> {
                Map<?, ?> p = (Map<?, ?>) args[0];
                String encoding = String.valueOf(p.get("encoding"));
                String data = String.valueOf(p.get("data"));
                emitted.add(new Emitted(
                        ((Number) p.get("index")).intValue(),
                        ((Number) p.get("fileIndex")).intValue(),
                        String.valueOf(p.get("name")),
                        encoding,
                        "base64".equals(encoding)
                                ? Base64.getDecoder().decode(data)
                                : data.getBytes(StandardCharsets.UTF_8)));
                return null;
            });

            Page page = context.newPage();
            page.evaluate(FAKE_RUNTIME);
            page.evaluate(ExportScripts.INSTALL);

            List<Map<String, Object>> selectors = new ArrayList<>();
            for (String id : chartIds) {
                Map<String, Object> selector = new LinkedHashMap<>();
                selector.put("id", id);
                selectors.add(selector);
            }

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("format", format);
            args.put("bulk", bulk);
            args.put("selectors", selectors);
            args.put("concurrency", concurrency);
            args.put("timeoutMs", (double) timeoutMs);

            Object result = page.evaluate(ExportScripts.RUN_ALL, args);
            List<Map<String, Object>> entries =
                    (List<Map<String, Object>>) ((Map<?, ?>) result).get("entries");
            return new Run(entries, emitted);
        }
    }

    @Test
    void textFormatsAreTransportedAsUtf8AndNotBase64() {
        Run run = run("svg", false, 4, 5_000, List.of("alpha"));

        assertEquals(1, run.files().size());
        assertEquals("utf8", run.files().getFirst().encoding());
        assertEquals("<a>alpha</a>", new String(run.files().getFirst().content(), StandardCharsets.UTF_8));
        assertTrue((Boolean) run.entries().getFirst().get("ok"));
    }

    @Test
    void binaryFormatsStillUseBase64AndRoundTripExactly() {
        Run run = run("png", false, 4, 5_000, List.of("alpha"));

        assertEquals("base64", run.files().getFirst().encoding());
        assertArrayEqualsPngHeader(run.files().getFirst().content());
    }

    private static void assertArrayEqualsPngHeader(byte[] actual) {
        byte[] expected = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "byte " + i);
        }
    }

    @Test
    void widgetWithoutSvgFailsFastInsteadOfWaitingOutTheTimeout() {
        long startedAt = System.nanoTime();
        // A generous timeout: if the pre-check regressed, this would take the full 10s.
        Run run = run("svg", false, 4, 10_000, List.of("beta"));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> entry = run.entries().getFirst();
        assertFalse((Boolean) entry.get("ok"));
        assertTrue(String.valueOf(entry.get("error")).contains("does not support svg"),
                "unexpected error: " + entry.get("error"));
        assertTrue(elapsedMs < 5_000, "capability pre-check did not fail fast: " + elapsedMs + "ms");
        assertTrue(run.files().isEmpty());
    }

    @Test
    void widgetWithoutSvgStillExportsDataFormats() {
        Run run = run("json", false, 4, 5_000, List.of("beta"));

        assertTrue((Boolean) run.entries().getFirst().get("ok"));
        assertEquals(1, run.files().size());
    }

    @Test
    void asyncRejectionSettlesImmediatelyRatherThanTimingOut() {
        long startedAt = System.nanoTime();
        Run run = run("svg", false, 4, 10_000, List.of("delta"));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> entry = run.entries().getFirst();
        assertFalse((Boolean) entry.get("ok"));
        assertTrue(String.valueOf(entry.get("error")).contains("boom delta"),
                "unexpected error: " + entry.get("error"));
        assertTrue(elapsedMs < 5_000, "rejection did not settle fast: " + elapsedMs + "ms");
    }

    @Test
    void manifestAndFilesStayInSelectorOrderUnderConcurrency() {
        Run run = run("svg", true, 4, 5_000, List.of("gamma", "alpha", "beta", "delta"));

        assertEquals(4, run.entries().size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i, ((Number) run.entries().get(i).get("index")).intValue());
        }
        // gamma and alpha succeed with two files each; beta (no svg) and delta (throws) fail.
        assertTrue((Boolean) run.entries().get(0).get("ok"));
        assertTrue((Boolean) run.entries().get(1).get("ok"));
        assertFalse((Boolean) run.entries().get(2).get("ok"));
        assertFalse((Boolean) run.entries().get(3).get("ok"));

        assertEquals(4, run.files().size());
        assertEquals(2, run.files().stream().filter(f -> f.index() == 0).count());
        assertEquals(2, run.files().stream().filter(f -> f.index() == 1).count());
    }

    @Test
    void concurrencyActuallyOverlapsWidgetExports() {
        List<String> four = List.of("alpha", "gamma", "epsilon", "zeta");

        long sequentialStart = System.nanoTime();
        run("svg", false, 1, 5_000, four);
        long sequentialMs = (System.nanoTime() - sequentialStart) / 1_000_000;

        long concurrentStart = System.nanoTime();
        run("svg", false, 4, 5_000, four);
        long concurrentMs = (System.nanoTime() - concurrentStart) / 1_000_000;

        // Each fake export sleeps 20ms, so serialised work dominates only in the sequential run.
        assertTrue(concurrentMs < sequentialMs,
                "expected concurrency to help: sequential=" + sequentialMs + "ms concurrent=" + concurrentMs + "ms");
    }

    @Test
    void repeatingOneChartInTheSelectorsIsSerialisedRatherThanDeadlocked() {
        // Overlapping exports of a single chart would clobber each other's patched download
        // sinks; the driver serialises per chart so both selectors still produce a file.
        Run run = run("svg", false, 4, 5_000, List.of("alpha", "alpha"));

        assertEquals(2, run.entries().size());
        assertTrue((Boolean) run.entries().get(0).get("ok"));
        assertTrue((Boolean) run.entries().get(1).get("ok"));
        assertEquals(2, run.files().size());
    }

    @Test
    void hangingExportIsCutOffByTheWidgetTimeout() {
        long startedAt = System.nanoTime();
        Run run = run("svg", false, 4, 300, List.of("eta"));
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        Map<String, Object> entry = run.entries().getFirst();
        assertFalse((Boolean) entry.get("ok"));
        assertTrue(String.valueOf(entry.get("error")).contains("Export timeout"),
                "unexpected error: " + entry.get("error"));
        assertTrue(elapsedMs < 5_000, "timeout did not fire in time: " + elapsedMs + "ms");
        assertTrue(run.files().isEmpty());
    }
}
