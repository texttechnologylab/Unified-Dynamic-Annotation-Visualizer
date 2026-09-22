package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.api.browser.BrowserProcessCpuTracker;
import org.texttechnologylab.udav.api.browser.BrowserSession;
import org.texttechnologylab.udav.api.browser.BrowserSessionPool;
import org.texttechnologylab.udav.api.export.ExportMetricsSnapshot;
import org.texttechnologylab.udav.api.export.ExportProperties;
import org.texttechnologylab.udav.api.export.ExportScripts;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class BrowserExportService {

    private static final List<String> SUPPORTED_FORMATS = List.of("svg", "png", "tex", "csv", "json");

    private final PipelineService pipelineService;
    private final BrowserSessionPool sessionPool;
    private final ExportProperties properties;

    public BrowserExportService(
            PipelineService pipelineService,
            BrowserSessionPool sessionPool,
            ExportProperties properties) {
        this.pipelineService = pipelineService;
        this.sessionPool = sessionPool;
        this.properties = properties;
    }

    public WidgetExportResult exportWidget(String pipelineId, WidgetSelection selection, String format, boolean bulk) {
        String normalizedFormat = normalizeFormat(format);
        Objects.requireNonNull(selection, "widget selection must not be null");
        if (selection.id == null && selection.generatorId == null) {
            throw new IllegalArgumentException("Widget selection must contain at least an id or generatorId.");
        }

        ExportRun run = execute(pipelineId, List.of(selection), normalizedFormat, bulk, "widget");
        if (!run.failures().isEmpty()) {
            return WidgetExportResult.failed(selection, run.failures().getFirst().error, run.metrics());
        }
        return WidgetExportResult.success(selection, run.files(), run.metrics());
    }

    public PipelineExportResult exportPipeline(String pipelineId, String format, boolean bulk) {
        String normalizedFormat = normalizeFormat(format);
        List<WidgetSelection> generatorWidgets = generatorWidgets(pipelineId);

        ExportRun run = execute(pipelineId, generatorWidgets, normalizedFormat, bulk, "pipeline");
        return new PipelineExportResult(pipelineId, normalizedFormat, run.files(), run.failures(), run.metrics());
    }

    private List<WidgetSelection> generatorWidgets(String pipelineId) {
        JsonNode pipeline;
        try {
            pipeline = pipelineService.get(pipelineId);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load pipeline for export: " + pipelineId,
                    ex
            );
        }

        List<WidgetSelection> generatorWidgets = new ArrayList<>();
        JsonNode widgets = pipeline.path("widgets");
        if (widgets.isArray()) {
            for (JsonNode widget : widgets) {
                String generatorId = textOrNull(widget.path("generator").path("id"));
                if (generatorId == null) {
                    continue;
                }

                WidgetSelection selection = new WidgetSelection();
                selection.id = textOrNull(widget.path("id"));
                selection.type = textOrNull(widget.path("type"));
                selection.title = textOrNull(widget.path("title"));
                selection.generatorId = generatorId;
                generatorWidgets.add(selection);
            }
        }
        return generatorWidgets;
    }

    /**
     * Runs one export against a pooled browser session.
     *
     * <p>The browser and driver come from {@link BrowserSessionPool} and outlive the request; the
     * context and page do not, which keeps each export isolated. All Playwright calls happen inside
     * {@link BrowserSession#call} so they run on the session's owning thread.
     */
    private ExportRun execute(
            String pipelineId, List<WidgetSelection> selections, String format, boolean bulk, String scope) {

        ExportRun[] completed = new ExportRun[1];
        BatchExportMetrics metrics = new BatchExportMetrics(pipelineId, format, scope);
        metrics.setEnabled(properties.getMetrics().isEnabled());
        metrics.setConcurrency(effectiveConcurrency(selections.size()));
        metrics.setViewport(properties.getViewportWidth(), properties.getViewportHeight());
        metrics.start();

        try {
            completed[0] = sessionPool.withSession(session -> {
                long borrowedAtNs = System.nanoTime();
                boolean reused = session.browser() != null;
                session.ensureStarted();
                metrics.setSessionReused(reused);

                BrowserProcessCpuTracker cpuTracker = session.cpuTracker();
                long cpuBeforeMs = cpuTracker != null ? cpuTracker.snapshotCpuMs() : -1;

                try {
                    return session.call(() -> {
                        Browser browser = session.browser();
                        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                                .setViewportSize(properties.getViewportWidth(), properties.getViewportHeight()))) {

                            FileSink sink = new FileSink();
                            context.exposeBinding("__udavEmitFile", (source, args) -> sink.accept(args));

                            Page page = context.newPage();
                            metrics.recordBrowserInit(borrowedAtNs);

                            long pageStartNs = System.nanoTime();
                            openPipelinePage(page, pipelineId);
                            metrics.recordPageReady(pageStartNs);

                            long exportStartNs = System.nanoTime();
                            Object evaluated = page.evaluate(ExportScripts.RUN_ALL, runArgs(selections, format, bulk));
                            metrics.recordExportPhase(exportStartNs);

                            return collect(selections, evaluated, sink, metrics);
                        }
                    });
                } finally {
                    if (cpuTracker != null && cpuBeforeMs >= 0) {
                        metrics.recordBrowserCpu(cpuTracker.snapshotCpuMs() - cpuBeforeMs);
                    }
                }
            });
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Headless " + scope + " export failed: " + rootCauseMessage(ex),
                    ex
            );
        } finally {
            metrics.finish();
            metrics.print();
        }

        // The snapshot is attached after the try/finally: inside the try it would be taken
        // before metrics.finish() runs and carry a bogus wall time.
        if (completed[0] == null) {
            return null;
        }
        return properties.getMetrics().isEnabled() ? completed[0].withMetrics(metrics.snapshot()) : completed[0];
    }

    private int effectiveConcurrency(int widgetCount) {
        return Math.max(1, Math.min(properties.getConcurrency(), Math.max(1, widgetCount)));
    }

    private Map<String, Object> runArgs(List<WidgetSelection> selections, String format, boolean bulk) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("format", format);
        args.put("bulk", bulk);
        args.put("selectors", selections.stream().map(WidgetSelection::toMap).toList());
        args.put("concurrency", effectiveConcurrency(selections.size()));
        // Playwright's Java value serializer rejects Long; doubles round-trip as JS numbers.
        args.put("timeoutMs", (double) properties.getWidgetTimeoutMs());
        return args;
    }

    private void openPipelinePage(Page page, String pipelineId) {
        String url = properties.getBaseUrl()
                + "/view/" + URLEncoder.encode(pipelineId, StandardCharsets.UTF_8)
                + "?export=1";

        // DOMCONTENTLOADED rather than LOAD: LOAD blocks on every subresource, including the
        // external image/video/iframe URLs Static* widgets point at, which are never exported.
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

        // Explicit polling interval: waitForFunction defaults to requestAnimationFrame, which an
        // occluded headless renderer may throttle or never fire.
        page.waitForFunction(ExportScripts.READY_PREDICATE, null, new Page.WaitForFunctionOptions()
                .setTimeout(properties.getReadyTimeoutMs())
                .setPollingInterval(20));

        page.evaluate(ExportScripts.INSTALL);
    }

    /**
     * Rebuilds the result in selector order.
     *
     * <p>Files arrive through the binding in completion order, which under concurrency is not
     * selector order. {@code BrowserExportController.zipFiles} names ZIP entries by list position,
     * so returning them unsorted would rename every entry from one run to the next.
     */
    private ExportRun collect(
            List<WidgetSelection> selections, Object evaluated, FileSink sink, BatchExportMetrics metrics) {

        List<ExportedFile> files = new ArrayList<>();
        List<ExportFailure> failures = new ArrayList<>();

        Map<Integer, List<FileSink.Entry>> byWidget = sink.groupedByWidget();
        List<Map<String, Object>> entries = manifestEntries(evaluated);

        for (int index = 0; index < selections.size(); index++) {
            WidgetSelection selection = selections.get(index);
            Map<String, Object> entry = index < entries.size() ? entries.get(index) : null;

            if (entry == null) {
                failures.add(new ExportFailure(selection, "No export result was reported for this widget."));
                metrics.recordWidgetFailure(0, 0);
                continue;
            }

            double startMs = numberValue(entry.get("startMs"));
            double endMs = numberValue(entry.get("endMs"));

            if (!Boolean.TRUE.equals(entry.get("ok"))) {
                String error = textValue(entry.get("error"));
                failures.add(new ExportFailure(selection, error != null ? error : "Export failed."));
                metrics.recordWidgetFailure(startMs, endMs);
                continue;
            }

            List<FileSink.Entry> widgetFiles = byWidget.getOrDefault(index, List.of());
            long bytes = 0;
            for (FileSink.Entry file : widgetFiles) {
                files.add(new ExportedFile(file.name(), file.contentType(), file.content()));
                bytes += file.content().length;
            }
            metrics.recordWidget(startMs, endMs, bytes);
        }

        return new ExportRun(files, failures, null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> manifestEntries(Object evaluated) {
        if (!(evaluated instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object entries = map.get("entries");
        if (!(entries instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(item instanceof Map<?, ?> entry ? (Map<String, Object>) entry : null);
        }
        return out;
    }

    /** Collects artefacts pushed out of the page by {@code __udavEmitFile}. */
    private static final class FileSink {

        record Entry(int widgetIndex, int fileIndex, String name, String contentType, byte[] content) {
        }

        private final List<Entry> entries = new ArrayList<>();

        /**
         * Invoked from Playwright's dispatch loop, which is pumped by the session thread while it
         * is blocked inside {@code page.evaluate}. It therefore runs on that same thread and needs
         * no synchronization, but it also blocks the pump for its whole duration, stalling every
         * other in-flight widget export. So it does nothing beyond decoding and appending: no
         * Playwright calls (which would deadlock), no I/O.
         */
        Object accept(Object... args) {
            if (args.length == 0 || !(args[0] instanceof Map<?, ?> payload)) {
                return null;
            }

            String name = textValue(payload.get("name"));
            String data = textValue(payload.get("data"));
            if (name == null || data == null) {
                return null;
            }

            String contentType = textValue(payload.get("contentType"));
            byte[] content = "base64".equals(textValue(payload.get("encoding")))
                    ? Base64.getDecoder().decode(data)
                    : data.getBytes(StandardCharsets.UTF_8);

            entries.add(new Entry(
                    (int) numberValue(payload.get("index")),
                    (int) numberValue(payload.get("fileIndex")),
                    name,
                    contentType != null ? contentType : "application/octet-stream",
                    content));
            return null;
        }

        Map<Integer, List<Entry>> groupedByWidget() {
            Map<Integer, List<Entry>> grouped = new LinkedHashMap<>();
            for (Entry entry : entries) {
                grouped.computeIfAbsent(entry.widgetIndex(), key -> new ArrayList<>()).add(entry);
            }
            grouped.values().forEach(list -> list.sort(Comparator.comparingInt(Entry::fileIndex)));
            return grouped;
        }
    }

    private record ExportRun(
            List<ExportedFile> files,
            List<ExportFailure> failures,
            ExportMetricsSnapshot metrics) {

        ExportRun withMetrics(ExportMetricsSnapshot snapshot) {
            return new ExportRun(files, failures, snapshot);
        }
    }

    private static String normalizeFormat(String format) {
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        String normalized = format.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return normalized;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0d : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message;
    }

    public static final class WidgetSelection {
        public String id;
        public String type;
        public String title;
        public String generatorId;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("type", type);
            map.put("title", title);
            map.put("generatorId", generatorId);
            return map;
        }
    }

    public static final class ExportedFile {
        public final String name;
        public final String contentType;
        public final byte[] content;

        public ExportedFile(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = content;
        }
    }

    public static final class ExportFailure {
        public final WidgetSelection widget;
        public final String error;

        public ExportFailure(WidgetSelection widget, String error) {
            this.widget = widget;
            this.error = error;
        }
    }

    public static final class WidgetExportResult {
        public final WidgetSelection widget;
        public final List<ExportedFile> files;
        public final String error;
        /** Measurements of this request; null unless {@code app.export.metrics.enabled} is set. */
        public final ExportMetricsSnapshot metrics;

        private WidgetExportResult(
                WidgetSelection widget, List<ExportedFile> files, String error, ExportMetricsSnapshot metrics) {
            this.widget = widget;
            this.files = files;
            this.error = error;
            this.metrics = metrics;
        }

        public static WidgetExportResult success(
                WidgetSelection widget, List<ExportedFile> files, ExportMetricsSnapshot metrics) {
            return new WidgetExportResult(widget, files, null, metrics);
        }

        public static WidgetExportResult failed(
                WidgetSelection widget, String error, ExportMetricsSnapshot metrics) {
            return new WidgetExportResult(widget, List.of(), error, metrics);
        }

        public static WidgetExportResult success(WidgetSelection widget, List<ExportedFile> files) {
            return new WidgetExportResult(widget, files, null, null);
        }

        public boolean hasError() {
            return error != null;
        }
    }

    public static final class PipelineExportResult {
        public final String pipelineId;
        public final String format;
        public final List<ExportedFile> files;
        public final List<ExportFailure> failures;

        /** Measurements of this request; null unless {@code app.export.metrics.enabled} is set. */
        public final ExportMetricsSnapshot metrics;

        public PipelineExportResult(
                String pipelineId, String format, List<ExportedFile> files,
                List<ExportFailure> failures, ExportMetricsSnapshot metrics) {
            this.pipelineId = pipelineId;
            this.format = format;
            this.files = files;
            this.failures = failures;
            this.metrics = metrics;
        }

        public PipelineExportResult(String pipelineId, String format, List<ExportedFile> files, List<ExportFailure> failures) {
            this(pipelineId, format, files, failures, null);
        }
    }
}
