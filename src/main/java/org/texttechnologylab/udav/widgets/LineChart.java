package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.Map;
import java.util.Set;

@Component("LineChart")
public class LineChart extends Widget {

    public LineChart(GeneratorDataRepository repo, ObjectMapper mapper) {
        super(repo, mapper);
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {
        ArrayNode out = mapper.createArrayNode();
        // First dataset
        var dataset1 = mapper.createObjectNode();
        dataset1.put("name", "Dataset Test");
        dataset1.put("color", "#00618f");
        ArrayNode coordinates1 = mapper.createArrayNode();
        coordinates1.add(mapper.createObjectNode().put("y", 5).put("x", 0));
        coordinates1.add(mapper.createObjectNode().put("y", 20).put("x", 20));
        coordinates1.add(mapper.createObjectNode().put("y", 10).put("x", 40));
        coordinates1.add(mapper.createObjectNode().put("y", 40).put("x", 60));
        coordinates1.add(mapper.createObjectNode().put("y", 5).put("x", 80));
        coordinates1.add(mapper.createObjectNode().put("y", 60).put("x", 100));
        dataset1.set("coordinates", coordinates1);
        out.add(dataset1);
        // Second dataset
        var dataset2 = mapper.createObjectNode();
        dataset2.put("name", "Test 2");
        dataset2.put("color", "#ff8000");
        ArrayNode coordinates2 = mapper.createArrayNode();
        coordinates2.add(mapper.createObjectNode().put("y", 10).put("x", 0));
        coordinates2.add(mapper.createObjectNode().put("y", 15).put("x", 20));
        coordinates2.add(mapper.createObjectNode().put("y", 30).put("x", 40));
        coordinates2.add(mapper.createObjectNode().put("y", 25).put("x", 60));
        coordinates2.add(mapper.createObjectNode().put("y", 50).put("x", 80));
        coordinates2.add(mapper.createObjectNode().put("y", 35).put("x", 100));
        dataset2.set("coordinates", coordinates2);
        out.add(dataset2);
        return out;
    }
}
