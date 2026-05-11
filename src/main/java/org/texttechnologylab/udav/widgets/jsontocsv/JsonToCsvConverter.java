package org.texttechnologylab.udav.widgets.jsontocsv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class JsonToCsvConverter {

    private final ObjectMapper mapper;
    private final char delimiter;

    public JsonToCsvConverter() {
        this(new ObjectMapper(), ',');
    }

    public JsonToCsvConverter(ObjectMapper mapper) {
        this(mapper, ',');
    }

    public JsonToCsvConverter(ObjectMapper mapper, char delimiter) {
        this.mapper = mapper;
        this.delimiter = delimiter;
    }

    public String convert(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        return convert(root);
    }

    public String convert(JsonNode root) {
        List<Map<String, String>> rows = toRows(root);
        return rowsToCsv(rows);
    }

    private List<Map<String, String>> toRows(JsonNode root) {
        List<Map<String, String>> rows = new ArrayList<>();

        if (root == null || root.isNull()) {
            rows.add(new LinkedHashMap<>());
            return rows;
        }

        if (root.isArray()) {
            if (root.isEmpty()) {
                rows.add(new LinkedHashMap<>());
                return rows;
            }

            for (JsonNode item : root) {
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                flatten(item, "", row);
                rows.add(row);
            }
            return rows;
        }

        LinkedHashMap<String, String> singleRow = new LinkedHashMap<>();
        flatten(root, "", singleRow);
        rows.add(singleRow);
        return rows;
    }

    private void flatten(JsonNode node, String path, Map<String, String> out) {
        if (node == null || node.isNull()) {
            if (!path.isEmpty()) out.put(path, "");
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String nextPath = path.isEmpty() ? field.getKey() : path + "." + field.getKey();
                flatten(field.getValue(), nextPath, out);
            }
            return;
        }

        if (node.isArray()) {
            if (node.isEmpty()) {
                if (!path.isEmpty()) out.put(path, "[]");
                return;
            }

            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                String nextPath = path + "[" + i + "]";
                flatten(item, nextPath, out);
            }
            return;
        }

        if (path.isEmpty()) {
            out.put("value", node.asText());
        } else {
            out.put(path, node.asText());
        }
    }

    private String rowsToCsv(List<Map<String, String>> rows) {
        LinkedHashSet<String> headers = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            headers.addAll(row.keySet());
        }

        List<String> columns = new ArrayList<>(headers);
        StringBuilder sb = new StringBuilder();

        if (!columns.isEmpty()) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(delimiter);
                sb.append(escape(columns.get(i)));
            }
            sb.append('\n');

            for (Map<String, String> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) sb.append(delimiter);
                    String value = row.getOrDefault(columns.get(i), "");
                    sb.append(escape(value));
                }
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";

        boolean mustQuote = value.indexOf(delimiter) >= 0
                || value.contains("\n")
                || value.contains("\r")
                || value.contains("\"");

        String escaped = value.replace("\"", "\"\"");
        return mustQuote ? "\"" + escaped + "\"" : escaped;
    }
}
