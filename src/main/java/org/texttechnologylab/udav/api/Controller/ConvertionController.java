package org.texttechnologylab.udav.api.Controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convertions")
public class ConvertionController {

    @PostMapping("/tikz")
    public ResponseEntity<Map<String, String>> svg2tikz(@RequestBody String body) throws Exception {
        // Parse JSON body to extract SVG string
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
        String svg = node.get("svg").asText();

        // Convert SVG to TikZ
        String tikz = convertSvgToTikz(svg);

        tikz = addMetaDataToTikz(tikz); // TODO

        Map<String, String> response = new HashMap<>();
        response.put("content", tikz);
        return ResponseEntity.ok(response);
    }

    private static String convertSvgToTikz(String svgContent) throws Exception {
        String[] command = resolveSvg2TikzCommand(null);

        // Write SVG content to a temporary file
        java.nio.file.Path tempSvgFile = Files.createTempFile("input-svg", ".svg");
        Files.write(tempSvgFile, svgContent.getBytes(StandardCharsets.UTF_8));

        command[command.length - 1] = tempSvgFile.toAbsolutePath().toString();

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // Read stdout
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Read stderr
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(tempSvgFile);
            throw new RuntimeException("svg2tikz timed out.");
        }

        if (process.exitValue() != 0) {
            Files.deleteIfExists(tempSvgFile);
            throw new RuntimeException("svg2tikz failed:\n" + errorOutput);
        }

        Files.deleteIfExists(tempSvgFile);
        return output.toString();
    }

    private static String[] resolveSvg2TikzCommand(String svgFilePath) {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            // 1. Check svg2tikz in PATH
            try {
                Process whereProc = new ProcessBuilder("where", "svg2tikz.exe").start();
                boolean finished = whereProc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                if (finished && whereProc.exitValue() == 0) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(whereProc.getInputStream(), StandardCharsets.UTF_8))) {
                        String path = reader.readLine();
                        if (path != null && !path.isEmpty()) {
                            return new String[]{path, svgFilePath};
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 2. Check known installation paths
            String[] candidates = {
                    home + "\\AppData\\Local\\Programs\\Python\\Python312\\Scripts\\svg2tikz.exe",
                    home + "\\.local\\bin\\svg2tikz.exe",
                    home + "\\.local\\bin\\svg2tikz",
            };
            for (String candidate : candidates) {
                java.io.File f = new java.io.File(candidate);
                if (f.exists() && f.canExecute()) {
                    return new String[]{candidate, svgFilePath};
                }
            }

            // 3. Check python module svg2tikz
            String[] pythonCandidates = {"python", "python3", "py"};
            for (String py : pythonCandidates) {
                try {
                    Process p = new ProcessBuilder(py, "-m", "svg2tikz", "--help").start();
                    boolean finished = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (finished && p.exitValue() == 0) {
                        return new String[]{py, "-m", "svg2tikz", svgFilePath};
                    }
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("svg2tikz not found");
        } else {
            // Linux/Mac
            String[] candidates = {
                    "svg2tikz",
                    home + "/.local/bin/svg2tikz",
                    "/usr/local/bin/svg2tikz",
                    "/usr/bin/svg2tikz"
            };
            for (String candidate : candidates) {
                java.io.File f = new java.io.File(candidate);
                if (f.exists() && f.canExecute()) {
                    return new String[]{candidate, svgFilePath};
                }
            }
            // Fallback: Python-Module
            String[] pythonCandidates = {"python3", "python"};
            for (String py : pythonCandidates) {
                try {
                    Process p = new ProcessBuilder(py, "-m", "svg2tikz", "--help").start();
                    boolean finished = p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (finished && p.exitValue() == 0) {
                        return new String[]{py, "-m", "svg2tikz", svgFilePath};
                    }
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("svg2tikz not found");
        }
    }


    private static String addMetaDataToTikz(String tikz) {
        return tikz;
    }
}
