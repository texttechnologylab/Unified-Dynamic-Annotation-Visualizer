package org.texttechnologylab.udav.api.Controller;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.texttechnologylab.udav.widgets.Widget;
import org.texttechnologylab.udav.widgets.jsontocsv.JsonToCsvConverter;
import org.texttechnologylab.udav.widgets.svgtolatex.SvgToLaTeXConverter;

@RestController
@RequestMapping("/api/convertions")
public class ConvertionController {

    public record ZipFileItem(String name, String content) {}
    public record ZipRequest(String archiveName, List<ZipFileItem> files) {}

    @PostMapping("/csv")
    public ResponseEntity<Map<String, String>> widgetToCsv(@RequestBody String body) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(body);
        JsonNode jsonNodeJson = node.get("data");
        String widgetType = node.get("type").asText();

        String csv;
        try {
            Widget widget = Widget.constructWidget(widgetType);
            csv = widget.toCsv(node);
            if (csv == null) throw new Exception();
            // widget-intrinsic native csv defined!

        } catch (Exception ignored) {
            // No widget-intrinsic csv defined -> Use general JsonToCsvConverter

            JsonToCsvConverter converter = new JsonToCsvConverter(mapper);
            csv = converter.convert(jsonNodeJson);
        }

        Map<String, String> response = new HashMap<>();
        response.put("content", csv);

        // TODO: Add metadata

        return ResponseEntity.ok(response);
    }

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
            // widget-intrinsic native tex defined!

        } catch (Exception ignored) {
            // No widget-intrinsic tex defined -> Use general SvgToLaTeXConverter

            String svg = node.get("svg").asText();
            SvgToLaTeXConverter converter = new SvgToLaTeXConverter();
            tex = converter.convert(svg);
        }

        tex = addMetaDataToTex(tex, node);

        Map<String, String> response = new HashMap<>();
        response.put("content", tex);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/zip")
    public ResponseEntity<byte[]> createZip(@RequestBody ZipRequest request) throws Exception {
        String archiveName = (request == null || request.archiveName() == null || request.archiveName().isBlank())
                ? "export.zip"
                : request.archiveName().trim();
        if (!archiveName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            archiveName = archiveName + ".zip";
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(byteStream)) {
            if (request != null && request.files() != null) {
                int index = 0;
                for (ZipFileItem item : request.files()) {
                    if (item == null) continue;
                    String rawName = (item.name() == null || item.name().isBlank())
                            ? String.format(Locale.ROOT, "file-%03d.txt", index)
                            : item.name();
                    String safeName = rawName.replaceAll("[^a-zA-Z0-9._/-]", "_");
                    String content = item.content() == null ? "" : item.content();

                    zip.putNextEntry(new ZipEntry(safeName));
                    zip.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    zip.closeEntry();
                    index++;
                }
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archiveName + "\"")
                .body(byteStream.toByteArray());
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
