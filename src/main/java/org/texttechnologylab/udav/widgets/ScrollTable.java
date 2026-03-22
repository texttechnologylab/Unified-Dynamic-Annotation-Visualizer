package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartHandler;
import org.texttechnologylab.udav.api.charts.ValueTransforms;
import org.texttechnologylab.udav.widgets.jsontocsv.JsonToCsvConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("ScrollTable")
public class ScrollTable extends Widget {
    public ScrollTable(GeneratorDataRepository repo, ObjectMapper mapper) { super(repo, mapper); }

    public String toTex(JsonNode jsonNode) {
        JsonNode dataNode = jsonNode.get("data");
        if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) { return null; }

        // New format: each row is an object keyed by column name.
        // The first object's values are the display header names; its keys define column order.
        JsonNode firstRow = dataNode.get(0);
        if (firstRow == null || !firstRow.isObject()) return null;

        List<String> keys = new ArrayList<>();    // field names used as lookup keys
        List<String> headers = new ArrayList<>(); // display names (values of the first row)
        for (Map.Entry<String, JsonNode> entry : firstRow.properties()) {
            keys.add(entry.getKey());
            headers.add(entry.getValue().asText());
        }

        int numColumns = keys.size();
        if (numColumns == 0) return null;

        // Calculate max content length per column for approximate LaTeX column widths
        int[] maxLengths = new int[numColumns];
        for (int j = 0; j < numColumns; j++) {
            maxLengths[j] = headers.get(j).length();
        }
        for (int i = 1; i < dataNode.size(); i++) {
            JsonNode row = dataNode.get(i);
            for (int j = 0; j < numColumns; j++) {
                JsonNode cell = row.get(keys.get(j));
                int len = cell != null ? cell.asText().length() : 0;
                if (len > maxLengths[j]) maxLengths[j] = len;
            }
        }

        StringBuilder sb = new StringBuilder();

        // LaTeX preamble
        sb.append("\\documentclass{standalone}\n")
                .append("\\usepackage[utf8]{inputenc}\n")
                .append("\\usepackage{array}\n")
                .append("\\usepackage{geometry}\n")
                .append("\\geometry{margin=1in}\n\n")
                .append("\\begin{document}\n\n");

        // Begin table with dynamic widths
        sb.append("\\begin{tabular}{|>{\\centering\\arraybackslash}p{1cm}|"); // # column
        for (int j = 0; j < numColumns; j++) {
            double width = Math.max(maxLengths[j] * 0.25, 2.0); // rough cm approximation, min 2cm
            sb.append(">{\\centering\\arraybackslash}p{").append(String.format("%.2f", width)).append("cm}|");
        }
        sb.append("}\n\\hline\n");

        // Fill table rows — row 0 is the header row, subsequent rows are data
        for (int i = 0; i < dataNode.size(); i++) {
            JsonNode row = dataNode.get(i);

            // Row number column
            sb.append(i == 0 ? "\\textbf{\\#} & " : (i + " & "));

            for (int j = 0; j < numColumns; j++) {
                String rawText = i == 0
                        ? headers.get(j)
                        : (row.get(keys.get(j)) != null ? row.get(keys.get(j)).asText() : "");
                String cellText = escapeLatex(rawText);

                if (i == 0) {
                    sb.append("\\textbf{").append(cellText).append("}");
                } else {
                    sb.append(cellText);
                }
                if (j < numColumns - 1) sb.append(" & ");
            }
            sb.append(" \\\\\n\\hline\n");
        }

        sb.append("\\end{tabular}\n\n").append("\\end{document}");

