package org.texttechnologylab.udav.api.browser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

public class BrowserExecutableResolver {

    private static final List<String> CHROMIUM_COMMANDS = List.of(
            "chromium",
            "chromium-browser",
            "chromium-freeworld",
            "google-chrome",
            "google-chrome-stable"
    );

    private static final List<String> EDGE_COMMANDS = List.of(
            "microsoft-edge",
            "microsoft-edge-stable",
            "microsoft-edge-dev",
            "microsoft-edge-beta",
            "msedge"
    );

    private static final List<Path> CHROMIUM_PATHS = List.of(
            Path.of("/usr/bin/chromium"),
            Path.of("/usr/bin/chromium-browser"),
            Path.of("/snap/bin/chromium"),
            Path.of("/usr/lib/chromium/chromium"),
            Path.of("/opt/chromium/chromium"),
            Path.of("/opt/google/chrome/chrome")
    );

    private static final List<Path> EDGE_PATHS = List.of(
            Path.of("/usr/bin/microsoft-edge"),
            Path.of("/usr/bin/microsoft-edge-stable"),
            Path.of("/usr/bin/microsoft-edge-dev"),
            Path.of("/usr/bin/microsoft-edge-beta"),
            Path.of("/usr/bin/msedge"),
            Path.of("/opt/microsoft/msedge/msedge"),
            Path.of("/opt/microsoft/msedge-beta/msedge"),
            Path.of("/opt/microsoft/msedge-dev/msedge")
    );

    /** Application bundles on macOS and the default install locations on Windows. */
    private static final List<Path> PLATFORM_PATHS = platformPaths();

    private static List<Path> platformPaths() {
        List<Path> paths = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            paths.add(Path.of("/Applications/Chromium.app/Contents/MacOS/Chromium"));
            paths.add(Path.of("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
            paths.add(Path.of("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"));
        } else if (os.contains("win")) {
            for (String root : new String[]{System.getenv("PROGRAMFILES"), System.getenv("PROGRAMFILES(X86)"), System.getenv("LOCALAPPDATA")}) {
                if (root == null || root.isBlank()) continue;
                paths.add(Path.of(root, "Chromium", "Application", "chrome.exe"));
                paths.add(Path.of(root, "Google", "Chrome", "Application", "chrome.exe"));
                paths.add(Path.of(root, "Microsoft", "Edge", "Application", "msedge.exe"));
            }
        }
        return List.copyOf(paths);
    }

    public Path resolve() {
        List<Path> candidates = resolveCandidates();
        if (!candidates.isEmpty()) {
            return candidates.getFirst();
        }

        throw new IllegalStateException(
                "No Chromium/Edge executable found. Set BROWSER_EXECUTABLE_PATH to an absolute browser binary path."
        );
    }

    /**
     * Resolved candidates are memoized: the set of installed browser binaries does not change
     * while the JVM runs, and discovery forks one {@code which} per command name and stats every
     * known path, which is too expensive to repeat on every export request.
     */
    private static volatile List<Path> memoizedCandidates;

    /** Clears the memo. Intended for tests. */
    static void invalidateMemo() {
        memoizedCandidates = null;
    }

    public List<Path> resolveCandidates() {
        String override = System.getenv("BROWSER_EXECUTABLE_PATH");
        if (override != null && !override.isBlank()) {
            return List.of(Path.of(override.trim()));
        }

        List<Path> cached = memoizedCandidates;
        if (cached != null) {
            return cached;
        }

        LinkedHashSet<Path> out = new LinkedHashSet<>();
        out.addAll(resolveCommandsAll(CHROMIUM_COMMANDS));
        out.addAll(resolvePathsAll(CHROMIUM_PATHS));
        out.addAll(resolvePathsAll(PLATFORM_PATHS));
        out.addAll(resolveCommandsAll(EDGE_COMMANDS));
        out.addAll(resolvePathsAll(EDGE_PATHS));

        // Snap wrappers often fail under headless automation; prefer distro binaries first.
        List<Path> ordered = new ArrayList<>(out);
        ordered.sort((a, b) -> {
            boolean aSnap = a.toString().startsWith("/snap/");
            boolean bSnap = b.toString().startsWith("/snap/");
            return Boolean.compare(aSnap, bSnap);
        });

        List<Path> resolved = List.copyOf(ordered);
        memoizedCandidates = resolved;
        return resolved;
    }

    private List<Path> resolveCommandsAll(List<String> commands) {
        List<Path> out = new ArrayList<>();
        for (String command : commands) {
            Path resolved = resolveCommand(command);
            if (resolved != null) {
                out.add(resolved);
            }
        }
        return out;
    }

    private List<Path> resolvePathsAll(List<Path> paths) {
        List<Path> out = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isExecutable(path)) {
                out.add(path);
            }
        }
        return out;
    }

    private Path resolveCommand(String command) {
        Process process = null;
        try {
            // "which" only exists on Unix-like systems; on Windows the ProcessBuilder throws and the
            // platform paths above do the work.
            process = new ProcessBuilder("which", command)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String resolved = reader.readLine();
                int exit = process.waitFor();
                if (exit == 0 && resolved != null && !resolved.isBlank()) {
                    return Path.of(resolved.trim());
                }
            }
        } catch (Exception ignored) {
            // Try next candidate.
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
        return null;
    }
}

