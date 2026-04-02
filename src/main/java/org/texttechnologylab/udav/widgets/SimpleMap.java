package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("SimpleMap")
public class SimpleMap extends Widget {

    public SimpleMap(GeneratorDataRepository repo, ObjectMapper mapper) {
        super(repo, mapper);
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {
        assert repo != null;

        if (filters != null && filters.containsKey("hide")
                && filters.get("hide") != null
                && !filters.get("hide").isEmpty()) {
            return mapper.createArrayNode();
        }

        Map<String, List<GeneratorDataRepository.MapCoordinatesRow>> result =
                repo.loadMapCoordinatesByFile(schema, generatorId);

        assert mapper != null;
        ArrayNode out = mapper.createArrayNode();

        for (Map.Entry<String, List<GeneratorDataRepository.MapCoordinatesRow>> entry : result.entrySet()) {
            String filename = entry.getKey();
            if (files != null && !files.isEmpty() && !files.contains(filename)) {
                continue;
            }

            List<GeneratorDataRepository.MapCoordinatesRow> rows = entry.getValue();
            if (rows == null || rows.isEmpty()) {
                continue;
            }

            for (GeneratorDataRepository.MapCoordinatesRow row : rows) {
                if (row.coordinates() == null || row.coordinates().size() < 2) {
                    continue;
                }

                var feature = mapper.createObjectNode();
                feature.put("type", "Feature");

                var properties = mapper.createObjectNode();
                properties.put("label", row.label() != null ? row.label() : filename);

                String color = firstNonBlank(row.fillColor(), row.strokeColor(), row.outsideColor(), "#00618f");
                properties.put("color", color);
                feature.set("properties", properties);

                var geometry = mapper.createObjectNode();
                geometry.put("type", "Point");
                ArrayNode coordinates = mapper.createArrayNode();
                coordinates.add(row.coordinates().get(0));
                coordinates.add(row.coordinates().get(1));
                geometry.set("coordinates", coordinates);
                feature.set("geometry", geometry);

                out.add(feature);
            }
        }

        return out;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
