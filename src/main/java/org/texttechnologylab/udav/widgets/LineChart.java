package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.widgets.tools.SvgToLaTeXConverter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("LineChart")
public class LineChart extends Widget {

    public LineChart(GeneratorDataRepository repo, ObjectMapper mapper) {
        super(repo, mapper);
    }

    @Override
    public String toTex(JsonNode jsonNode) {
        try {
            String svg = jsonNode.get("svg").asText();
            SvgToLaTeXConverter converter = new SvgToLaTeXConverter();
            return converter.convert(svg);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {
        assert repo != null;
        if (filters != null && filters.containsKey("hide") && filters.get("hide") != null && !filters.get("hide").isEmpty()) {
            return mapper.createArrayNode();
        }
        Map<String, List<GeneratorDataRepository.MapCoordinatesRow>> result = repo.loadMapCoordinatesByFile(schema, generatorId);
        assert mapper != null;
        ArrayNode out = mapper.createArrayNode();
        for (Map.Entry<String, List<GeneratorDataRepository.MapCoordinatesRow>> entry : result.entrySet()) {
            String datasetName = entry.getKey();
            List<GeneratorDataRepository.MapCoordinatesRow> rows = entry.getValue();
            if (rows == null || rows.isEmpty()) continue;
            var datasetObj = mapper.createObjectNode();
            datasetObj.put("name", datasetName);
            // Use color from first row if available, else default
            String color = (rows.getFirst() != null && rows.getFirst().fillColor() != null) ? rows.getFirst().fillColor() : "#00618f";
            datasetObj.put("color", color);
            ArrayNode coordinatesArr = mapper.createArrayNode();
            for (GeneratorDataRepository.MapCoordinatesRow row : rows) {
                if (row.coordinates() != null && row.coordinates().size() > 1) {
                    var coordObj = mapper.createObjectNode();
                    coordObj.put("x", row.coordinates().get(0));
                    coordObj.put("y", row.coordinates().get(1));
                    coordinatesArr.add(coordObj);
                }
            }
            datasetObj.set("coordinates", coordinatesArr);
            out.add(datasetObj);
        }
        return out;
    }
}
