package org.texttechnologylab.udav.api.charts.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartHandler;
import org.texttechnologylab.udav.api.charts.ValueTransforms;

import java.util.*;
import java.util.stream.Collectors;

@Component("PieChart")
@RequiredArgsConstructor
public class PieChartHandler implements ChartHandler {

    private final GeneratorDataRepository repo;
    private final ObjectMapper mapper;

    // ---- small local utils ----
    private static Set<String> parseCsvSet(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptySet();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Double parseDoubleOrNull(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {

        // Which fields to emit (default: label,value,color)
        LinkedHashSet<String> attrs = parseCsvSet(filters.getOrDefault("attrs", "label,value,color"))
                .stream().collect(Collectors.toCollection(LinkedHashSet::new));
        if (attrs.isEmpty()) attrs = new LinkedHashSet<>(List.of("label", "value", "color"));

        // Optional: restrict labels via filter "label" or "labels"
        Set<String> keepLabels = parseCsvSet(
                filters.getOrDefault("label", filters.getOrDefault("labels", ""))
        );

        // Sorting / filtering controls
        String sort = filters.getOrDefault("sort", "value");
        boolean desc = Boolean.parseBoolean(filters.getOrDefault("desc", "true"));
        Double min = parseDoubleOrNull(filters.get("min"));
        Double max = parseDoubleOrNull(filters.get("max"));
        Integer limit = parseIntOrNull(filters.get("limit"));

        // Optional: chart-specific type (for type-specific colors)
        String typeForColors = filters.getOrDefault("type", null);

        // Load base category values + colors
        var data = repo.loadCategoryNumber(schema, generatorId, files, typeForColors);
        Map<String, Double> values = new LinkedHashMap<>(data.values());

        // Apply optional label restriction BEFORE transforms
        if (!keepLabels.isEmpty()) {
            values.keySet().retainAll(keepLabels);
        }

        // For PER_FILE_AVG only: load per-file breakdown to average over files (optionally restricted by 'files')
        Map<String, Map<String, Double>> perFile = null;
        if (valueMode == ValueMode.PER_FILE_AVG) {
            perFile = repo.loadCategoryNumberPerFile(schema, generatorId, typeForColors);
        }

        // Transform values (RAW, SHARE, MAX1, ZSCORE, PER_FILE_AVG)
        Map<String, Double> valuesTx = ValueTransforms.apply(values, valueMode, perFile, files);

        // Optional min/max/sort/limit
        var rows = ValueTransforms.sortLimitFilter(valuesTx, sort, desc, min, max, limit);

        // Build [{label,value,color}] (or subset per 'attrs')
        ArrayNode out = mapper.createArrayNode();
        for (var entry : rows) {
            String label = entry.getKey();
            Double value = entry.getValue();

            var o = mapper.createObjectNode();
            if (attrs.contains("label")) o.put("label", label);
            if (attrs.contains("value")) o.put("value", value);
            if (attrs.contains("color")) {
                String color = data.colors().get(label);
                o.put("color", color == null ? "#999999" : color);
            }
            out.add(o);
        }
        return out;
    }
}
