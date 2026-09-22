package org.texttechnologylab.udav.api.service;

import org.texttechnologylab.udav.api.export.ExportMetricsSnapshot;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight instrumentation for batch export API calls.
 *
 * <p>Measures wall-clock time, JVM CPU time, sampled browser-process CPU time, JVM heap peak,
 * allocation and GC counters, per-widget timings, and uncompressed output size. The controller
 * adds the ZIP size to the {@link #snapshot()} it returns in the response header.
 *
 * <p>Usage pattern inside {@code BrowserExportService.execute()}:
 * <pre>
 *   BatchExportMetrics metrics = new BatchExportMetrics(pipelineId, format, scope);
 *   metrics.setConcurrency(concurrency);
 *   metrics.start();
 *
 *   long bStart = System.nanoTime();
 *   // ... borrow a pooled session, create context + page ...
 *   metrics.recordBrowserInit(bStart);
 *
 *   long pStart = System.nanoTime();
 *   // ... openPipelinePage() ...
 *   metrics.recordPageReady(pStart);
 *
 *   long eStart = System.nanoTime();
 *   // ... one evaluate exporting every widget ...
 *   metrics.recordExportPhase(eStart);
 *
 *   // per-widget durations come from the page, because concurrent exports overlap
 *   metrics.recordWidget(entry.startMs, entry.endMs, bytes);
 *
 *   metrics.recordBrowserCpu(cpuAfter - cpuBefore);
 *   metrics.finish();
 *   metrics.print();
 * </pre>
 */
public final class BatchExportMetrics {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchExportMetrics.class);

    private final String pipelineId;
    private final String format;
    private final String scope;

    private long wallStartNs;
    private long wallEndNs;
    private long cpuStartNs = -1;
    private long cpuEndNs   = -1;

    private long browserInitMs = -1;
    private long pageReadyMs   = -1;
    private long browserCpuMs  = -1;
    private long exportPhaseMs = -1;

    private boolean enabled = true;
    private int  concurrency = 1;
    private boolean sessionReused;
    private int  viewportWidth;
    private int  viewportHeight;
    private double firstWidgetStartMs = Double.NaN;
    private double lastWidgetEndMs    = Double.NaN;

    private final List<Long> widgetTimesMs = new ArrayList<>();
    private long totalOutputBytes;
    private int  failureCount;

    // Allocation volume and GC pause, read as deltas of JVM-global counters. They belong to this
    // request only while nothing else runs in the JVM, which is how the evaluation is conducted.
    private long allocStartBytes = -1;
    private long allocEndBytes   = -1;
    private long gcTimeStartMs   = -1;
    private long gcTimeEndMs     = -1;
    private long gcCountStart    = -1;
    private long gcCountEnd      = -1;

    // --- Constructor ---

    public BatchExportMetrics(String pipelineId, String format, String scope) {
        this.pipelineId = pipelineId != null ? pipelineId : "";
        this.format     = format     != null ? format     : "";
        this.scope      = scope      != null ? scope      : "";
    }

    // --- Lifecycle ---

    /**
     * Call before the session is borrowed from the pool.
     * Resets JVM heap peak counters and records the CPU + wall-clock baseline.
     */
    public void start() {
        if (!enabled) {
            wallStartNs = System.nanoTime();
            return;
        }
        resetHeapPeaks();
        cpuStartNs = getCpuTimeNs();
        allocStartBytes = getAllocatedBytes();
        gcTimeStartMs   = getGcTimeMs();
        gcCountStart    = getGcCount();
        wallStartNs = System.nanoTime();
    }

    /**
     * @param launchStartNs {@code System.nanoTime()} recorded when the pooled session was
     *                      borrowed, before {@code ensureStarted()} and the context/page setup
     */
    public void recordBrowserInit(long launchStartNs) {
        browserInitMs = (System.nanoTime() - launchStartNs) / 1_000_000;
    }

    /**
     * @param pageStartNs {@code System.nanoTime()} recorded just before
     *                    {@code openPipelinePage()}
     */
    public void recordPageReady(long pageStartNs) {
        pageReadyMs = (System.nanoTime() - pageStartNs) / 1_000_000;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setConcurrency(int concurrency) { this.concurrency = Math.max(1, concurrency); }
    public void setSessionReused(boolean sessionReused) { this.sessionReused = sessionReused; }

    public void setViewport(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    /** @param exportStartNs {@code System.nanoTime()} recorded just before the export phase */
    public void recordExportPhase(long exportStartNs) {
        exportPhaseMs = (System.nanoTime() - exportStartNs) / 1_000_000;
    }

    /** Browser-process CPU attributed to this request, as a snapshot difference. */
    public void recordBrowserCpu(long browserCpuMs) {
        this.browserCpuMs = browserCpuMs;
    }

    /**
     * Records one successful widget export from the timestamps the page reported.
     *
     * <p>Under concurrency the JVM-side wall time around a widget is meaningless because widget
     * exports overlap, so durations are taken from {@code performance.now()} inside the page and
     * the overall makespan is tracked separately.
     */
    public void recordWidget(double startMs, double endMs, long outputBytes) {
        widgetTimesMs.add((long) Math.max(0d, endMs - startMs));
        totalOutputBytes += outputBytes;
        trackSpan(startMs, endMs);
    }

    public void recordWidgetFailure(double startMs, double endMs) {
        widgetTimesMs.add((long) Math.max(0d, endMs - startMs));
        failureCount++;
        trackSpan(startMs, endMs);
    }

    private void trackSpan(double startMs, double endMs) {
        if (Double.isNaN(firstWidgetStartMs) || startMs < firstWidgetStartMs) {
            firstWidgetStartMs = startMs;
        }
        if (Double.isNaN(lastWidgetEndMs) || endMs > lastWidgetEndMs) {
            lastWidgetEndMs = endMs;
        }
    }

    /**
     * Call after the browser context has been closed (outside the try-with-resources).
     * Captures final CPU time and wall clock.
     */
    public void finish() {
        wallEndNs = System.nanoTime();
        cpuEndNs  = getCpuTimeNs();
        allocEndBytes = getAllocatedBytes();
        gcTimeEndMs   = getGcTimeMs();
        gcCountEnd    = getGcCount();
    }

    /** Everything the evaluation harness reads, for the X-UDAV-Export-Metrics response header. */
    public ExportMetricsSnapshot snapshot() {
        List<Long> sorted = widgetTimesMs.stream().sorted().toList();
        long widgetMedianMs = sorted.isEmpty() ? -1 : sorted.get(sorted.size() / 2);

        return new ExportMetricsSnapshot(
                (wallEndNs - wallStartNs) / 1_000_000,
                browserInitMs,
                pageReadyMs,
                exportPhaseMs,
                widgetSpanMs(),
                widgetMedianMs,
                (cpuStartNs >= 0 && cpuEndNs >= 0) ? (cpuEndNs - cpuStartNs) / 1_000_000 : -1,
                browserCpuMs,
                heapPeakBytes(),
                delta(allocStartBytes, allocEndBytes),
                delta(gcTimeStartMs, gcTimeEndMs),
                delta(gcCountStart, gcCountEnd),
                totalOutputBytes,
                -1,
                widgetTimesMs.size() - failureCount,
                failureCount,
                concurrency,
                sessionReused);
    }

    private static long delta(long start, long end) {
        return (start >= 0 && end >= start) ? end - start : -1;
    }

    private static long getAllocatedBytes() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean sun) {
            try {
                return sun.getTotalThreadAllocatedBytes();
            } catch (RuntimeException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static long getGcTimeMs() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long time = gc.getCollectionTime();
            if (time > 0) total += time;
        }
        return total;
    }

    private static long getGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count > 0) total += count;
        }
        return total;
    }

    // --- Reporting ---

    /** Logs the full metrics report at INFO, ending with a compact block of the key values. */
    public void print() {
        if (!enabled) {
            return;
        }
        long wallMs  = (wallEndNs - wallStartNs) / 1_000_000;
        long cpuMs   = (cpuStartNs >= 0 && cpuEndNs >= 0)
                ? (cpuEndNs - cpuStartNs) / 1_000_000
                : -1;
        long heapPeak = heapPeakBytes();
        long heapMax  = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        int  procs    = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();

        int successCount = widgetTimesMs.size() - failureCount;

        LongSummaryStatistics ws = widgetTimesMs.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        StringBuilder out = new StringBuilder(2048);
        String SEP  = "=".repeat(67);
        String DASH = "-".repeat(67);

        out.append(System.lineSeparator());
        line(out, SEP);
        line(out, " BATCH EXPORT METRICS");
        line(out, SEP);
        line(out, " Scope      : %s%n",   scope);
        line(out, " Pipeline   : %s%n",   truncate(pipelineId, 62));
        line(out, " Format     : %s%n",   format);
        line(out, " Processors : %d%n",   procs);

        line(out, DASH);
        line(out, " TIMING");
        line(out, "   Wall clock total   : %,12d ms%n",   wallMs);
        line(out, "   Browser init       : %12s ms%n",    fmtMs(browserInitMs));
        line(out, "   Page load + ready  : %12s ms%n",    fmtMs(pageReadyMs));
        line(out, "   Export phase       : %12s ms%n",    fmtMs(exportPhaseMs));
        line(out, "   Widget span        : %12s ms%n",    fmtMs(widgetSpanMs()));
        if (ws.getCount() > 0) {
            line(out, "   Widget avg         : %,12.0f ms%n", ws.getAverage());
            line(out, "   Widget max         : %,12d ms%n",   ws.getMax());
            line(out, "   Widget min         : %,12d ms%n",   ws.getMin());
        }

        line(out, DASH);
        line(out, " WIDGETS");
        line(out, "   Exported (ok)      : %12d%n",   successCount);
        line(out, "   Failed             : %12d%n",   failureCount);
        line(out, "   Concurrency        : %12d%n",   concurrency);
        line(out, "   Timing basis       : %12s%n",
                concurrency > 1 ? "in-page" : "jvm-wall");

        line(out, DASH);
        line(out, " OUTPUT  (ZIP size is in the response header)");
        line(out, "   Uncompressed bytes : %,12d%n",      totalOutputBytes);
        line(out, "   Uncompressed (KB)  : %12.1f KB%n",  totalOutputBytes / 1024.0);

        line(out, DASH);
        line(out, " JVM / BROWSER");
        line(out, "   JVM CPU time       : %12s ms%n",
                cpuMs >= 0 ? String.format(Locale.ROOT, "%,d", cpuMs) : "n/a");
        line(out, "   Browser CPU time   : %12s ms%n",    fmtMs(browserCpuMs));
        line(out, "   Heap peak          : %12.1f MB%n",  heapPeak / (1024.0 * 1024.0));
        line(out, "   Heap max configured: %12.1f MB%n",  heapMax  / (1024.0 * 1024.0));
        line(out, "   Session reused     : %12s%n",       sessionReused ? "yes" : "no");
        line(out, "   Viewport           : %12s%n",       viewport());

        line(out, SEP);
        line(out, " TABLE VALUES  (ZIP size is in the response header)");
        line(out, "   Wall (ms)           : %d%n",    wallMs);
        line(out, "   Browser init (ms)   : %s%n",    fmtMs(browserInitMs));
        line(out, "   Page ready (ms)     : %s%n",    fmtMs(pageReadyMs));
        if (ws.getCount() > 0) {
            line(out, "   Avg/widget (ms)     : %.0f%n", ws.getAverage());
            line(out, "   Max/widget (ms)     : %d%n",   ws.getMax());
        }
        line(out, "   JVM CPU (ms)        : %s%n",    cpuMs >= 0 ? cpuMs : "n/a");
        line(out, "   Browser CPU (ms)    : %s%n",    browserCpuMs >= 0 ? browserCpuMs : "n/a");
        line(out, "   Heap peak (MB)      : %.1f%n",  heapPeak / (1024.0 * 1024.0));
        line(out, "   Output (KB)         : %.1f%n",  totalOutputBytes / 1024.0);
        line(out, "   Widgets (ok)        : %d%n",    successCount);
        line(out, "   Failures            : %d%n",    failureCount);
        // Order matters: parsers match these rows by label and position, so new rows go last.
        line(out, "   Export phase (ms)   : %s%n",    fmtMs(exportPhaseMs));
        line(out, "   Widget span (ms)    : %s%n",    fmtMs(widgetSpanMs()));
        line(out, "   Concurrency         : %d%n",    concurrency);
        line(out, "   Session reused      : %d%n",    sessionReused ? 1 : 0);
        line(out, "   Viewport            : %s%n",    viewport());
        line(out, SEP);
        LOGGER.info("{}", out);
    }

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(String.format(Locale.ROOT, format, args));
        if (!format.endsWith("%n")) out.append(System.lineSeparator());
    }

    /** Makespan of the concurrent export phase measured inside the page. */
    private long widgetSpanMs() {
        if (Double.isNaN(firstWidgetStartMs) || Double.isNaN(lastWidgetEndMs)) {
            return -1;
        }
        return (long) Math.max(0d, lastWidgetEndMs - firstWidgetStartMs);
    }

    private String viewport() {
        return viewportWidth > 0 && viewportHeight > 0
                ? viewportWidth + "x" + viewportHeight
                : "n/a";
    }

    // --- JVM helpers ---

    private static void resetHeapPeaks() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                try { pool.resetPeakUsage(); } catch (Exception ignored) {}
            }
        }
    }

    private static long heapPeakBytes() {
        long total = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                MemoryUsage u = pool.getPeakUsage();
                if (u != null) total += Math.max(0L, u.getUsed());
            }
        }
        return total;
    }

    /**
     * Returns the JVM process CPU time in nanoseconds via the
     * {@code com.sun.management.OperatingSystemMXBean} extension, or {@code -1}
     * if the JVM does not expose it.
     */
    private static long getCpuTimeNs() {
        java.lang.management.OperatingSystemMXBean os =
                ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuTime();
        }
        return -1;
    }

    // --- Formatting helpers ---

    private static String fmtMs(long ms) {
        return ms >= 0 ? String.format(Locale.ROOT, "%,d", ms) : "n/a";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
