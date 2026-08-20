package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.api.browser.BrowserExecutableResolver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class BrowserExportService {

    private static final List<String> SUPPORTED_FORMATS = List.of("svg", "png", "tex", "csv", "json");
    private static final long EXPORT_TIMEOUT_MS = 45_000L;
    private static final String BASE_URL = System.getenv().getOrDefault("UDAV_BASE_URL", "http://localhost:8080");

    private final PipelineService pipelineService;
    private final BrowserExecutableResolver browserExecutableResolver;

    public BrowserExportService(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
        this.browserExecutableResolver = new BrowserExecutableResolver();
    }

    public WidgetExportResult exportWidget(String pipelineId, WidgetSelection selection, String format, boolean bulk) {
        String normalizedFormat = normalizeFormat(format);
        Objects.requireNonNull(selection, "widget selection must not be null");
        if (selection.id == null && selection.generatorId == null) {
            throw new IllegalArgumentException("Widget selection must contain at least an id or generatorId.");
        }

        try {
            try (Playwright playwright = Playwright.create()) {
                try (Browser browser = launchBrowser(playwright)) {
                    try (BrowserContext context = browser.newContext()) {
                        Page page = context.newPage();
                        openPipelinePage(page, pipelineId);
                        ExportCapture capture = captureWidgetExport(page, selection, normalizedFormat, bulk);
                        if (capture.error != null) {
                            return WidgetExportResult.failed(selection, capture.error);
                        }
                        return WidgetExportResult.success(selection, capture.files);
                    }
                }
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Headless widget export failed: " + rootCauseMessage(ex),
                    ex
            );
        }
    }

    public PipelineExportResult exportPipeline(String pipelineId, String format, boolean bulk) {
        String normalizedFormat = normalizeFormat(format);

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
        JsonNode widgets = pipeline.path("widgets");
        List<WidgetSelection> generatorWidgets = new ArrayList<>();
        if (widgets.isArray()) {
            for (JsonNode widget : widgets) {
                JsonNode generator = widget.path("generator");
                String generatorId = textOrNull(generator.path("id"));
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

        List<ExportedFile> files = new ArrayList<>();
        List<ExportFailure> failures = new ArrayList<>();

        try {
            try (Playwright playwright = Playwright.create()) {
                try (Browser browser = launchBrowser(playwright)) {
                    try (BrowserContext context = browser.newContext(
                            new Browser.NewContextOptions().setViewportSize(1600, 1000)
                    )) {
                        Page page = context.newPage();
                        openPipelinePage(page, pipelineId);

                        for (WidgetSelection selection : generatorWidgets) {
                            try {
                                ExportCapture capture = captureWidgetExport(page, selection, normalizedFormat, bulk);
                                if (capture.error != null) {
                                    failures.add(new ExportFailure(selection, capture.error));
                                    continue;
                                }
                                files.addAll(capture.files);
                            } catch (Exception error) {
                                failures.add(new ExportFailure(selection, error.getMessage() != null ? error.getMessage() : error.toString()));
                            }
                        }
                    }
                }
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Headless pipeline export failed: " + rootCauseMessage(ex),
                    ex
            );
        }

        return new PipelineExportResult(pipelineId, normalizedFormat, files, failures);
    }

    private Browser launchBrowser(Playwright playwright) {
        List<Path> candidates = browserExecutableResolver.resolveCandidates();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No Chromium/Edge executable found.");
        }

        List<String> errors = new ArrayList<>();
        for (Path executablePath : candidates) {
            try {
                BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setExecutablePath(executablePath);
                return playwright.chromium().launch(launchOptions);
            } catch (RuntimeException ex) {
                errors.add(executablePath + " -> " + rootCauseMessage(ex));
            }
        }

        try {
            return playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        } catch (RuntimeException ex) {
            errors.add("playwright-managed-browser -> " + rootCauseMessage(ex));
        }

        throw new IllegalStateException("Failed to launch any browser candidate: " + String.join(" | ", errors));
    }

    private void openPipelinePage(Page page, String pipelineId) {
        String url = BASE_URL + "/view/" + URLEncoder.encode(pipelineId, StandardCharsets.UTF_8);
        page.navigate(url);
        page.waitForLoadState(LoadState.LOAD);
        page.waitForTimeout(250);
        page.evaluate("async () => {\n" +
                "  for (let i = 0; i < 100; i++) {\n" +
                "    if (globalThis.__UDAV_READY__ && typeof globalThis.__UDAV_READY__.then === 'function') {\n" +
                "      await globalThis.__UDAV_READY__;\n" +
                "      return true;\n" +
                "    }\n" +
                "    await new Promise((resolve) => setTimeout(resolve, 50));\n" +
                "  }\n" +
                "  return true;\n" +
                "}");
    }

    private ExportCapture captureWidgetExport(Page page, WidgetSelection selection, String format, boolean bulk) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("format", format);
        args.put("bulk", bulk);
        args.put("selector", selection.toMap());
        Object evaluated = page.evaluate(exportScript(), args);
        return ExportCapture.fromEvaluated(evaluated);
    }

    private String exportScript() {
        return """
                async ({ format, bulk, selector }) => {
                  async function blobToBase64(blob) {
                    return await new Promise((resolve, reject) => {
                      const reader = new FileReader();
                      reader.onload = () => {
                        const value = String(reader.result || "");
                        const comma = value.indexOf(",");
                        resolve(comma >= 0 ? value.slice(comma + 1) : value);
                      };
                      reader.onerror = () => reject(reader.error || new Error("Failed to read blob as base64."));
                      reader.readAsDataURL(blob);
                    });
                  }

                  async function blobToFile(blob, name) {
                    return {
                      name,
                      contentType: blob.type || "application/octet-stream",
                      base64: await blobToBase64(blob),
                    };
                  }

                  function selectTarget(charts, selector) {
                    let target = null;
                    if (selector && selector.id) {
                      target = charts.find((chart) => chart?.config?.id === selector.id) || null;
                    }
                    if (!target && selector && selector.generatorId && selector.type) {
                      target = charts.find((chart) => {
                        return chart?.config?.generator?.id === selector.generatorId
                          && chart?.config?.type === selector.type;
                      }) || null;
                    }
                    if (!target && selector && selector.generatorId) {
                      target = charts.find((chart) => chart?.config?.generator?.id === selector.generatorId) || null;
                    }
                    return target;
                  }

                  async function exportChart(chart) {
                    return await new Promise((resolve) => {
                      let settled = false;
                      const handler = chart.exports;
                      if (!handler || typeof handler.startExport !== 'function') {
                        resolve({ ok: false, error: 'Widget export handler is not available.' });
                        return;
                      }

                      const originalSingle = handler.downloadSingleBlob;
                      const originalMany = handler.downloadBlobs;

                      const restore = () => {
                        handler.downloadSingleBlob = originalSingle;
                        handler.downloadBlobs = originalMany;
                      };

                      const timer = setTimeout(() => {
                        if (!settled) {
                          settled = true;
                          restore();
                          resolve({ ok: false, error: 'Export timeout after 45 seconds.' });
                        }
                      }, %d);

                      const finish = async (files, error) => {
                        if (settled) {
                          return;
                        }
                        settled = true;
                        clearTimeout(timer);
                        restore();
                        resolve(error ? { ok: false, error } : { ok: true, files });
                      };

                      handler.downloadSingleBlob = async (blob, name) => {
                        try {
                          const file = await blobToFile(blob, name || `${handler.filename}.${format}`);
                          await finish([file], null);
                        } catch (error) {
                          await finish([], String(error));
                        }
                      };

                      handler.downloadBlobs = async (blobs, type) => {
                        try {
                          const files = [];
                          for (let i = 0; i < blobs.length; i++) {
                            const blob = blobs[i];
                            const name = `${handler.filename}-${String(i).padStart(3, '0')}.${type}`;
                            files.push(await blobToFile(blob, name));
                          }
                          await finish(files, null);
                        } catch (error) {
                          await finish([], String(error));
                        }
                      };

                      try {
                        handler.startExport(format, bulk);
                      } catch (error) {
                        finish([], String(error));
                      }
                    });
                  }

                  const charts = globalThis.__UDAV_VIEW_STATE__?.charts || [];
                  const target = selectTarget(charts, selector);

                  if (!target) {
                    return {
                      ok: false,
                      error: 'Widget not found or not generator-backed in view state.',
                      files: [],
                    };
                  }

                  const result = await exportChart(target);
                  return {
                    ...result,
                    widget: {
                      id: target?.config?.id || null,
                      type: target?.config?.type || null,
                      title: target?.config?.title || null,
                      generatorId: target?.config?.generator?.id || null,
                    },
                  };
                }
                """.formatted(EXPORT_TIMEOUT_MS);
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

        private WidgetExportResult(WidgetSelection widget, List<ExportedFile> files, String error) {
            this.widget = widget;
            this.files = files;
            this.error = error;
        }

        public static WidgetExportResult success(WidgetSelection widget, List<ExportedFile> files) {
            return new WidgetExportResult(widget, files, null);
        }

        public static WidgetExportResult failed(WidgetSelection widget, String error) {
            return new WidgetExportResult(widget, List.of(), error);
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

        public PipelineExportResult(String pipelineId, String format, List<ExportedFile> files, List<ExportFailure> failures) {
            this.pipelineId = pipelineId;
            this.format = format;
            this.files = files;
            this.failures = failures;
        }
    }

    private static final class ExportCapture {
        final List<ExportedFile> files;
        final String error;

        private ExportCapture(List<ExportedFile> files, String error) {
            this.files = files;
            this.error = error;
        }

        static ExportCapture fromEvaluated(Object evaluated) {
            if (!(evaluated instanceof Map<?, ?> map)) {
                return new ExportCapture(List.of(), "Unexpected export response shape.");
            }

            Object okValue = map.get("ok");
            boolean ok = Boolean.TRUE.equals(okValue) || "true".equals(String.valueOf(okValue));
            String error = textValue(map.get("error"));
            if (!ok) {
                return new ExportCapture(List.of(), error != null ? error : "Export failed.");
            }

            List<ExportedFile> files = new ArrayList<>();
            Object filesValue = map.get("files");
            if (filesValue instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> fileMap)) {
                        continue;
                    }
                    String name = textValue(fileMap.get("name"));
                    String contentType = textValue(fileMap.get("contentType"));
                    String base64 = textValue(fileMap.get("base64"));
                    if (name == null || base64 == null) {
                        continue;
                    }
                    byte[] content = Base64.getDecoder().decode(base64);
                    files.add(new ExportedFile(name, contentType != null ? contentType : "application/octet-stream", content));
                }
            }
            return new ExportCapture(files, null);
        }

        private static String textValue(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value);
            return text == null || text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
        }
    }
}






