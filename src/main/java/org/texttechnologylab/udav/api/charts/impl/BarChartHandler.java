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

import java.util.Map;
import java.util.Set;

@Component("BarChart")
@RequiredArgsConstructor
public class BarChartHandler implements ChartHandler {

    private final GeneratorDataRepository repo;
    private final ObjectMapper mapper;



    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {

        // Optional: chart-specific "type" (e.g., for type-specific colors)
        String typeForColors = filters.getOrDefault("type", null);

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
        ArrayNode out = mapper.createArrayNode();
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
        return out;
    }
}
