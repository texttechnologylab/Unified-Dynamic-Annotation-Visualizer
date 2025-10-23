package org.texttechnologylab.udav.sources;

import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * DBAccess
 * ----------
 * Minimal access wrapper used by Source.java and others.
 * Updated for the DUUI/JooqDatabaseWriter schema:
 *  - Source-file discovery now comes from `sofas.sofa_uri` (NOT legacy SOFA table or per-type tables)
 *  - Only returns URIs that actually have text (sofa_string IS NOT NULL)
 */
public class DBAccess {

    @Getter
    private final DataSource dataSource;
    @Getter
    private final String schema;

    public DBAccess(DataSource dataSource, String schema) {
        this.dataSource = dataSource;
        this.schema = (schema == null || schema.isBlank()) ? "public" : schema;
    }

    public DBAccess(DataSource dataSource) {
        this(dataSource, "public");
    }

    /**
     * Returns all distinct source files/URIs present in the database that contain text.
     * In the new schema, this is `SELECT DISTINCT sofa_uri FROM sofas WHERE sofa_string IS NOT NULL`.
     */
    public Set<String> getSourceFiles() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(conn);
            Field<String> uri = DSL.field(DSL.name("public","sofas","sofa_uri"), String.class);
            Field<String> doc = DSL.field(DSL.name("public","sofas","doc_id"), String.class);
            Field<String> label = DSL.coalesce(uri, doc);

            var result = dsl.selectDistinct(label)
                    .from(DSL.table(DSL.name("public","sofas")))
                    .fetch(label);

            return new HashSet<>(result);
        }
    }

}
