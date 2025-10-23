package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.api.Repositories.VisualisationsRepository;

@Service
@Deprecated
public class VisualisationsService {

    private final VisualisationsRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public VisualisationsService(VisualisationsRepository repo) {
        this.repo = repo;
    }

    public String getVisualisationsJson(String pipelineId, boolean pretty) {
        String raw = repo.loadJsonByPipelineId(pipelineId, pipelineId).orElse("[]");
        try {
            JsonNode node = mapper.readTree(raw);
            return pretty
                    ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                    : mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Validate payload is JSON array and normalize to compact string.
     */
    private String normalizeArrayJson(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (!node.isArray()) throw new IllegalArgumentException("Payload must be a JSON array");
            return mapper.writeValueAsString(node);
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    /**
     * Create-only. Throws DuplicateKeyException if exists.
     */
    public void create(String pipelineId, String jsonArray) {
        String normalized = normalizeArrayJson(jsonArray);
        repo.insertNew(pipelineId, pipelineId, normalized);
    }

    /**
     * Replace-only. Returns false if missing.
     */
    public boolean replace(String pipelineId, String jsonArray) {
        String normalized = normalizeArrayJson(jsonArray);
        return repo.replaceExisting(pipelineId, pipelineId, normalized);
    }
}
