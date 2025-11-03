package org.texttechnologylab.udav.importer;

import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
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

    @Value("${app.pipeline-table:pipeline}")
    private String PIPELINE_TABLE;

    @Value("${app.pipeline-col-id:pipeline_id}")
    private String COL_PIPELINE_ID;

    @Value("${app.pipeline-col-json:json}")
    private String COL_PIPELINE_JSON;

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

            // app data table (in your app's schema)
            Table<?> P = DSL.table(DSL.name(schema, PIPELINE_TABLE));
            Field<String> P_ID = DSL.field(DSL.name(schema, PIPELINE_TABLE, COL_PIPELINE_ID), String.class);

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

    // --- Simple DB-based advisory lock using a small table ---
    private boolean tryAcquireLock(DSLContext dsl, String pipelineId) {
        Name lockTable = DSL.name(schema, "pipeline_locks");
        dsl.createTableIfNotExists(lockTable)
                .column("pipeline_id", org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false))
                .column("locked_at", org.jooq.impl.SQLDataType.TIMESTAMPWITHTIMEZONE.nullable(false))
                .constraints(DSL.constraint("PK_pipeline_locks").primaryKey("pipeline_id"))
                .execute();

        // try insert; if already exists, we didn’t get the lock
        try {
            dsl.insertInto(DSL.table(lockTable))
                    .columns(DSL.field("pipeline_id"), DSL.field("locked_at"))
                    .values(pipelineId, DSL.currentTimestamp())
                    .execute();
            return true;
        } catch (DataAccessException e) {
            // row exists -> locked by someone else
            return false;
        }
    }

    private void releaseLock(DSLContext dsl, String pipelineId) {
        dsl.deleteFrom(DSL.table(DSL.name(schema, "pipeline_locks")))
                .where(DSL.field("pipeline_id").eq(pipelineId))
                .execute();
    }

}
