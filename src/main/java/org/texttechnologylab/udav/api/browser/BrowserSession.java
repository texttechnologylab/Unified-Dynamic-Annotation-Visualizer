package org.texttechnologylab.udav.api.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.texttechnologylab.udav.api.export.ExportProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One long-lived Playwright driver plus Chromium browser, pinned to a dedicated thread.
 *
 * <p>The thread is part of the resource because Playwright's Java binding is not
 * thread-safe: a {@link Playwright} instance and every object transitively derived from it
 * ({@link Browser}, {@code BrowserContext}, {@code Page}, handles) may only be touched by the
 * thread that created the {@code Playwright}. Sharing a pooled {@code Browser} across Tomcat
 * worker threads would therefore be invalid. So each session owns a single-thread executor and
 * every Playwright call (creation, use, liveness check and teardown) is submitted to it via
 * {@link #call(Callable)}. Callers hand in work and block; they never touch a Playwright object
 * on their own thread.
 */
public final class BrowserSession implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserSession.class);

    private final int index;
    private final ExportProperties properties;
    private final BrowserExecutableResolver executableResolver;
    private final ExecutorService thread;
    private final String launchMarker = "--udav-batch-export-session=" + UUID.randomUUID();

    // Playwright objects are only used on the session thread. browser and cpuTracker are
    // additionally read by callers (for reuse detection and CPU snapshots), so they must be
    // volatile for those reads to see the launch that happened on the session thread.
    private Playwright playwright;
    private volatile Browser browser;
    private volatile BrowserProcessCpuTracker cpuTracker;

    private volatile long lastUsedAtNanos = System.nanoTime();

    public BrowserSession(int index, ExportProperties properties, BrowserExecutableResolver executableResolver) {
        this.index = index;
        this.properties = properties;
        this.executableResolver = executableResolver;
        this.thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "udav-export-session-" + index);
            t.setDaemon(true);
            return t;
        });
    }

    /** Runs {@code task} on this session's owning thread and returns its result. */
    public <T> T call(Callable<T> task) throws Exception {
        try {
            return thread.submit(task).get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw ex;
        }
    }

    /** Launches the driver and browser if they are not up yet. Idempotent. */
    public void ensureStarted() throws Exception {
        call(() -> {
            if (browser != null && browser.isConnected()) {
                return null;
            }
            closeQuietlyOnOwnThread();
            Set<Long> baseline = BrowserProcessCpuTracker.snapshotJvmDescendants();
            // Playwright.create() runs the driver's "install", which downloads Chromium, Firefox
            // and WebKit (about 1 GB) into ~/.cache/ms-playwright unless told not to. With a
            // browser already on the machine that download is skipped; Playwright's own
            // Chromium is only fetched when none of the local candidates launches.
            List<Path> candidates = executableResolver.resolveCandidates();
            playwright = createPlaywright(!candidates.isEmpty());
            browser = launch(candidates);
            // Discovery and sampling run once per session rather than once per request, which
            // keeps the up-to-2s discovery wait off the request path.
            if (properties.getMetrics().isEnabled()) {
                cpuTracker = BrowserProcessCpuTracker.discover(
                        baseline, launchMarker, properties.getMetrics().getCpuSampleIntervalMs());
                cpuTracker.start();
            }
            LOGGER.info("Started browser export session {}", index);
            return null;
        });
    }

    /** True when the browser is up and still connected. Evaluated on the owning thread. */
    public boolean isHealthy() {
        try {
            return Boolean.TRUE.equals(call(() -> browser != null && browser.isConnected()));
        } catch (Exception ex) {
            return false;
        }
    }

    public Browser browser() {
        return browser;
    }

    public BrowserProcessCpuTracker cpuTracker() {
        return cpuTracker;
    }

    public long lastUsedAtNanos() {
        return lastUsedAtNanos;
    }

    public void markUsed() {
        lastUsedAtNanos = System.nanoTime();
    }

    @Override
    public void close() {
        try {
            call(() -> {
                closeQuietlyOnOwnThread();
                return null;
            });
        } catch (Exception ex) {
            LOGGER.warn("Failed to close browser export session {}: {}", index, ex.toString());
        } finally {
            thread.shutdown();
            try {
                if (!thread.awaitTermination(15, TimeUnit.SECONDS)) {
                    thread.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                thread.shutdownNow();
            }
        }
    }

    /** Must only be called on the owning thread. */
    private void closeQuietlyOnOwnThread() {
        if (cpuTracker != null) {
            try {
                cpuTracker.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
            cpuTracker = null;
        }
        if (browser != null) {
            try {
                browser.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
            playwright = null;
        }
    }

    /** Must only be called on the owning thread, with {@link #playwright} created. */
    private Browser launch(List<Path> candidates) {
        List<String> errors = new ArrayList<>();

        for (Path executablePath : candidates) {
            try {
                return playwright.chromium().launch(launchOptions(executablePath));
            } catch (RuntimeException ex) {
                errors.add(executablePath + " -> " + rootCauseMessage(ex));
            }
        }

        if (!candidates.isEmpty()) {
            // The driver was created without the browser download; fetch it now for the fallback.
            LOGGER.warn("No local browser could be launched ({}); falling back to Playwright's own Chromium",
                    String.join(" | ", errors));
            playwright.close();
            playwright = createPlaywright(false);
        }
        try {
            return playwright.chromium().launch(launchOptions(null));
        } catch (RuntimeException ex) {
            errors.add("playwright-managed-browser -> " + rootCauseMessage(ex));
        }

        throw new IllegalStateException("Failed to launch any browser candidate: " + String.join(" | ", errors));
    }

    private static Playwright createPlaywright(boolean skipBrowserDownload) {
        Playwright.CreateOptions options = new Playwright.CreateOptions();
        if (skipBrowserDownload) {
            options.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        }
        return Playwright.create(options);
    }

    private BrowserType.LaunchOptions launchOptions(Path executablePath) {
        List<String> args = new ArrayList<>();
        args.add(launchMarker);

        // A headless page counts as occluded, so Chromium throttles its timers toward 1 Hz.
        // The export path is built on setTimeout and async callbacks, so this matters directly.
        args.add("--disable-background-timer-throttling");
        args.add("--disable-renderer-backgrounding");
        args.add("--disable-backgrounding-occluded-windows");

        // /dev/shm defaults to 64 MB in containers; large bulk exports crash the renderer without this.
        args.add("--disable-dev-shm-usage");

        args.add("--mute-audio");
        args.add("--no-first-run");
        args.add("--no-default-browser-check");
        args.add("--disable-extensions");
        args.add("--disable-component-update");
        args.add("--disable-crash-reporter");
        args.add("--disable-breakpad");

        // NOT set: --hide-scrollbars and device-scale flags. Chart SVGs are sized from
        // .dv-chart-area getBoundingClientRect(), so removing the scrollbar gutter would change the
        // width/height baked into every exported artefact.
        if (properties.getBrowser().isDisableGpu()) {
            args.add("--disable-gpu");
        }
        if (properties.getBrowser().isNoSandbox()) {
            args.add("--no-sandbox");
        }

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(args);
        if (executablePath != null) {
            launchOptions.setExecutablePath(executablePath);
        }
        return launchOptions;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message;
    }
}
