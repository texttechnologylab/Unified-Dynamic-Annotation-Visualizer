package org.texttechnologylab.udav.db;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.jooq.impl.DSL.constraint;
import static org.jooq.impl.DSL.name;

/**
 * Ensures the minimal set of tables needed by the UI exist, independent of optional importers.
 *
 * This prevents runtime failures like "relation json_data does not exist" when repositories query
 * optional tables before/without running the corresponding importer.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DSLContext dsl;

    @Value("${app.db.schema:public}")
    private String schema;

    public SchemaInitializer(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Ensure schema exists
        dsl.createSchemaIfNotExists(DSL.name(schema)).execute();

        // Ensure json_data exists (used by AnnotationController -> UIMATypeRepository)
        dsl.createTableIfNotExists(name(schema, SchemaObjectNames.TABLE_JSON_DATA))
                .column(SchemaObjectNames.COL_JSON_DATA_SOURCEFILE_NAME, SQLDataType.VARCHAR(255).nullable(false))
                // For Postgres, CLOB renders to TEXT
                .column(SchemaObjectNames.COL_JSON_DATA_JSON, SQLDataType.CLOB.nullable(false))
                .constraints(constraint("PK_" + SchemaObjectNames.TABLE_JSON_DATA)
                        .primaryKey(SchemaObjectNames.COL_JSON_DATA_SOURCEFILE_NAME))
                .execute();

        // Ensure pipeline exists (used by the UI and by MissingSchemaScanner)
        dsl.createTableIfNotExists(name(schema, SchemaObjectNames.TABLE_PIPELINE))
                .column(SchemaObjectNames.COL_PIPELINE_ID, SQLDataType.VARCHAR(255).nullable(false))
                .column(SchemaObjectNames.COL_PIPELINE_NAME, SQLDataType.VARCHAR(255).nullable(false))
                .column(SchemaObjectNames.COL_PIPELINE_JSON, SQLDataType.CLOB.nullable(false))
                .constraints(constraint("PK_" + SchemaObjectNames.TABLE_PIPELINE)
                        .primaryKey(SchemaObjectNames.COL_PIPELINE_ID))
                .execute();

        LOG.info("Ensured DB objects exist: schema={}, tables=[{}, {}]", schema,
                SchemaObjectNames.TABLE_JSON_DATA,
                SchemaObjectNames.TABLE_PIPELINE);
    }
}
