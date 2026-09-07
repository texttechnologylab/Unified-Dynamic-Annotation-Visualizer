package org.texttechnologylab.udav.api.browser;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.api.export.ExportProperties;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("browser")
class BrowserSessionPoolTest {

    private static ExportProperties properties(int maxSessions, int maxWaiters, long borrowTimeoutMs) {
        ExportProperties properties = new ExportProperties();
        properties.getPool().setMaxSessions(maxSessions);
        properties.getPool().setMaxWaiters(maxWaiters);
        properties.getPool().setBorrowTimeoutMs(borrowTimeoutMs);
        properties.getPool().setIdleTimeoutMs(0);
        // The CPU sampler is irrelevant here and only slows the test down.
        properties.getMetrics().setEnabled(false);
        return properties;
    }

    @Test
    void secondBorrowReusesTheSameBrowserRatherThanRelaunching() throws Exception {
        try (BrowserSessionPool pool = new BrowserSessionPool(properties(1, 4, 30_000))) {
            AtomicReference<Object> first = new AtomicReference<>();
            AtomicReference<Object> second = new AtomicReference<>();

            pool.withSession(session -> {
                first.set(session.browser());
                return null;
            });
            pool.withSession(session -> {
                second.set(session.browser());
                return null;
            });

            assertNotNull(first.get());
            // The browser must survive across requests; that is what the pool is for.
            assertSame(first.get(), second.get());
        }
    }

    @Test
    void everyPlaywrightCallRunsOnTheSessionsOwningThread() throws Exception {
        List<String> threads = new CopyOnWriteArrayList<>();

        try (BrowserSessionPool pool = new BrowserSessionPool(properties(1, 4, 30_000))) {
            pool.withSession(session -> session.call(() -> {
                threads.add(Thread.currentThread().getName());
                // Touching a Playwright object off its owning thread is undefined behaviour;
                // this asserts the pool never lets that happen.
                return session.browser().isConnected();
            }));
        }

        assertEquals(1, threads.size());
        assertTrue(threads.getFirst().startsWith("udav-export-session-"),
                "Playwright work ran on " + threads.getFirst());
    }

    @Test
    void loadIsShedWithServiceUnavailableInsteadOfQueueingUnbounded() throws Exception {
        // One session, maxWaiters=0: the second caller must be rejected, not parked.
        try (BrowserSessionPool pool = new BrowserSessionPool(properties(1, 0, 30_000))) {
            CountDownLatch holding = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            Thread holder = new Thread(() -> {
                try {
                    pool.withSession(session -> {
                        holding.countDown();
                        release.await(20, TimeUnit.SECONDS);
                        return null;
                    });
                } catch (Exception ignored) {
                    // the holder's own outcome is not what this test is about
                }
            });
            holder.start();

            try {
                assertTrue(holding.await(60, TimeUnit.SECONDS), "session was never borrowed");

                ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                        () -> pool.withSession(session -> null));
                assertEquals(503, thrown.getStatusCode().value());
            } finally {
                release.countDown();
                holder.join(TimeUnit.SECONDS.toMillis(30));
            }
        }
    }
}
