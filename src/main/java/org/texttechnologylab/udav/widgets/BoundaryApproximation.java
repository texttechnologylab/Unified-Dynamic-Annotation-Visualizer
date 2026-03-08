package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("BoundaryApproximation")
public class BoundaryApproximation extends Widget {

    public BoundaryApproximation(GeneratorDataRepository repo, ObjectMapper mapper) {
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

        ArrayNode coordinatesArr = mapper.createArrayNode();

        for (Map.Entry<String, List<GeneratorDataRepository.MapCoordinatesRow>> entry : result.entrySet()) {
            List<GeneratorDataRepository.MapCoordinatesRow> rows = entry.getValue();
            if (rows == null || rows.isEmpty()) continue;

            for (GeneratorDataRepository.MapCoordinatesRow row : rows) {
                if (row.coordinates() != null && row.coordinates().size() > 1) {
                    ObjectNode coordObj = mapper.createObjectNode();
                    coordObj.put("x", row.coordinates().get(0));
                    coordObj.put("y", row.coordinates().get(1));
                    coordinatesArr.add(coordObj);
                }
            }
        }

        return coordinatesArr;
    }
}
