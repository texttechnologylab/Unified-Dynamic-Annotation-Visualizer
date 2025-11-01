package org.texttechnologylab.udav.api.service.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.SecureRandom;
import java.util.*;

/**
 * Flacht die TextImager-ähnliche Source-Definition auf eine generators-Liste ab.
 */
public final class GeneratorExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final SecureRandom RANDOM = new SecureRandom();

    private GeneratorExtractor() {
    }

    /**
     * Nimmt das komplette Input-JSON (als String) und liefert ein Objekt mit "generators": [...]
     */
    public static String extractGeneratorsJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            List<ObjectNode> generators = extractGenerators(root);
            ObjectNode out = MAPPER.createObjectNode();
            ArrayNode arr = MAPPER.createArrayNode();
            generators.forEach(arr::add);
            out.set("generators", arr);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse input JSON", e);
        }
    }

    /**
     * Kernmethode: nimmt den Wurzelknoten und gibt alle gefundenen Generatoren (flach) zurück.
     */
    public static List<ObjectNode> extractGenerators(JsonNode root) {
        List<ObjectNode> result = new ArrayList<>();

        // 1. from sources (recursive, like before)
        JsonNode sources = root.get("sources");
        if (sources != null && sources.isArray()) {
            for (JsonNode sourceNode : sources) {
                String sourceId = sourceNode.path("id").asText(null);
                String sourceType = sourceNode.path("type").asText(null);
                collectFromNode(sourceNode, sourceId, sourceType, result);
            }
        }

        // 2. from derivedGenerators: take AS IS, just push into result
        JsonNode derived = root.get("derivedGenerators");
        if (derived != null && derived.isArray()) {
            for (JsonNode d : derived) {
                // ensure it's an object
                if (d.isObject()) {
                    // make a deep copy so we don't mutate the original
                    ObjectNode copy = d.deepCopy();
                    result.add(copy);
                }
            }
        }

        return result;
    }


    /**
     * Läuft über einen Knoten, sammelt direkt definierte Generatoren und geht rekursiv in deren Kinder.
     *
     * @param parentSourceId   ID der übergeordneten Source (kann null sein)
     * @param parentSourceType Typ der übergeordneten Source (kann null sein)
     */
    private static void collectFromNode(JsonNode node,
                                        String parentSourceId,
                                        String parentSourceType,
                                        List<ObjectNode> target) {

        if (node.has("type") && !node.has("sources")) {
            ObjectNode gen = buildGenerator(node, parentSourceId, parentSourceType);
            target.add(gen);
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


    /**
     * Baut einen einzelnen Ziel-Generator im gewünschten Zielformat.
     * Hier legst du deine Mapping-Regeln fest.
     */
    private static ObjectNode buildGenerator(JsonNode src,
                                             String parentSourceId,
                                             String parentSourceType) {
        ObjectNode out = MAPPER.createObjectNode();

        String type = src.path("type").asText("Unknown");
        String id = src.path("id").asText(null);
        JsonNode settings = src.path("settings");

        // name
        out.put("name", "New " + type);

        // type
        out.put("type", type);

        // source
        // Wenn du eine feste Source willst, kannst du es hier hart coden.
        // Sonst: nimm den Typ/ID der übergeordneten Source, wenn vorhanden.
        if (parentSourceType != null) {
            out.put("source", parentSourceType);
        } else if (parentSourceId != null) {
            out.put("source", parentSourceId);
        } else {
            // Fallback – in deinem Beispiel:
            // "org.hucompute.textimager.uima.type.category.CategoryCoveredTagged"
            out.put("source", "uima.tcas.Annotation");
        }

        // settings
        if (settings != null && !settings.isMissingNode() && !settings.isNull()) {
            out.set("settings", settings.deepCopy());
        } else {
            out.set("settings", MAPPER.createObjectNode());
        }

        // id
        if (id == null || id.isEmpty()) {
            id = type + "-" + randomSuffix(6);
        }
        out.put("id", id);

        return out;
    }

    private static String randomSuffix(int len) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    // Beispiel-Nutzung
    public static void main(String[] args) {
        String input = """
                {
                  "id": "usecase3",
                  "sources": [
                    {
                      "id": "MyPOS",
                      "type": "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
                      "createsGenerators": [
                        {
                          "type": "combi",
                          "settings": { "categoriesBlacklist": [ "PUNCT" ] },
                          "createsGenerators": [
                            {
                              "type": "CategoryNumberMapping",
                              "id": "total",
                              "settings": { "categoriesBlacklist": [ "DET" ] }
                            },
                            {
                              "type": "TextFormatting",
                              "id": "text-color",
                              "settings": {
                                "sofaFile": "ID21200100.xmi",
                                "style": "underline"
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "derivedGenerators": [
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

        String out = extractGeneratorsJson(input);
        System.out.println(out);
    }
}
