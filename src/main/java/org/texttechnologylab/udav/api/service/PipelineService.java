package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.texttechnologylab.udav.db.SchemaObjectNames;
import org.texttechnologylab.udav.api.service.utils.GeneratorConverter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    Logger LOGGER = LoggerFactory.getLogger(PipelineService.class);
    @Value("${app.db.schema:public}")
    private String schema;

    /**
     * Short-TTL cache of the normalized JSON string for a pipeline.
     *
     * <p>A single batch export reads the same pipeline at least {@code 2 + 2W} times (once by the
     * export service, once by {@code AppController.view} when the headless page loads, and once per
     * {@code /api/data} request during view init and again on bulk refetch), each time paying a CLOB
     * SELECT plus {@link GeneratorConverter#toNewFormat}.
     *
     * <p>The string is cached rather than the parsed tree: {@link #get} hands callers a mutable
     * {@code ObjectNode}, and several of them keep it around, so sharing one instance would let
     * a stray mutation in any caller corrupt every later request. Re-parsing per call keeps
     * callers isolated while still skipping the database and the normalization.
     */
    private final ConcurrentHashMap<String, CachedPipeline> pipelineCache = new ConcurrentHashMap<>();

    @Value("${app.pipeline-cache.ttl-ms:5000}")
    private long pipelineCacheTtlMs;

    private record CachedPipeline(String normalizedJson, long expiresAtNanos) {
        boolean isFresh() {
            return System.nanoTime() - expiresAtNanos < 0;
        }
    }

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
    public List<String> listAllIds() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            var fieldId = DSL.field(DSL.name(COL_ID), String.class);
            return dsl.select(fieldId)
                    .from(DSL.table(DSL.name(schema, TABLE)))
                    .orderBy(fieldId.asc())
                    .fetch(fieldId);
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
            var fieldJson = DSL.field(DSL.name(COL_JSON), String.class);
            var cond = (q == null || q.isBlank())
                    ? DSL.noCondition()
                    : fieldId.likeIgnoreCase("%" + q + "%")
                    .or(fieldName.likeIgnoreCase("%" + q + "%"));
            return dsl.select(fieldId, fieldName, fieldJson)
                    .from(DSL.table(DSL.name(schema, TABLE)))
                    .where(cond)
                    .orderBy(fieldName.asc())
                    .offset(Math.max(0, page) * Math.max(1, size))
                    .limit(Math.max(1, size))
                    .fetch(record -> {
                        String id = record.get(fieldId);
                        String name = record.get(fieldName);

                        Map<String, String> summary = new LinkedHashMap<>();
                        summary.put("id", id);
                        summary.put("name", name);
                        return summary;
                    });
        }
    }

    @Transactional(readOnly = true)
    public JsonNode get(String id) throws Exception {
        if (pipelineCacheTtlMs > 0) {
            CachedPipeline cached = pipelineCache.get(id);
            if (cached != null && cached.isFresh()) {
                return parseJson(cached.normalizedJson());
            }
        }

        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            String json = dsl.select(DSL.field(DSL.name(COL_JSON), String.class))
                    .from(DSL.table(DSL.name(schema, TABLE)))
                    .where(DSL.field(DSL.name(COL_ID)).eq(id))
                    .orderBy(DSL.field(DSL.name(COL_NAME)).asc())
                    .fetchOneInto(String.class);
            if (json == null) throw new ResponseStatusException(NOT_FOUND, "Pipeline not found");

            String normalized = withRowId(GeneratorConverter.toNewFormat(json), id);
            if (pipelineCacheTtlMs > 0) {
                pipelineCache.put(id, new CachedPipeline(
                        normalized, System.nanoTime() + pipelineCacheTtlMs * 1_000_000L));
            }
            return parseJson(normalized);
        }
    }

    /**
     * Drops the cached copy of {@code id}; called from every write path.
     *
     * <p>Evicts immediately and again after commit. The second eviction matters: the
     * write is not visible to other connections until the transaction commits, so a concurrent
     * reader slipping in between would otherwise re-cache the pre-write value.
     */
    private void invalidate(String id) {
        pipelineCache.remove(id);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    pipelineCache.remove(id);
                }
            });
        }
    }

    @Transactional
    public String create(JsonNode json) throws Exception {
        String id = json.get("id").asText();
        String name = json.get("name").asText();
        String jsonStr = toString(json);

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
                    .values(id, name, jsonStr)
                    .execute();

            invalidate(id);
            sourceBuildService.startBuild(id, id);

            LOGGER.info("Created new pipeline: {}", id);
        }

        return id;
    }

    @Transactional
    public void update(JsonNode json) throws Exception {
        String id = json.get("id").asText();
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing or empty pipeline id");
        }

        String name = json.get("name").asText();
        String jsonStr = toString(json);

        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);

            int updated = dsl.update(DSL.table(DSL.name(schema, TABLE)))
                    .set(DSL.field(DSL.name(COL_NAME)), name)
                    .set(DSL.field(DSL.name(COL_JSON)), jsonStr)
                    .where(DSL.field(DSL.name(COL_ID)).eq(id))
                    .execute();

            if (updated == 0) {
                throw new ResponseStatusException(NOT_FOUND, "Pipeline not found");
            }

            invalidate(id);
            sourceBuildService.startBuild(id, id);
            LOGGER.info("Updated pipeline: {}", id);
        }
    }

    @Transactional
    public void delete(String id) {
        invalidate(id);
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

    /**
     * The id inside the JSON has to be the row's: the view page, the editor and the data API take
     * the pipeline id from there. Duplicates stored by older versions of PipelineJsonImporter still
     * carry the original pipeline's id, so it is corrected on read.
     */
    private String withRowId(String json, String id) {
        JsonNode node = parseJson(json);
        if (!(node instanceof ObjectNode obj) || id.equals(obj.path("id").asText())) return json;
        obj.put("id", id);
        return toString(obj);
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

}
