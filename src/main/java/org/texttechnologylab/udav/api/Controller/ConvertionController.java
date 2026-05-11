package org.texttechnologylab.udav.api.Controller;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.texttechnologylab.udav.widgets.Widget;
import org.texttechnologylab.udav.widgets.jsontocsv.JsonToCsvConverter;
import org.texttechnologylab.udav.widgets.svgtolatex.SvgToLaTeXConverter;

@RestController
@RequestMapping("/api/convertions")
public class ConvertionController {

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
    public ResponseEntity<byte[]> createZip(
            @RequestParam("files") List<MultipartFile> files) throws Exception {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename() != null
                        ? file.getOriginalFilename()
                        : "file_" + System.currentTimeMillis();

                ZipEntry zipEntry = new ZipEntry(filename);
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(file.getBytes());
                zipOutputStream.closeEntry();
            }
        }

        byte[] zipBytes = byteArrayOutputStream.toByteArray();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"archive.zip\"");
        headers.set(HttpHeaders.CONTENT_TYPE, "application/zip");
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(zipBytes.length));

        return ResponseEntity.ok()
                .headers(headers)
                .body(zipBytes);
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
