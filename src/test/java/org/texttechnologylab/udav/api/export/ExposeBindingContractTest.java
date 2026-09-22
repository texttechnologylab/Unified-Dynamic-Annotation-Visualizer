package org.texttechnologylab.udav.api.export;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Playwright behaviour the batch exporter's streaming transport depends on: that an
 * {@code exposeBinding} callback is dispatched while the owning thread is blocked inside
 * a synchronous {@code page.evaluate}, and that it runs on that same thread.
 *
 * <p>If this ever stops holding, {@code BrowserExportService} must fall back to returning
 * artefacts in waves from the evaluate result instead of streaming them.
 */
@Tag("browser")
class ExposeBindingContractTest {

    @Test
    void bindingIsDispatchedOnTheEvaluatingThreadDuringEvaluate() {
        List<String> received = new ArrayList<>();
        List<String> callbackThreads = new ArrayList<>();
        String mainThread = Thread.currentThread().getName();

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true))) {
                try (BrowserContext context = browser.newContext()) {
                    context.exposeBinding("__udavEmitFile", (source, args) -> {
                        callbackThreads.add(Thread.currentThread().getName());
                        received.add(String.valueOf(((Map<?, ?>) args[0]).get("name")));
                        return null;
                    });

                    Page page = context.newPage();
                    Object result = page.evaluate("""
                            async () => {
                              for (let i = 0; i < 5; i++) {
                                await globalThis.__udavEmitFile({ name: 'file-' + i });
                              }
                              return { entries: 5 };
                            }
                            """);

                    // The binding must have fired before evaluate returned, not after.
                    assertEquals(List.of("file-0", "file-1", "file-2", "file-3", "file-4"), received);
                    // Playwright maps whole JS numbers to Integer, not Double.
                    assertEquals(5, ((Number) ((Map<?, ?>) result).get("entries")).intValue());
                    assertTrue(callbackThreads.stream().allMatch(mainThread::equals),
                            "binding ran off the evaluating thread: " + callbackThreads);
                }
            }
        }
    }
}
