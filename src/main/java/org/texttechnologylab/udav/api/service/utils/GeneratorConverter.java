package org.texttechnologylab.udav.api.service.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public final class GeneratorConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private GeneratorConverter() {
    }

    /**
     * old -> new (you already have this)
     */
    public static String toNewFormat(String oldJson) {
        try {
            JsonNode root = MAPPER.readTree(oldJson);
            List<ObjectNode> generators = OldToNew.extractGenerators(root);
            ObjectNode out = MAPPER.createObjectNode();
            ArrayNode arr = MAPPER.createArrayNode();
            generators.forEach(arr::add);
            out.set("generators", arr);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * new -> old
     * Takes:
     * {
     * "generators": [ ...flat... ]
     * }
     * Returns:
     * {
     * "id": "restored",
     * "sources": [ { ... , "createsGenerators": [ ... ] } ],
     * "derivedGenerators": [ ... ]
     * }
     */
    public static String toOldFormat(String newJson) {
        try {
            JsonNode root = MAPPER.readTree(newJson);
            JsonNode gens = root.get("generators");
            if (gens == null || !gens.isArray()) {
                throw new IllegalArgumentException("No generators array");
            }

            List<ObjectNode> normalGenerators = new ArrayList<>();
            List<ObjectNode> derivedGenerators = new ArrayList<>();

            for (JsonNode g : gens) {
                if (!g.isObject()) continue;
                ObjectNode go = (ObjectNode) g;

                boolean isDerived = go.has("fromGenerators");
                if (isDerived) {
                    // take as-is, but drop fields that belong only to flat format (optionally)
                    ObjectNode copy = go.deepCopy();
                    // often in old format there was NO "type" for derived items, so remove it if present
                    copy.remove("type");
                    copy.remove("name");
                    copy.remove("source");
                    derivedGenerators.add(copy);
                } else {
                    // normal generator
                    normalGenerators.add(go.deepCopy());
                }
            }

            // Build old root
            ObjectNode out = MAPPER.createObjectNode();
            out.put("id", "restored");

            ArrayNode sources = MAPPER.createArrayNode();
            out.set("sources", sources);

            // single synthetic source
            ObjectNode source = MAPPER.createObjectNode();
            source.put("id", "RestoredSource");
            source.put("type", "uima.tcas.Annotation");
            sources.add(source);

            // its createsGenerators
            ArrayNode creates = MAPPER.createArrayNode();
            source.set("createsGenerators", creates);

            // put all normal generators here, but in old format (without name, with type/id/settings)
            for (ObjectNode ng : normalGenerators) {
                ObjectNode oldGen = MAPPER.createObjectNode();
                // mandatory
                String type = ng.path("type").asText(null);
                if (type != null) {
                    oldGen.put("type", type);
                }
                // id
                if (ng.has("id")) {
                    oldGen.set("id", ng.get("id"));
                }
                // settings
                if (ng.has("settings")) {
                    oldGen.set("settings", ng.get("settings"));
                }
                // if in your original format generators could also have children, add that here

                creates.add(oldGen);
            }

            // derivedGenerators
            ArrayNode derivedArr = MAPPER.createArrayNode();
            for (ObjectNode d : derivedGenerators) {
                derivedArr.add(d);
            }
            out.set("derivedGenerators", derivedArr);

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // demo
    public static void main(String[] args) {
        String newFormat = """
                {
                  "generators": [
                    {
                      "name": "New CategoryNumberMapping",
                      "type": "CategoryNumberMapping",
                      "source": "org.hucompute.textimager.uima.type.category.CategoryCoveredTagged",
                      "settings": {},
                      "id": "CategoryNumberMapping-p78dawc"
                    },
                    {
                      "name": "New TextFormatting",
                      "type": "TextFormatting",
                      "source": "uima.tcas.Annotation",
                      "settings": { "style": "highlight" },
                      "id": "TextFormatting-ezj3cu7"
                    },
                    {
                      "id": "Total_Formatting",
                      "fromGenerators": [
                        { "id": "POS_Formatting" },
                        { "id": "NE_Formatting" }
                      ]
                    }
                  ]
                }
                """;

        System.out.println(toOldFormat(newFormat));
    }

    /**
     * your previous old->new helper collapsed here for completeness
     */
    private static final class OldToNew {
        static List<ObjectNode> extractGenerators(JsonNode root) {
            List<ObjectNode> result = new ArrayList<>();

            JsonNode sources = root.get("sources");
            if (sources != null && sources.isArray()) {
                for (JsonNode sourceNode : sources) {
                    String sourceId = sourceNode.path("id").asText(null);
                    String sourceType = sourceNode.path("type").asText(null);
                    collectFromNode(sourceNode, sourceId, sourceType, result);
                }
            }

            JsonNode derived = root.get("derivedGenerators");
            if (derived != null && derived.isArray()) {
                for (JsonNode d : derived) {
                    if (d.isObject()) {
                        result.add(((ObjectNode) d).deepCopy());
                    }
                }
            }

            return result;
        }

        private static void collectFromNode(JsonNode node,
                                            String parentSourceId,
                                            String parentSourceType,
                                            List<ObjectNode> target) {

            if (node.has("type") && !node.has("sources")) {
                target.add(buildGenerator(node, parentSourceId, parentSourceType));
            }

            JsonNode creates = node.get("createsGenerators");
            if (creates != null && creates.isArray()) {
                for (JsonNode child : creates) {
                    collectFromNode(child, parentSourceId, parentSourceType, target);
                }
            }

            JsonNode derived = node.get("derivedGenerators");
            if (derived != null && derived.isArray()) {
                for (JsonNode child : derived) {
                    collectFromNode(child, parentSourceId, parentSourceType, target);
                }
            }
        }

        private static ObjectNode buildGenerator(JsonNode src,
                                                 String parentSourceId,
                                                 String parentSourceType) {
            ObjectNode out = MAPPER.createObjectNode();

            String type = src.path("type").asText("Unknown");
            String id = src.path("id").asText(null);
            JsonNode settings = src.path("settings");

            out.put("name", "New " + type);
            out.put("type", type);

            // mapping, adjust as needed
            String mappedSource = (parentSourceType != null)
                    ? parentSourceType
                    : (parentSourceId != null ? parentSourceId : "uima.tcas.Annotation");
            out.put("source", mappedSource);

            if (settings != null && !settings.isMissingNode() && !settings.isNull()) {
                out.set("settings", settings.deepCopy());
            } else {
                out.set("settings", MAPPER.createObjectNode());
            }

            if (id != null) {
                out.put("id", id);
            } else {
                out.put("id", type + "-restored");
            }

            return out;
        }
    }
}
