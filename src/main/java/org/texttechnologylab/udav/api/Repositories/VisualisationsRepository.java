package org.texttechnologylab.udav.api.Repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.texttechnologylab.udav.database.DBConstants;

import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Repository
@Deprecated
public class VisualisationsRepository {

    private final DSLContext dsl;
    private final ObjectMapper mapper = new ObjectMapper();

    public VisualisationsRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<String> loadJsonByPipelineId(String schema, String pipelineId) {
        var V = V(schema);
        var PIPELINEID = F_PIPELINEID(schema);
        var JSONSTR = F_JSONSTR(schema);

        return dsl.select(JSONSTR)
                .from(V)
                .where(PIPELINEID.eq(pipelineId))
                .limit(1)
                .fetchOptional(JSONSTR);
    }

    // ----- helpers to build schema-qualified objects per call -----
    private Table<?> V(String schema) {
        return table(name(schema, DBConstants.TABLENAME_VISUALIZATIONJSONS));
    }

    private Field<String> F_PIPELINEID(String schema) {
        return field(name(schema, DBConstants.TABLENAME_VISUALIZATIONJSONS, DBConstants.TABLEATTR_PIPELINEID), String.class);
    }

    private Field<String> F_JSONSTR(String schema) {
        return field(name(schema, DBConstants.TABLENAME_VISUALIZATIONJSONS, DBConstants.TABLEATTR_JSONSTR), String.class);
    }

    /**
     * Create only. Throws DuplicateKeyException if already exists (requires UNIQUE on PIPELINEID).
     */
    public void insertNew(String schema, String pipelineId, String json) {
        var V = V(schema);
        var PIPELINEID = F_PIPELINEID(schema);
        var JSONSTR = F_JSONSTR(schema);

        try {
            dsl.insertInto(V)
                    .columns(PIPELINEID, JSONSTR)
                    .values(pipelineId, json)
                    .execute();
        } catch (DataAccessException e) {
            throw new DuplicateKeyException("Pipeline already exists: " + pipelineId, e);
        }
    }

    /**
     * Replace only. Returns true if updated, false if missing.
     */
    public boolean replaceExisting(String schema, String pipelineId, String json) {
        var V = V(schema);
        var PIPELINEID = F_PIPELINEID(schema);
        var JSONSTR = F_JSONSTR(schema);

        int updated = dsl.update(V)
                .set(JSONSTR, json)
                .where(PIPELINEID.eq(pipelineId))
                .execute();
        return updated > 0;
    }

    /**
     * Lookup a single visualization meta by pipelineId + visualizationId.
     * Returns empty if not found.
     */
    public Optional<VisualizationMeta> findMeta(String schema, String pipelineId, String visualizationId) {
        var V = V(schema);
        var PIPELINEID = F_PIPELINEID(schema);
        var JSONSTR = F_JSONSTR(schema);

        String json = dsl.select(JSONSTR)
                .from(V)
                .where(PIPELINEID.eq(pipelineId))
                .fetchOne(JSONSTR);

        if (json == null) return Optional.empty();

        try {
            return getVisualizationMeta(visualizationId, json);
        } catch (Exception e) {
            throw new DataAccessException("Failed to parse visualization JSON for pipeline " + pipelineId, e) {
            };
        }
    }

    private Optional<VisualizationMeta> getVisualizationMeta(String visualizationId, String json) throws JsonProcessingException {
        JsonNode root = mapper.readTree(json);
        if (root.isArray()) {
            for (JsonNode n : root) {
                String id = n.path("id").asText(null);
                if (visualizationId.equals(id)) {
                    String type = n.path("type").asText(null);
                    String gen = n.path("generator").path("id").asText(null);
                    if (type != null && gen != null) {
                        return Optional.of(new VisualizationMeta(id, type, gen));
                    }
                }
            }
        }
        return Optional.empty();
    }

    public record VisualizationMeta(String visualizationId, String type, String generatorId) {
    }
}
