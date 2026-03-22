package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

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
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            var cond = (q == null || q.isBlank())
                    ? DSL.noCondition()
                    : DSL.field(DSL.name(COL_ID), String.class).likeIgnoreCase("%" + q + "%");
            return dsl.select(DSL.field(DSL.name(COL_ID), String.class))
                    .from(DSL.table(DSL.name(schema, TABLE)))
                    .where(cond)
                    .orderBy(DSL.field(DSL.name(COL_ID)).asc())
                    .offset(Math.max(0, page) * Math.max(1, size))
                    .limit(Math.max(1, size))
                    .fetchInto(String.class);
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
        String id = json.get("id").asText("main");

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
                    .values(id, id, jsonStr)
                    .execute();

            sourceBuildService.startBuild(id, id);

            LOGGER.info("Created new pipeline: {}", id);
        }

        return id;
    }

    @Transactional
    public void update(JsonNode json) throws Exception {
        String id = json.get("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing or empty pipeline id");
        }
        String jsonStr = toString(json);
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);
            int updated = dsl.update(DSL.table(DSL.name(schema, TABLE)))
                    .set(DSL.field(DSL.name(COL_JSON)), jsonStr)
                    .where(DSL.field(DSL.name(COL_ID)).eq(id))
                    .execute();
            if (updated == 0) throw new ResponseStatusException(NOT_FOUND, "Pipeline not found");
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
}
