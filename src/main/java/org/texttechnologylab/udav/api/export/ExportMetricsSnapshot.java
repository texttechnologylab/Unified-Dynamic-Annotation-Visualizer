package org.texttechnologylab.udav.api.export;

/**
 * Measurements of one batch-export request, returned by {@code BrowserExportController} as the
 * {@code X-UDAV-Export-Metrics} response header when {@code app.export.metrics.enabled} is set.
 * The evaluation harness ({@code BatchExportEvaluationIT}) reads every field.
 *
 * @param wallMs          total time inside the export service
 * @param browserInitMs   borrowing a pooled session plus creating context and page
 * @param pageReadyMs     navigation until the view signals readiness
 * @param exportPhaseMs   wall time of the widget-export stage (widgets run concurrently)
 * @param widgetSpanMs    makespan of the widget exports as measured inside the page
 * @param widgetMedianMs  median per-widget duration measured inside the page
 * @param jvmCpuMs        JVM process CPU time consumed by the request
 * @param browserCpuMs    Chromium process-tree CPU time attributed to the request
 * @param heapPeakBytes   peak JVM heap during the request
 * @param allocatedBytes  bytes allocated by the JVM during the request
 * @param gcPauseMs       garbage-collection time during the request
 * @param gcCount         garbage collections during the request
 * @param outputBytes     uncompressed size of all exported artefacts
 * @param zipBytes        size of the returned archive; filled in by the controller
 * @param widgetsOk       widgets exported successfully
 * @param widgetsFailed   widgets that reported an error
 * @param concurrency     widgets exported in parallel
 * @param sessionReused   whether the browser came warm from the pool
 */
public record ExportMetricsSnapshot(
        long wallMs,
        long browserInitMs,
        long pageReadyMs,
        long exportPhaseMs,
        long widgetSpanMs,
        long widgetMedianMs,
        long jvmCpuMs,
        long browserCpuMs,
        long heapPeakBytes,
        long allocatedBytes,
        long gcPauseMs,
        long gcCount,
        long outputBytes,
        long zipBytes,
        int widgetsOk,
        int widgetsFailed,
        int concurrency,
        boolean sessionReused
) {

    public ExportMetricsSnapshot withZipBytes(long bytes) {
        return new ExportMetricsSnapshot(wallMs, browserInitMs, pageReadyMs, exportPhaseMs,
                widgetSpanMs, widgetMedianMs, jvmCpuMs, browserCpuMs, heapPeakBytes, allocatedBytes,
                gcPauseMs, gcCount, outputBytes, bytes, widgetsOk, widgetsFailed, concurrency, sessionReused);
    }

    /** Compact JSON for the response header; a fixed set of numbers, so no ObjectMapper needed. */
    public String toJson() {
        return "{"
                + "\"wallMs\":" + wallMs
                + ",\"browserInitMs\":" + browserInitMs
                + ",\"pageReadyMs\":" + pageReadyMs
                + ",\"exportPhaseMs\":" + exportPhaseMs
                + ",\"widgetSpanMs\":" + widgetSpanMs
                + ",\"widgetMedianMs\":" + widgetMedianMs
                + ",\"jvmCpuMs\":" + jvmCpuMs
                + ",\"browserCpuMs\":" + browserCpuMs
                + ",\"heapPeakBytes\":" + heapPeakBytes
                + ",\"allocatedBytes\":" + allocatedBytes
                + ",\"gcPauseMs\":" + gcPauseMs
                + ",\"gcCount\":" + gcCount
                + ",\"outputBytes\":" + outputBytes
                + ",\"zipBytes\":" + zipBytes
                + ",\"widgetsOk\":" + widgetsOk
                + ",\"widgetsFailed\":" + widgetsFailed
                + ",\"concurrency\":" + concurrency
                + ",\"sessionReused\":" + sessionReused
                + "}";
    }
}
