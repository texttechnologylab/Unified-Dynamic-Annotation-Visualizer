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

        // svg = svg.replaceAll("\"currentColor\"", "\"#000000\"");
        // svg = svg.replaceAll("\"transparent\"", "\"none\"");

        // Convert SVG to TikZ
        String tikz = convertSvgToTikz(svg);

        tikz = addMetaDataToTikz(tikz); // TODO

        Map<String, String> response = new HashMap<>();
        response.put("content", tikz);
        return ResponseEntity.ok(response);
    }

    private static String convertSvgToTikz(String svgContent) throws Exception {

        String executable = resolveSvg2Tikz();

        // Write SVG content to a temporary file
        java.nio.file.Path tempSvgFile = Files.createTempFile("input-svg", ".svg");
        Files.write(tempSvgFile, svgContent.getBytes(StandardCharsets.UTF_8));

        ProcessBuilder pb = new ProcessBuilder(executable, tempSvgFile.toAbsolutePath().toString());
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

    private static String resolveSvg2Tikz() {
        String home = System.getProperty("user.home");

        String[] candidates = {
                "svg2tikz",
                home + "/.local/bin/svg2tikz",
                "/usr/local/bin/svg2tikz",
                "/usr/bin/svg2tikz"
        };

        for (String candidate : candidates) {
            try {
                Process p = new ProcessBuilder(candidate, "--help").start();
                if (p.waitFor() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        throw new RuntimeException("svg2tikz not found.");
    }

    private static String addMetaDataToTikz(String tikz) {
        return tikz;
    }
}
