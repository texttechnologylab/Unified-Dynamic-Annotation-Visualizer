package org.texttechnologylab.udav.api.service.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeneratorConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private GeneratorConverter() {
    }

    /**
     * old -> new
     *
     * Ensures a top-level generators array exists and all generator definitions are
     * flattened out of sources[*].createsGenerators.
     */
    public static String toNewFormat(String oldJson) {
        try {
            JsonNode root = MAPPER.readTree(oldJson);
            if (!(root instanceof ObjectNode rootObj)) {
                throw new IllegalArgumentException("Pipeline JSON root must be an object");
            }

            ObjectNode normalized = rootObj.deepCopy();

            List<ObjectNode> ordered = new ArrayList<>();
            Map<String, ObjectNode> byId = new LinkedHashMap<>();
            Set<String> noIdFingerprints = new LinkedHashSet<>();

            // Keep existing top-level generators first and enrich them with nested data if needed.
            JsonNode existingTopLevel = normalized.get("generators");
            if (existingTopLevel != null && existingTopLevel.isArray()) {
                for (JsonNode g : existingTopLevel) {
                    if (g instanceof ObjectNode objectNode) {
                        addOrMergeGenerator(
                                normalizeGenerator(objectNode, null),
                                ordered,
                                byId,
                                noIdFingerprints
                        );
                    }
                }
            }

            JsonNode sources = normalized.get("sources");
            if (sources != null && sources.isArray()) {
                for (JsonNode sourceNode : sources) {
                    if (!(sourceNode instanceof ObjectNode sourceObj)) {
                        continue;
                    }

                    String sourceId = textOrNull(sourceObj.get("id"));
                    OldToNew.extractFromArray(sourceObj.get("createsGenerators"), sourceId, ordered, byId, noIdFingerprints);
                    OldToNew.extractFromArray(sourceObj.get("derivedGenerators"), sourceId, ordered, byId, noIdFingerprints);

                    // Normalized GET output must not keep in-source generator definitions.
                    sourceObj.remove("createsGenerators");
                    sourceObj.remove("derivedGenerators");
                }
            }

            JsonNode legacyDerived = normalized.get("derivedGenerators");
            if (legacyDerived != null && legacyDerived.isArray()) {
                for (JsonNode g : legacyDerived) {
                    if (g instanceof ObjectNode objectNode) {
                        addOrMergeGenerator(
                                normalizeGenerator(objectNode, null),
                                ordered,
                                byId,
                                noIdFingerprints
                        );
                    }
                }
            }

            ArrayNode generatorsOut = MAPPER.createArrayNode();
            for (ObjectNode generator : ordered) {
                generatorsOut.add(generator);
            }

            normalized.set("generators", generatorsOut);
            normalized.remove("derivedGenerators");

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalized);
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
        System.out.println(toNewFormat(toOldFormat(newFormat)));
    }

    private static void addOrMergeGenerator(ObjectNode candidate,
                                            List<ObjectNode> ordered,
                                            Map<String, ObjectNode> byId,
                                            Set<String> noIdFingerprints) {
        String id = textOrNull(candidate.get("id"));
        if (id != null) {
            ObjectNode existing = byId.get(id);
            if (existing == null) {
                byId.put(id, candidate);
                ordered.add(candidate);
                return;
            }
            mergeMissing(existing, candidate);
            return;
        }

        String fingerprint = candidate.toString();
        if (noIdFingerprints.add(fingerprint)) {
            ordered.add(candidate);
        }
    }

    private static void mergeMissing(ObjectNode target, ObjectNode source) {
        for (String fieldName : List.of("name", "type", "source", "settings")) {
            JsonNode targetValue = target.get(fieldName);
            JsonNode sourceValue = source.get(fieldName);
            if (isMissing(targetValue) && !isMissing(sourceValue)) {
                target.set(fieldName, sourceValue.deepCopy());
            }
        }
    }

    private static boolean isMissing(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        if (node.isTextual()) {
            return node.asText().isBlank();
        }
        return false;
    }

    private static ObjectNode normalizeGenerator(ObjectNode src, String sourceId) {
        ObjectNode out = src.deepCopy();
        out.remove("createsGenerators");
        out.remove("derivedGenerators");

        String type = textOrNull(out.get("type"));
        if (type != null && isMissing(out.get("name"))) {
            out.put("name", "New " + type);
        }

        if (type != null && out.get("settings") == null && !out.has("fromGenerators")) {
            out.set("settings", MAPPER.createObjectNode());
        }

        // For extracted source-nested generators, always reference source by source id.
        if (sourceId != null && !sourceId.isBlank() && !out.has("fromGenerators")) {
            out.put("source", sourceId);
        }

        return out;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static final class OldToNew {
        static void extractFromArray(JsonNode nodes,
                                     String sourceId,
                                     List<ObjectNode> ordered,
                                     Map<String, ObjectNode> byId,
                                     Set<String> noIdFingerprints) {
            if (nodes == null || !nodes.isArray()) {
                return;
            }
            for (JsonNode node : nodes) {
                if (node instanceof ObjectNode objectNode) {
                    extractFromNode(objectNode, sourceId, ordered, byId, noIdFingerprints);
                }
            }
        }

        private static void extractFromNode(ObjectNode node,
                                            String sourceId,
                                            List<ObjectNode> ordered,
                                            Map<String, ObjectNode> byId,
                                            Set<String> noIdFingerprints) {
            boolean isGeneratorDefinition = node.has("type") || node.has("fromGenerators");
            if (isGeneratorDefinition) {
                addOrMergeGenerator(
                        normalizeGenerator(node, sourceId),
                        ordered,
                        byId,
                        noIdFingerprints
                );
            }

            extractFromArray(node.get("createsGenerators"), sourceId, ordered, byId, noIdFingerprints);
            extractFromArray(node.get("derivedGenerators"), sourceId, ordered, byId, noIdFingerprints);
        }
    }
}
