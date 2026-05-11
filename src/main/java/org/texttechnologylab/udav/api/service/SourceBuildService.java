package org.texttechnologylab.udav.api.service;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.pipeline.Pipeline;
import org.texttechnologylab.udav.sources.DBAccess;
import org.texttechnologylab.udav.sources.SourceBuildOps;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class SourceBuildService {

    private static final Logger logger = LoggerFactory.getLogger(SourceBuildService.class);
    private final DataSource dataSource;
    private final SourceBuildOps ops;

    @Value("${app.db.schema:public}")
    private String appDbSchema;

    /**
     * Build all sources for a given schema + pipeline.
     * This version runs synchronously and is not concurrency-guarded.
     */
    public void startBuild(String schema, @Nullable String pipelineId) { //TODO: remove duplicate unnecessary call
        try {
            doBuild(schema, pipelineId);
        } catch (Exception e) {
            logger.error("Build failed for pipeline={}: {}", pipelineId, e.getMessage(), e);
            throw new RuntimeException("Build failed for pipeline=" + pipelineId, e);
        }
    }

    private void doBuild(String schema, @Nullable String pipelineId) throws Exception {
        if (pipelineId == null || pipelineId.isBlank()) {
            pipelineId = "main";
        }
        String targetSchema = normalizeSchemaName(schema);
        String tempSchema = targetSchema + "__tmp";
        String oldSchema = targetSchema + "__old";

        assertSafePipelineSchema(targetSchema);
        assertSafeAuxSchema(tempSchema);
        assertSafeAuxSchema(oldSchema);

        cleanupTransientSchemas(tempSchema, oldSchema);

        // The pipeline row lives in app.db.schema; generator data is built in temp and then swapped into the target schema.
        DBAccess readAccess = new DBAccess(dataSource, appDbSchema);
        DBAccess writeAccess = new DBAccess(dataSource, tempSchema);

        // Load pipeline JSON from the shared schema, build generators with the pipeline's own schema.
        Pipeline pipeline = Pipeline.fromDB(readAccess, writeAccess, pipelineId);
        String id = pipeline.getId();

        // Persist visualization JSONs and build types/tables
        Collection<Pipeline> coll = new ArrayList<>();
        coll.add(pipeline);
        ops.savePipelinesVisualizationsJSONs(coll, tempSchema);

        // Generate & save generator data
        pipeline.saveToDB();

        // Swap rebuilt temp schema into production name, then remove previous schema.
        swapSchemas(targetSchema, tempSchema, oldSchema);

        logger.info("Build completed for schema=" + targetSchema + ", pipeline=" + id);
    }

    private void swapSchemas(String targetSchema, String tempSchema, String oldSchema) {
        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);
            boolean targetRenamed = false;

            try {
                dsl.dropSchemaIfExists(DSL.name(oldSchema)).cascade().execute();

                if (schemaExists(dsl, targetSchema)) {
                    dsl.alterSchema(DSL.name(targetSchema)).renameTo(DSL.name(oldSchema)).execute();
                    targetRenamed = true;
                }

                dsl.alterSchema(DSL.name(tempSchema)).renameTo(DSL.name(targetSchema)).execute();

                if (targetRenamed) {
                    dsl.dropSchemaIfExists(DSL.name(oldSchema)).cascade().execute();
                }
            } catch (Exception swapError) {
                // Restore original schema name if swap did not finish.
                try {
                    if (targetRenamed && !schemaExists(dsl, targetSchema) && schemaExists(dsl, oldSchema)) {
                        dsl.alterSchema(DSL.name(oldSchema)).renameTo(DSL.name(targetSchema)).execute();
                    }
                } catch (Exception rollbackError) {
                    logger.error("Failed to rollback schema swap for {}: {}", targetSchema, rollbackError.getMessage(), rollbackError);
                }
                throw new IllegalStateException("Failed to swap rebuilt schema for pipeline " + targetSchema, swapError);
            } finally {
                // Ensure transient schemas do not remain after success or failure.
                safeDropSchemaIfExists(dsl, tempSchema);
                safeDropSchemaIfExists(dsl, oldSchema);
            }
        } catch (Exception e) {
            throw new RuntimeException("Schema swap failed for pipeline " + targetSchema, e);
        }
    }

    private void cleanupTransientSchemas(String tempSchema, String oldSchema) {
        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);
            safeDropSchemaIfExists(dsl, tempSchema);
            safeDropSchemaIfExists(dsl, oldSchema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cleanup transient schemas for pipeline build", e);
        }
    }

    private void safeDropSchemaIfExists(DSLContext dsl, String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            return;
        }
        if (schemaExists(dsl, schemaName)) {
            dsl.dropSchemaIfExists(DSL.name(schemaName)).cascade().execute();
        }
    }

    private boolean schemaExists(DSLContext dsl, String schemaName) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(DSL.table(DSL.name("information_schema", "schemata")))
                        .where(DSL.field(DSL.name("information_schema", "schemata", "schema_name"), String.class).eq(schemaName))
        );
    }

    private String normalizeSchemaName(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Pipeline schema must not be null/blank");
        }
        return schema.trim();
    }

    private void assertSafePipelineSchema(String schemaName) {
        if (schemaName.equalsIgnoreCase(appDbSchema)) {
            throw new IllegalArgumentException("Refusing to rebuild reserved app schema: " + schemaName);
        }
        if (schemaName.equalsIgnoreCase(DBConstants.DB_SCHEMA_UIMA)) {
            throw new IllegalArgumentException("Refusing to rebuild reserved UIMA schema: " + schemaName);
        }
    }

    private void assertSafeAuxSchema(String schemaName) {
        if (schemaName.equalsIgnoreCase(appDbSchema)) {
            throw new IllegalArgumentException("Refusing to use reserved app schema as transient schema: " + schemaName);
        }
        if (schemaName.equalsIgnoreCase(DBConstants.DB_SCHEMA_UIMA)) {
            throw new IllegalArgumentException("Refusing to use reserved UIMA schema as transient schema: " + schemaName);
        }
    }
}
