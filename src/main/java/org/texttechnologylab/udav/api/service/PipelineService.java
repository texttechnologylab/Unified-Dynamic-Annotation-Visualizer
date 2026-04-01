package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.db.SchemaObjectNames;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;

@Service
public class PipelineService {

    private static final String TABLE = SchemaObjectNames.TABLE_PIPELINE;
    private static final String COL_ID = SchemaObjectNames.COL_PIPELINE_ID;
    private static final String COL_NAME = SchemaObjectNames.COL_PIPELINE_NAME;
    private static final String COL_JSON = SchemaObjectNames.COL_PIPELINE_JSON;
    private final SourceBuildService sourceBuildService;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Value("${app.db.schema:public}")
    private String schema;

    Logger LOGGER = LoggerFactory.getLogger(PipelineService.class);

    public PipelineService(SourceBuildService sourceBuildService, DataSource dataSource, ObjectMapper objectMapper) {
        this.sourceBuildService = sourceBuildService;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void ensureTable() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            dsl.createSchemaIfNotExists(DSL.name(schema)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, TABLE))
                    .column(DSL.name(COL_ID), SQLDataType.VARCHAR(255).nullable(false))
                    .column(DSL.name(COL_NAME), SQLDataType.VARCHAR(255).nullable(false))
                    .column(DSL.name(COL_JSON), SQLDataType.CLOB.nullable(false))
                    .constraints(DSL.constraint("PK_" + TABLE).primaryKey(DSL.name(COL_ID)))
                    .execute();
        }
    }

    @Transactional(readOnly = true)
    public List<String> listIds(int page, int size, String q) throws Exception {
        return listSummaries(page, size, q).stream()
                .map(summary -> summary.get("id"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> listSummaries(int page, int size, String q) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            var fieldId = DSL.field(DSL.name(COL_ID), String.class);
            var fieldName = DSL.field(DSL.name(COL_NAME), String.class);
            var cond = (q == null || q.isBlank())
                    ? DSL.noCondition()
                    : fieldId.likeIgnoreCase("%" + q + "%")
                    .or(fieldName.likeIgnoreCase("%" + q + "%"));
            return dsl.select(fieldId, fieldName)
                    .from(DSL.table(DSL.name(schema, TABLE)))
                    .where(cond)
                    .orderBy(fieldId.asc())
                    .offset(Math.max(0, page) * Math.max(1, size))
                    .limit(Math.max(1, size))
                    .fetch(record -> Map.of(
                            "id", record.get(fieldId),
                            "name", Objects.requireNonNullElse(record.get(fieldName), record.get(fieldId))
                    ));
        }
    }

    @Transactional(readOnly = true)
    public JsonNode get(String id) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            String json = dsl.select(DSL.field(DSL.name(COL_JSON), String.class))
                    .from(DSL.table(DSL.name(schema, TABLE)))
                    .where(DSL.field(DSL.name(COL_ID)).eq(id))
                    .fetchOneInto(String.class);
            if (json == null) throw new ResponseStatusException(NOT_FOUND, "Pipeline not found");

            return parseJson(json);
        }
    }

    @Transactional
    public String create(JsonNode json) throws Exception {
        JsonNode normalizedJson = normalizeGeneratorLayout(json);
        String id = normalizedJson.path("id").asText("main");

        String jsonStr = toString(normalizedJson);

        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            // check exists
            boolean exists = dsl.fetchExists(
                    dsl.selectOne()
                            .from(DSL.table(DSL.name(schema, TABLE)))
                            .where(DSL.field(DSL.name(COL_ID)).eq(id))
            );
            if (exists) throw new ResponseStatusException(CONFLICT, "Pipeline already exists");

            dsl.insertInto(DSL.table(DSL.name(schema, TABLE)),
                            DSL.field(DSL.name(COL_ID)),
                            DSL.field(DSL.name(COL_NAME)),
                            DSL.field(DSL.name(COL_JSON)))
                    .values(id, id, jsonStr)
                    .execute();

            sourceBuildService.startBuild(id, id);

            LOGGER.info("Created new pipeline: {}", id);
        }

        return id;
    }

    @Transactional
    public void update(JsonNode json) throws Exception {
        JsonNode normalizedJson = normalizeGeneratorLayout(json);
        String id = normalizedJson.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing or empty pipeline id");
        }

        String jsonStr = toString(normalizedJson);

        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);

            int updated = dsl.update(DSL.table(DSL.name(schema, TABLE)))
                    .set(DSL.field(DSL.name(COL_NAME)), id)
                    .set(DSL.field(DSL.name(COL_JSON)), jsonStr)
                    .where(DSL.field(DSL.name(COL_ID)).eq(id))
                    .execute();

            if (updated == 0) {
                throw new ResponseStatusException(NOT_FOUND, "Pipeline not found");
            }

            sourceBuildService.startBuild(id, id);
            LOGGER.info("Updated pipeline: {}", id);
        }
    }

    @Transactional
    public void delete(String id) {
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);

            // 1) Delete the pipeline row
            int deleted = dsl.deleteFrom(DSL.table(DSL.name(schema, TABLE)))
                    .where(DSL.field(DSL.name(COL_ID)).eq(id))
                    .execute();
            if (deleted == 0) {
                throw new ResponseStatusException(NOT_FOUND, "Pipeline not found");
            }

            // 2) Drop the schema + everything inside it
            int ignored = dsl.dropSchema(DSL.name(id))
                    .cascade()
                    .execute();

            LOGGER.info("Deleted pipeline: {}", id);

        } catch (org.jooq.exception.DataAccessException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to drop schema: " + id, e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            // Stored value is not valid JSON (shouldn't happen if we always validate on write)
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Stored JSON is invalid");
        }
    }

    private String toString(JsonNode json) {
        try {
            // Normalize JSON; also rejects invalid JSON early if coming as raw string
            return (json == null) ? "{}" : objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid JSON");
        }
    }

    private JsonNode normalizeGeneratorLayout(JsonNode json) {
        if (json == null || !json.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid pipeline JSON object");
        }

        ObjectNode root = ((ObjectNode) json).deepCopy();
        JsonNode sourcesNode = root.get("sources");
        if (!sourcesNode.isArray()) {
            return root;
        }

        Map<String, ObjectNode> sourceById = new LinkedHashMap<>();
        Map<String, Set<String>> generatorIdsBySource = new LinkedHashMap<>();

        for (JsonNode sourceNode : (ArrayNode) sourcesNode) {
            if (!sourceNode.isObject()) {
                continue;
            }
            ObjectNode sourceObject = (ObjectNode) sourceNode;
            String sourceId = sourceObject.path("id").asText(null);
            if (sourceId == null || sourceId.isBlank()) {
                continue;
            }

            JsonNode createsGeneratorsNode = sourceObject.get("createsGenerators");
            ArrayNode createsGenerators = (createsGeneratorsNode instanceof ArrayNode)
                    ? (ArrayNode) createsGeneratorsNode
                    : sourceObject.putArray("createsGenerators");

            Set<String> existingIds = new LinkedHashSet<>();
            for (JsonNode existingGenerator : createsGenerators) {
                String existingId = existingGenerator.path("id").asText("").trim();
                if (!existingId.isEmpty()) {
                    existingIds.add(existingId);
                }
            }

            sourceById.put(sourceId, sourceObject);
            generatorIdsBySource.put(sourceId, existingIds);
        }

        JsonNode topLevelGenerators = root.get("generators");
        if (topLevelGenerators instanceof ArrayNode generatorsArray) {
            for (JsonNode generatorNode : generatorsArray) {
                if (!generatorNode.isObject()) {
                    continue;
                }

                String sourceId = generatorNode.path("source").asText(null);
                if (sourceId == null || sourceId.isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "Generator is missing source reference");
                }

                ObjectNode targetSource = sourceById.get(sourceId);
                if (targetSource == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "Generator references unknown source id: " + sourceId);
                }

                String generatorId = generatorNode.path("id").asText("").trim();
                Set<String> seenIds = generatorIdsBySource.get(sourceId);
                if (!generatorId.isEmpty() && seenIds.contains(generatorId)) {
                    continue;
                }

                ObjectNode generatorCopy = ((ObjectNode) generatorNode).deepCopy();
                generatorCopy.remove("source");
                ((ArrayNode) targetSource.get("createsGenerators")).add(generatorCopy);

                if (!generatorId.isEmpty()) {
                    seenIds.add(generatorId);
                }
            }
        }

        root.remove("generators");
        return root;
    }
}
