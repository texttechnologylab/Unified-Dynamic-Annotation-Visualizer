package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.Map;
import java.util.Set;

@Component("ScrollTable")
public class ScrollTable extends Widget {
    public ScrollTable(GeneratorDataRepository repo, ObjectMapper mapper) { super(repo, mapper); }

    public String toTex(JsonNode jsonNode) {
        JsonNode dataNode = jsonNode.get("data");
        if (dataNode == null || !dataNode.isArray() || dataNode.isEmpty()) { return null; }

        int numColumns = dataNode.get(0).size();

        // Calculate max length of each column for approximate width
        int[] maxLengths = new int[numColumns];
        for (int j = 0; j < numColumns; j++) {
            int maxLen = dataNode.get(0).get(j).asText().length(); // header
            for (int i = 1; i < dataNode.size(); i++) {
                int len = dataNode.get(i).get(j).asText().length();
                if (len > maxLen) maxLen = len;
            }
            maxLengths[j] = maxLen;
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

        // Fill table rows
        for (int i = 0; i < dataNode.size(); i++) {
            JsonNode row = dataNode.get(i);

            // Row number column
            sb.append(i == 0 ? "\\textbf{\\#} & " : (i + " & "));

            for (int j = 0; j < numColumns; j++) {
                String cellText = row.get(j).asText();
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

    @Override
    public JsonNode render(String generatorId, Map<String, String> filters, Set<String> files, ValueMode valueMode, String schema) {
        return null;
    }
}