        return sb.toString();
    }

    /**
     * Escapes all LaTeX special characters in a plain-text string so it can be safely
     * embedded in a LaTeX document without causing syntax errors.
     * Order matters: backslash must be replaced first.
     */
    private String escapeLatex(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\textbackslash{}")
                .replace("&",  "\\&")
                .replace("%",  "\\%")
                .replace("$",  "\\$")
                .replace("#",  "\\#")
                .replace("_",  "\\_")
                .replace("{",  "\\{")
                .replace("}",  "\\}")
                .replace("~",  "\\textasciitilde{}")
                .replace("^",  "\\textasciicircum{}")
                .replace("<",  "\\textless{}")
                .replace(">",  "\\textgreater{}");
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {

        assert mapper != null;
        ArrayNode out = mapper.createArrayNode();
        String generatorType = resolveGeneratorType(schema, generatorId);
        if ("CategoryNumber".equalsIgnoreCase(generatorType)) {
            // Optional: chart-specific "type" (e.g., for type-specific colors)
            String typeForColors = filters.getOrDefault("type", null);

            assert repo != null;
            var data = repo.loadCategoryNumber(schema, generatorId, files, typeForColors);

            // For PER_FILE_AVG only:
            Map<String, Map<String, Double>> perFile = null;
            if (valueMode == ValueMode.PER_FILE_AVG) {
                perFile = repo.loadCategoryNumberPerFile(schema, generatorId, typeForColors);
            }

            Map<String, Double> valuesTx =
                    ValueTransforms.apply(data.values(), valueMode, perFile, files);

            // optional filtering/sorting/limit from filters:
            var rows = ValueTransforms.sortLimitFilter(
                    valuesTx,
                    filters.getOrDefault("sort", "value"),
                    Boolean.parseBoolean(filters.getOrDefault("desc", "true")),
                    ChartHandler.parseDoubleOrNull(filters.get("min")),
                    ChartHandler.parseDoubleOrNull(filters.get("max")),
                    ChartHandler.parseIntOrNull(filters.get("limit"))
            );

            // build a simple [{label, value, color}] array
            out = mapper.createArrayNode();
            for (var entry : rows) {   // rows is the List<Map.Entry<String, Double>>
                var obj = mapper.createObjectNode();
                String label = entry.getKey();
                Double value = entry.getValue();
                obj.put("label", label);
                obj.put("value", value);

                String color = data.colors().get(label);
                if (color != null) {
                    obj.put("color", color);
                }
                out.add(obj);
            }
        } else if ("MapCoordinates".equalsIgnoreCase(generatorType)) {

        } else if ("HighlightText".equalsIgnoreCase(generatorType)) {

        }

        String csv = new JsonToCsvConverter(mapper).convert(out);
        return csvToJsonNode(csv);
    }

    /**
     * Converts a CSV string (as produced by JsonToCsvConverter) back into an ArrayNode.
     * Every row — including the header — becomes an object keyed by the header column names.
     * Values that parse as a double are stored as numbers; everything else is stored as a string.
     *
     * Example:
     *   label,value,color        -> { "label":"label", "value":"value", "color":"color" }
     *   NOUN,542.0,#ff0000       -> { "label":"NOUN",  "value":542.0,   "color":"#ff0000" }
     */
    private ArrayNode csvToJsonNode(String csv) {
        ArrayNode result = mapper.createArrayNode();
        if (csv == null || csv.isBlank()) return result;

        List<List<String>> rows = parseCsv(csv);
        if (rows.isEmpty()) return result;

        List<String> headers = rows.get(0);

        for (List<String> row : rows) {
            ObjectNode obj = mapper.createObjectNode();
            for (int i = 0; i < headers.size(); i++) {
                String key = headers.get(i);
                String value = i < row.size() ? row.get(i) : "";
                try {
                    obj.put(key, Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    obj.put(key, value);
                }
            }
            result.add(obj);
        }

        return result;
    }

    /** Splits a full CSV string into rows, each row being a list of field values. */
    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) return rows;

        // Character-by-character walk to handle RFC4180 quoted fields that may span lines.
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        field.append('"');   // escaped double-quote inside a quoted field
                        i++;
                    } else {
                        inQuotes = false;    // closing quote
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    if (!currentRow.isEmpty()) rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else if (c == '\r') {
                    // skip – handled by the \n that follows in \r\n
                } else {
                    field.append(c);
                }
            }
        }

        // Flush trailing field / row that has no terminating newline.
        if (field.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            if (!currentRow.stream().allMatch(String::isBlank)) rows.add(currentRow);
        }

        return rows;
    }

}

