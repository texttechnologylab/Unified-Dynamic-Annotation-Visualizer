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
            "chromium-freeworld"
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

    public Path resolve() {
        List<Path> candidates = resolveCandidates();
        if (!candidates.isEmpty()) {
            return candidates.getFirst();
        }

        throw new IllegalStateException(
                "No Chromium/Edge executable found. Set BROWSER_EXECUTABLE_PATH to an absolute browser binary path."
        );
    }

    public List<Path> resolveCandidates() {
        String override = System.getenv("BROWSER_EXECUTABLE_PATH");
        if (override != null && !override.isBlank()) {
            return List.of(Path.of(override.trim()));
        }

        LinkedHashSet<Path> out = new LinkedHashSet<>();
        out.addAll(resolveCommandsAll(CHROMIUM_COMMANDS));
        out.addAll(resolvePathsAll(CHROMIUM_PATHS));
        out.addAll(resolveCommandsAll(EDGE_COMMANDS));
        out.addAll(resolvePathsAll(EDGE_PATHS));

        // Snap wrappers often fail under headless automation; prefer distro binaries first.
        List<Path> ordered = new ArrayList<>(out);
        ordered.sort((a, b) -> {
            boolean aSnap = a.toString().startsWith("/snap/");
            boolean bSnap = b.toString().startsWith("/snap/");
            return Boolean.compare(aSnap, bSnap);
        });
        return ordered;
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

