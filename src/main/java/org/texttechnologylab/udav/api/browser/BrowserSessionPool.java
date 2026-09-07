package org.texttechnologylab.udav.api.browser;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.api.export.ExportProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool of reusable {@link BrowserSession}s.
 *
 * <p>Sessions are created lazily on first use and closed again once they have been idle for
 * {@code app.export.pool.idle-timeout-ms}, so an idle server holds no Chromium. Only the driver
 * and browser are pooled; each request gets a fresh {@code BrowserContext} and page, which
 * costs milliseconds and keeps requests isolated from each other.
 *
 * <p>Admission control: an export holds a Tomcat worker while its headless page calls back into
 * this same application for {@code /api/data}. If those calls fail, the frontend's
 * {@code getData} swallows the error and substitutes bundled demo data, and the export succeeds
 * with the wrong content. Bounding the number of queued requests and rejecting the excess with a
 * 503 prevents that.
 */
@Component
public class BrowserSessionPool implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserSessionPool.class);

    private final ExportProperties properties;
    private final BrowserExecutableResolver executableResolver;

    private final BlockingQueue<BrowserSession> idle;
    private final Semaphore admission;
    private final List<BrowserSession> allSessions = new ArrayList<>();
    private final AtomicInteger sessionCounter = new AtomicInteger();
    private final ScheduledExecutorService evictor;

    private volatile boolean closed;

    public BrowserSessionPool(ExportProperties properties) {
        this.properties = properties;
        this.executableResolver = new BrowserExecutableResolver();
        this.idle = new ArrayBlockingQueue<>(Math.max(1, properties.getPool().getMaxSessions()));
        this.admission = new Semaphore(
                Math.max(1, properties.getPool().getMaxSessions()) + Math.max(0, properties.getPool().getMaxWaiters()),
                true);

        // The application does not enable Spring scheduling, so @Scheduled would not fire;
        // eviction runs on its own daemon scheduler instead.
        this.evictor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "udav-export-pool-evictor");
            thread.setDaemon(true);
            return thread;
        });
        long idleTimeoutMs = properties.getPool().getIdleTimeoutMs();
        if (idleTimeoutMs > 0) {
            long period = Math.max(1_000L, idleTimeoutMs / 4);
            evictor.scheduleWithFixedDelay(this::evictIdle, period, period, TimeUnit.MILLISECONDS);
        }
    }

    /** Work to run against a started, healthy session. */
    @FunctionalInterface
    public interface SessionWork<T> {
        T run(BrowserSession session) throws Exception;
    }

    /** Borrows a session, guarantees it is started, runs {@code work}, and returns the session. */
    public <T> T withSession(SessionWork<T> work) throws Exception {
        if (closed) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Export service is shutting down.");
        }

        if (!admission.tryAcquire()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Export capacity exhausted; too many exports queued. Retry later.");
        }

        BrowserSession session = null;
        try {
            session = acquire();
            session.markUsed();
            return work.run(session);
        } finally {
            if (session != null) {
                session.markUsed();
                // Reuse depends on session health, not on whether the work succeeded: a missing
                // pipeline leaves a usable browser, a crashed renderer fails isHealthy().
                if (!closed && session.isHealthy()) {
                    if (!idle.offer(session)) {
                        discard(session);
                    }
                } else {
                    discard(session);
                }
            }
            admission.release();
        }
    }

    private BrowserSession acquire() throws Exception {
        long timeoutMs = properties.getPool().getBorrowTimeoutMs();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;

        while (true) {
            BrowserSession pooled = idle.poll();
            if (pooled == null) {
                BrowserSession created = createIfAllowed();
                if (created != null) {
                    try {
                        created.ensureStarted();
                    } catch (Exception ex) {
                        // Otherwise the failed session keeps occupying a pool slot forever.
                        discard(created);
                        throw ex;
                    }
                    return created;
                }

                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Timed out waiting for a free browser export session.");
                }
                pooled = idle.poll(remainingNanos, TimeUnit.NANOSECONDS);
                if (pooled == null) {
                    continue;
                }
            }

            if (!pooled.isHealthy()) {
                discard(pooled);
                continue;
            }
            return pooled;
        }
    }

    private synchronized BrowserSession createIfAllowed() {
        if (closed || allSessions.size() >= Math.max(1, properties.getPool().getMaxSessions())) {
            return null;
        }
        BrowserSession session = new BrowserSession(
                sessionCounter.incrementAndGet(), properties, executableResolver);
        allSessions.add(session);
        return session;
    }

    private synchronized void discard(BrowserSession session) {
        allSessions.remove(session);
        try {
            session.close();
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to close browser export session: {}", ex.toString());
        }
    }

    private void evictIdle() {
        long idleTimeoutNanos = properties.getPool().getIdleTimeoutMs() * 1_000_000L;
        if (idleTimeoutNanos <= 0) {
            return;
        }

        List<BrowserSession> keep = new ArrayList<>();
        BrowserSession candidate;
        while ((candidate = idle.poll()) != null) {
            if (System.nanoTime() - candidate.lastUsedAtNanos() >= idleTimeoutNanos) {
                LOGGER.info("Evicting idle browser export session");
                discard(candidate);
            } else {
                keep.add(candidate);
            }
        }
        for (BrowserSession session : keep) {
            if (!idle.offer(session)) {
                discard(session);
            }
        }
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        closed = true;
        evictor.shutdownNow();
        idle.clear();
        for (BrowserSession session : List.copyOf(allSessions)) {
            try {
                session.close();
            } catch (RuntimeException ex) {
                LOGGER.warn("Failed to close browser export session on shutdown: {}", ex.toString());
            }
        }
        allSessions.clear();
    }
}
