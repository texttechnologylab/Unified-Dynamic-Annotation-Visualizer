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
        var o1 = mapper.createObjectNode();
        o1.put("label", "Cell 1");
        o1.put("x", -0.17228836433487893);
        o1.put("y", 0.0004034504818264395);
        o1.put("fill", "#FF5400FF");
        o1.put("stroke", "#800000FF");
        o1.put("scale", 0.007084618021265124);
        o1.put("abs", 0.008337255160062937);
        out.add(o1);
        var o2 = mapper.createObjectNode();
        o2.put("label", "Cell 2");
        o2.put("x", 0.08807457534091101);
        o2.put("y", 0.288377970457077);
        o2.put("fill", "#FFAA00FF");
        o2.put("stroke", "#FF9090FF");
        o2.put("scale", 0.41333009767848083);
        o2.put("abs", 0.1593699070893901);
        out.add(o2);
        var o3 = mapper.createObjectNode();
        o3.put("label", "Cell 3");
        o3.put("x", -0.31771938753358686);
        o3.put("y", 0.0005067524034529924);
        o3.put("fill", "#FEFF00FF");
        o3.put("stroke", "#800000FF");
        o3.put("scale", 0.018789279751973822);
        o3.put("abs", 0.012688777059128192);
        out.add(o3);
        return out;
    }
}
