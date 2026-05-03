package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("NetworkGraph")
public class NetworkGraph extends Widget {

    private static final String DEFAULT_NODE_COLOR = "#00618f";
    private static final String DEFAULT_LINK_COLOR = "#9eadbd";

    public NetworkGraph(GeneratorDataRepository repo, ObjectMapper mapper) {
        super(repo, mapper);
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {
        assert repo != null;
        assert mapper != null;

        ObjectNode out = mapper.createObjectNode();
        ArrayNode nodes = mapper.createArrayNode();
        ArrayNode links = mapper.createArrayNode();
        out.set("nodes", nodes);
        out.set("links", links);

        if (filters != null && filters.containsKey("hide")
                && filters.get("hide") != null
                && !filters.get("hide").isEmpty()) {
            return out;
        }

        Map<String, List<GeneratorDataRepository.MapCoordinatesRow>> pointsByFile =
                repo.loadMapCoordinatesByFile(schema, generatorId);
        Map<String, List<GeneratorDataRepository.MapCoordinatesEdgeRow>> edgesByFile =
                repo.loadMapCoordinatesEdgesByFile(schema, generatorId);

        Map<String, Integer> nodeIds = new HashMap<>();
        int nextNodeId = 1;

        for (Map.Entry<String, List<GeneratorDataRepository.MapCoordinatesRow>> entry : pointsByFile.entrySet()) {
            String filename = entry.getKey();
            if (files != null && !files.isEmpty() && !files.contains(filename)) {
                continue;
            }

            List<GeneratorDataRepository.MapCoordinatesRow> rows = entry.getValue();
            if (rows == null || rows.isEmpty()) {
                continue;
            }

            for (int i = 0; i < rows.size(); i++) {
                GeneratorDataRepository.MapCoordinatesRow row = rows.get(i);
                int nodeId = nextNodeId++;
                nodeIds.put(nodeKey(filename, i), nodeId);

                ObjectNode node = mapper.createObjectNode();
                node.put("id", nodeId);
                node.put("name", firstNonBlank(row.label(), filename + "-" + (i + 1)));
                node.put("color", firstNonBlank(row.fillColor(), row.strokeColor(), row.outsideColor(), DEFAULT_NODE_COLOR));
                nodes.add(node);
            }
        }

        for (Map.Entry<String, List<GeneratorDataRepository.MapCoordinatesEdgeRow>> entry : edgesByFile.entrySet()) {
            String filename = entry.getKey();
            if (files != null && !files.isEmpty() && !files.contains(filename)) {
                continue;
            }

            List<GeneratorDataRepository.MapCoordinatesEdgeRow> fileEdges = entry.getValue();
            if (fileEdges == null || fileEdges.isEmpty()) {
                continue;
            }

            for (GeneratorDataRepository.MapCoordinatesEdgeRow edge : fileEdges) {
                Integer sourceId = nodeIds.get(nodeKey(filename, edge.fromIndex()));
                Integer targetId = nodeIds.get(nodeKey(filename, edge.toIndex()));
                if (sourceId == null || targetId == null) {
                    continue;
                }

                ObjectNode link = mapper.createObjectNode();
                link.put("source", sourceId);
                link.put("target", targetId);
                link.put("color", firstNonBlank(edge.color(), DEFAULT_LINK_COLOR));
                links.add(link);
            }
        }

        return out;
    }

    private static String nodeKey(String filename, int index) {
        return filename + "::" + index;
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

