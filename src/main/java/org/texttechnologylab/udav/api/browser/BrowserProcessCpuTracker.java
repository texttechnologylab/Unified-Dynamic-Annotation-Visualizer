package org.texttechnologylab.udav.api.browser;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Samples CPU time for the Chromium process tree of one pooled browser session.
 *
 * <p>The tracker discovers the browser root process via a unique launch marker and
 * then periodically samples that process plus all of its descendants. This captures
 * browser-side work that is invisible to JVM process CPU metrics.
 *
 * <p>Callers read snapshot deltas rather than a total accumulated until close: a pooled browser
 * outlives the request that used it, so "CPU since start" would fold in idle time between requests
 * and time spent serving other requests. Instead the tracker runs for the session's lifetime and
 * callers read {@link #snapshotCpuMs()} before and after their request, reporting the difference.
 * Sampling (rather than reading the tree twice) is still required because Chromium spawns and
 * reaps renderer subprocesses mid-request, and a dead process's CPU time is otherwise unreadable.
 */
public final class BrowserProcessCpuTracker implements AutoCloseable {

    private static final long DISCOVERY_TIMEOUT_MS = 2_000L;
    private static final long DEFAULT_SAMPLE_INTERVAL_MS = 250L;

    private final long sampleIntervalMs;
    private final List<ProcessHandle> roots;
    private final ScheduledExecutorService sampler;
    private final Map<Long, Long> lastSeenCpuNs = new ConcurrentHashMap<>();
    private final AtomicLong accumulatedCpuNs = new AtomicLong();

    private volatile boolean started;
    private volatile boolean closed;

    private BrowserProcessCpuTracker(List<ProcessHandle> roots, long sampleIntervalMs) {
        this.sampleIntervalMs = sampleIntervalMs > 0 ? sampleIntervalMs : DEFAULT_SAMPLE_INTERVAL_MS;
        this.roots = List.copyOf(roots);
        this.sampler = roots.isEmpty()
                ? null
                : Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
    }

    public static Set<Long> snapshotJvmDescendants() {
        Set<Long> pids = new LinkedHashSet<>();
        ProcessHandle.current().descendants().forEach(handle -> pids.add(handle.pid()));
        return pids;
    }

    public static BrowserProcessCpuTracker discover(
            Set<Long> baselineDescendants, String launchMarker, long sampleIntervalMs) {
        long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DISCOVERY_TIMEOUT_MS);
        List<ProcessHandle> roots = List.of();

        while (System.nanoTime() <= deadlineNs) {
            List<ProcessHandle> fresh = ProcessHandle.current().descendants()
                    .filter(handle -> !baselineDescendants.contains(handle.pid()))
                    .toList();

            // Preferred: match the launch marker in the process arguments. Exact even if several
            // browsers are launched at once.
            roots = fresh.stream()
                    .filter(handle -> isBrowserRoot(handle, launchMarker))
                    .toList();

            // Fallback: identify the browser by its executable among the processes that appeared
            // since the baseline. ProcessHandle cannot read the arguments of every browser build
            // (a snap-packaged Chromium reports zero arguments and a truncated command line), so
            // the marker can be invisible even though CPU accounting still works. Without this
            // fallback the browser-CPU measurement would report unavailable for those builds.
            if (roots.isEmpty()) {
                roots = browserRootsByExecutable(fresh);
            }

            if (!roots.isEmpty()) {
                break;
            }
            sleepQuietly(25L);
        }

        return new BrowserProcessCpuTracker(roots, sampleIntervalMs);
    }

    /**
     * Picks the topmost browser processes out of {@code candidates}: those whose executable looks
     * like a browser and whose parent is not itself one of them, i.e. the browser roots rather
     * than their renderer children.
     */
    private static List<ProcessHandle> browserRootsByExecutable(List<ProcessHandle> candidates) {
        List<ProcessHandle> browsers = candidates.stream()
                .filter(handle -> handle.info().command().map(command -> {
                    String lower = command.toLowerCase(java.util.Locale.ROOT);
                    return lower.contains("chrome") || lower.contains("chromium") || lower.contains("msedge");
                }).orElse(false))
                .toList();

        Set<Long> browserPids = new LinkedHashSet<>();
        browsers.forEach(handle -> browserPids.add(handle.pid()));

        return browsers.stream()
                .filter(handle -> handle.parent().map(parent -> !browserPids.contains(parent.pid())).orElse(true))
                .toList();
    }

    public void start() {
        if (closed || started || sampler == null) {
            return;
        }
        sampleOnce();
        sampler.scheduleAtFixedRate(this::sampleSafely, sampleIntervalMs, sampleIntervalMs, TimeUnit.MILLISECONDS);
        started = true;
    }

    /**
     * Cumulative CPU milliseconds observed for this browser tree so far, or {@code -1} when the
     * tree could not be discovered. Take the difference of two calls to attribute CPU to a request.
     */
    public long snapshotCpuMs() {
        if (roots.isEmpty()) {
            return -1;
        }
        sampleSafely();
        return accumulatedCpuNs.get() / 1_000_000;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (sampler != null) {
            sampler.shutdownNow();
        }
        sampleOnce();
    }

    private void sampleSafely() {
        try {
            sampleOnce();
        } catch (RuntimeException ignored) {
            // Metrics collection must never interfere with the export request.
        }
    }

    private synchronized void sampleOnce() {
        if (roots.isEmpty()) {
            return;
        }

        Set<ProcessHandle> tree = new LinkedHashSet<>();
        for (ProcessHandle root : roots) {
            tree.add(root);
            root.descendants().forEach(tree::add);
        }

        for (ProcessHandle handle : tree) {
            OptionalLong cpuNs = cpuTimeNs(handle);
            if (cpuNs.isEmpty()) {
                continue;
            }

            long current = cpuNs.getAsLong();
            Long previous = lastSeenCpuNs.put(handle.pid(), current);
            if (previous != null && current > previous) {
                accumulatedCpuNs.addAndGet(current - previous);
            }
        }
    }

    private static boolean isBrowserRoot(ProcessHandle handle, String launchMarker) {
        ProcessHandle.Info info = handle.info();
        String[] args = info.arguments().orElseGet(() -> new String[0]);
        boolean hasMarker = false;
        boolean hasTypeFlag = false;
        for (String arg : args) {
            if (arg != null && arg.contains(launchMarker)) {
                hasMarker = true;
            }
            if (arg != null && arg.startsWith("--type=")) {
                hasTypeFlag = true;
            }
        }
        return hasMarker && !hasTypeFlag;
    }

    private static OptionalLong cpuTimeNs(ProcessHandle handle) {
        return handle.info()
                .totalCpuDuration()
                .map(Duration::toNanos)
                .stream()
                .mapToLong(Long::longValue)
                .findFirst();
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "udav-browser-cpu-sampler");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
