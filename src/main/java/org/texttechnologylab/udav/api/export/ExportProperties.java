package org.texttechnologylab.udav.api.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tuning knobs for the headless batch export API ({@code /api/batch}).
 *
 * <p>Defaults are chosen for a single-node deployment sharing a JVM with the pages it renders.
 * {@link Pool#maxSessions} and {@link #concurrency} both feed back into this same
 * application: an export holds one Tomcat worker while its headless page issues {@code /api/data}
 * and {@code /api/convertions/*} requests back to it. Chromium caps concurrent connections per
 * origin at roughly six, so batch-attributable worker usage is bounded by about
 * {@code (maxSessions + maxWaiters) + 6 * maxSessions}. Keep that comfortably under
 * {@code server.tomcat.threads.max}.
 */
@ConfigurationProperties("app.export")
public class ExportProperties {

    /** Origin the headless browser uses to reach this application. */
    private String baseUrl = "http://localhost:8080";

    /** Widgets exported concurrently inside one page. 1 restores strictly sequential export. */
    private int concurrency = 4;

    /** Safety net for a widget export that never settles, in milliseconds. */
    private long widgetTimeoutMs = 30_000L;

    /** How long to wait for the view to signal readiness, in milliseconds. */
    private long readyTimeoutMs = 30_000L;

    /** Viewport used when rendering the pipeline for export. */
    private int viewportWidth = 1600;
    private int viewportHeight = 1000;

    private final Pool pool = new Pool();
    private final Browser browser = new Browser();
    private final Metrics metrics = new Metrics();

    public static class Pool {
        /** Browsers kept alive for reuse. Each costs roughly 150-300 MB RSS. */
        private int maxSessions = 2;

        /** Requests allowed to queue for a session before the API sheds load with 503. */
        private int maxWaiters = 8;

        /** How long a request waits for a free session before failing, in milliseconds. */
        private long borrowTimeoutMs = 120_000L;

        /** Idle time after which a pooled browser is closed, in milliseconds. 0 disables eviction. */
        private long idleTimeoutMs = 300_000L;

        public int getMaxSessions() { return maxSessions; }
        public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
        public int getMaxWaiters() { return maxWaiters; }
        public void setMaxWaiters(int maxWaiters) { this.maxWaiters = maxWaiters; }
        public long getBorrowTimeoutMs() { return borrowTimeoutMs; }
        public void setBorrowTimeoutMs(long borrowTimeoutMs) { this.borrowTimeoutMs = borrowTimeoutMs; }
        public long getIdleTimeoutMs() { return idleTimeoutMs; }
        public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }
    }

    public static class Browser {
        /**
         * Disables the Chromium sandbox. Off by default: it is a real security control, and the
         * decision belongs to the deployment. Commonly required in containers that cannot grant
         * the user namespaces the sandbox needs.
         */
        private boolean noSandbox = false;

        /** Headless Chromium rasterises on CPU anyway; disabling GPU avoids probing for one. */
        private boolean disableGpu = true;

        public boolean isNoSandbox() { return noSandbox; }
        public void setNoSandbox(boolean noSandbox) { this.noSandbox = noSandbox; }
        public boolean isDisableGpu() { return disableGpu; }
        public void setDisableGpu(boolean disableGpu) { this.disableGpu = disableGpu; }
    }

    public static class Metrics {
        /**
         * Per-request measurements: a block in the log and the {@code X-UDAV-Export-Metrics}
         * response header, which the evaluation harness reads. Off by default.
         */
        private boolean enabled = false;

        /** Interval of the browser-process CPU sampler, in milliseconds. */
        private long cpuSampleIntervalMs = 250L;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getCpuSampleIntervalMs() { return cpuSampleIntervalMs; }
        public void setCpuSampleIntervalMs(long cpuSampleIntervalMs) { this.cpuSampleIntervalMs = cpuSampleIntervalMs; }
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public long getWidgetTimeoutMs() { return widgetTimeoutMs; }
    public void setWidgetTimeoutMs(long widgetTimeoutMs) { this.widgetTimeoutMs = widgetTimeoutMs; }
    public long getReadyTimeoutMs() { return readyTimeoutMs; }
    public void setReadyTimeoutMs(long readyTimeoutMs) { this.readyTimeoutMs = readyTimeoutMs; }
    public int getViewportWidth() { return viewportWidth; }
    public void setViewportWidth(int viewportWidth) { this.viewportWidth = viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public void setViewportHeight(int viewportHeight) { this.viewportHeight = viewportHeight; }
    public Pool getPool() { return pool; }
    public Browser getBrowser() { return browser; }
    public Metrics getMetrics() { return metrics; }
}
