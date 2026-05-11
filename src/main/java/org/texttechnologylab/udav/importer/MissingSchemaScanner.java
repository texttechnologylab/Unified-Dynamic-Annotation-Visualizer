package org.texttechnologylab.udav.importer;

import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.db.SchemaObjectNames;
import org.texttechnologylab.udav.pipeline.PipelineProcessor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

@Component
public class MissingSchemaScanner implements ApplicationRunner {

    private final DataSource dataSource;
    private final PipelineProcessor processor;

    // ---- Configure your table/column names (or inject DBConstants) ----
    @Value("${app.db.schema:public}")
    private String schema;

    // Pipeline table/columns are now centralized; keep old @Value overrides out to prevent drift

    // Optional rate limit / safety
    @Value("${app.missing-schema.max-per-run:50}")
    private int maxPerRun;

    public MissingSchemaScanner(DataSource dataSource,
                                PipelineProcessor processor) {
        this.dataSource = dataSource;
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        scanAndProcessOnce();
    }

    private void scanAndProcessOnce() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(c);

            // Check if pipeline table exists before attempting to query it
            if (!tableExists(dsl, schema, SchemaObjectNames.TABLE_PIPELINE)) {
                return;
            }

            // app data table (in your app's schema)
            Table<?> P = DSL.table(DSL.name(schema, SchemaObjectNames.TABLE_PIPELINE));
            Field<String> P_ID = DSL.field(DSL.name(schema, SchemaObjectNames.TABLE_PIPELINE, SchemaObjectNames.COL_PIPELINE_ID), String.class);

            // catalog view for schemas
            Table<?> SCHEMATA = DSL.table(DSL.name("information_schema", "schemata"));
            Field<String> SCHEMA_NAME = DSL.field(DSL.name("information_schema", "schemata", "schema_name"), String.class);

            // Return (pipeline_id, json) where NO DB schema with that exact name exists
            List<Record1<String>> candidates = dsl
                    .select(P_ID)
                    .from(P)
                    .whereNotExists(
                            dsl.selectOne()
                                    .from(SCHEMATA)
                                    .where(SCHEMA_NAME.eq(P_ID))              // exact match (case-sensitive)
                    )
                    .limit(maxPerRun)
                    .fetch();

            for (Record1<String> r : candidates) {
                String pid = r.value1();

                // Lightweight per-pipeline lock: create/hold a transient row lock in a helper table
                if (!tryAcquireLock(dsl, pid)) {
                    continue; // someone else is processing
                }

                try {
                    processor.process(pid);
                } catch (Exception ex) {
                    // Log & continue; you might want a dead-letter table
                    System.err.println("Failed processing pipeline " + pid + ": " + ex.getMessage());
                } finally {
                    releaseLock(dsl, pid);
                }
            }
        }
    }

    private boolean tableExists(DSLContext dsl, String schemaName, String tableName) {
        try {
            Integer count = dsl.selectCount()
                    .from(DSL.table(DSL.name("information_schema", "tables")))
                    .where(DSL.field(DSL.name("table_schema")).eq(schemaName)
                            .and(DSL.field(DSL.name("table_name")).eq(tableName)))
                    .fetchOne(0, Integer.class);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            return false;
        }
    }

    // --- Simple DB-based advisory lock using a small table ---
    private boolean tryAcquireLock(DSLContext dsl, String pipelineId) {
        Name lockTable = DSL.name(schema, SchemaObjectNames.TABLE_PIPELINE_LOCKS);
        dsl.createTableIfNotExists(lockTable)
                .column(SchemaObjectNames.COL_PIPELINE_LOCKS_PIPELINE_ID, org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false))
                .column(SchemaObjectNames.COL_PIPELINE_LOCKS_LOCKED_AT, org.jooq.impl.SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
                .constraints(DSL.constraint("PK_" + SchemaObjectNames.TABLE_PIPELINE_LOCKS)
                        .primaryKey(SchemaObjectNames.COL_PIPELINE_LOCKS_PIPELINE_ID))
                .execute();

        // try insert; if already exists, we didn’t get the lock
        try {
            dsl.insertInto(DSL.table(lockTable))
                    .columns(DSL.field(SchemaObjectNames.COL_PIPELINE_LOCKS_PIPELINE_ID), DSL.field(SchemaObjectNames.COL_PIPELINE_LOCKS_LOCKED_AT))
                    .values(pipelineId, DSL.currentTimestamp())
                    .execute();
            return true;
        } catch (DataAccessException e) {
            // row exists -> locked by someone else
            return false;
        }
    }

    private void releaseLock(DSLContext dsl, String pipelineId) {
        dsl.deleteFrom(DSL.table(DSL.name(schema, SchemaObjectNames.TABLE_PIPELINE_LOCKS)))
                .where(DSL.field(SchemaObjectNames.COL_PIPELINE_LOCKS_PIPELINE_ID).eq(pipelineId))
                .execute();
    }

}
