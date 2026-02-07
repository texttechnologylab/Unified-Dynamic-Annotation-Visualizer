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


    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {

        // Which fields to emit (default: label,value,color)
        LinkedHashSet<String> attrs = ChartHandler.parseCsvSet(filters.getOrDefault("attrs", "label,value,color"))
                .stream().collect(Collectors.toCollection(LinkedHashSet::new));
        if (attrs.isEmpty()) attrs = new LinkedHashSet<>(List.of("label", "value", "color"));

        // Optional: restrict labels via filter "label" or "labels"
        Set<String> keepLabels = ChartHandler.parseCsvSet(
                filters.getOrDefault("label", filters.getOrDefault("labels", ""))
        );

        // Sorting / filtering controls
        String sort = filters.getOrDefault("sort", "value");
        boolean desc = Boolean.parseBoolean(filters.getOrDefault("desc", "true"));
        Double min = ChartHandler.parseDoubleOrNull(filters.get("min"));
        Double max = ChartHandler.parseDoubleOrNull(filters.get("max"));
        Integer limit = ChartHandler.parseIntOrNull(filters.get("limit"));

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
