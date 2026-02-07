// /charts/ChartHandler.java
package org.texttechnologylab.udav.api.charts;

import com.fasterxml.jackson.databind.JsonNode;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.*;
import java.util.stream.Collectors;

public interface ChartHandler {
    JsonNode render(String generatorId,
                    Map<String, String> filters,
                    Set<String> files,
                    ValueMode valueMode,
                    String schema);


    static Set<String> parseCsvSet(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static Double parseDoubleOrNull(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
