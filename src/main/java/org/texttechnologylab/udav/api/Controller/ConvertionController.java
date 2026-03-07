package org.texttechnologylab.udav.api.Controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.texttechnologylab.udav.widgets.Widget;
import org.texttechnologylab.udav.widgets.tools.Svg2TikzFixes;

@RestController
@RequestMapping("/api/convertions")
public class ConvertionController {

    @PostMapping("/tikz")
    public ResponseEntity<Map<String, String>> widgetToTikz(@RequestBody String body) throws Exception {
        // Parse JSON body to extract SVG string
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(body);
        String widgetType = node.get("type").asText();

        String tex;
        try {
            Widget widget = Widget.constructWidget(widgetType);
            tex = widget.toTex(node);
            if (tex == null) throw new Exception();
            // Native tex defined!

        } catch (Exception ignored) {
            // No native tex defined -> Use Svg2Tikz

            String svg = node.get("svg").asText();
            svg = Svg2TikzFixes.preRun_hyphenMinusFix(svg);
            tex = convertSvgToTikz(svg);
            tex = Svg2TikzFixes.postRun_southAnchorFix(tex);
        }

        tex = addMetaDataToTex(tex, node);

        Map<String, String> response = new HashMap<>();
        response.put("content", tex);
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


    private static String addMetaDataToTex(String tex, JsonNode node) {
        try {
            JsonNode metadataNode = node.path("meta").path("metadata");

            if (!metadataNode.isObject()) {
                return tex;
            }

            StringBuilder header = new StringBuilder();
            header.append("% ---\n");

            appendNode(header, metadataNode, 0);

            header.append("% ---\n\n");

            return header + tex;

        } catch (Exception ignored) {}

        return tex;
    }

    private static void appendNode(StringBuilder sb, JsonNode node, int indent) {
        String indentStr = "  ".repeat(indent);

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();

                if (entry.getValue().isValueNode()) {
                    sb.append("% ")
                            .append(indentStr)
                            .append(entry.getKey())
                            .append(": ")
                            .append(entry.getValue().asText())
                            .append("\n");
                } else {
                    sb.append("% ")
                            .append(indentStr)
                            .append(entry.getKey())
                            .append(":\n");
                    appendNode(sb, entry.getValue(), indent + 1);
                }
            }
        }

        else if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isValueNode()) {
                    sb.append("% ")
                            .append(indentStr)
                            .append("- ")
                            .append(item.asText())
                            .append("\n");
                } else {
                    sb.append("% ")
                            .append(indentStr)
                            .append("-\n");
                    appendNode(sb, item, indent + 1);
                }
            }
        }
    }
}
