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

@Component("VoronoiDiagram")
public class Voronoi2D extends Widget {

    public Voronoi2D(GeneratorDataRepository repo, ObjectMapper mapper) {
        super(repo, mapper);
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {

        assert repo != null;
        Map<String, List<GeneratorDataRepository.MapCoordinatesRow>> result = repo.loadMapCoordinatesByFile(schema, generatorId);

        assert mapper != null;
        ArrayNode out = mapper.createArrayNode();
        for (Map.Entry<String, List<GeneratorDataRepository.MapCoordinatesRow>> entry : result.entrySet()) {
            String label = entry.getKey();
            List<GeneratorDataRepository.MapCoordinatesRow> rows = entry.getValue();
            for (GeneratorDataRepository.MapCoordinatesRow row : rows) {
                var obj = mapper.createObjectNode();
                obj.put("label", row.label());
                if (row.coordinates() != null && row.coordinates().size() > 1) {
                    obj.put("x", row.coordinates().get(0));
                    obj.put("y", row.coordinates().get(1));
                }
                obj.put("fill", row.fillColor());
                obj.put("stroke", row.strokeColor());
                obj.put("scale", row.scale());
                obj.put("outside", row.outsideColor());
                out.add(obj);
            }
        }
        return out;
    }
}
