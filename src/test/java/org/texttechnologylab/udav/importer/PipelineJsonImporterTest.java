package org.texttechnologylab.udav.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A pipeline file whose id is already taken is stored under a fresh id. The id inside the stored
 * JSON must follow, because the view page and the data API read it from there.
 */
class PipelineJsonImporterTest {

    private final PipelineJsonImporter importer = new PipelineJsonImporter(null, null, "pipelines", false);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void duplicateCarriesItsNewIdInsideTheJson() throws Exception {
        String stored = importer.withPipelineId("{\"id\":\"p-1\",\"name\":\"demo\",\"widgets\":[]}", "p-2");

        JsonNode root = mapper.readTree(stored);
        assertEquals("p-2", root.get("id").asText());
        assertEquals("demo", root.get("name").asText());
        assertEquals(0, root.get("widgets").size());
    }

    @Test
    void envelopeFormIsRewrittenInsideThePipelinesArray() throws Exception {
        String stored = importer.withPipelineId("{\"pipelines\":[{\"id\":\"p-1\",\"name\":\"demo\"}]}", "p-2");

        JsonNode root = mapper.readTree(stored);
        assertEquals("p-2", root.get("pipelines").get(0).get("id").asText());
    }
}
