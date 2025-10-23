package org.texttechnologylab.udav.database;

import org.jooq.DSLContext;

import java.util.Locale;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

/**
 * Resolves a UIMA type to the hashed physical table name the DUUI JooqDatabaseWriter created,
 * and constructs system/feature column names for that table.
 */
public final class TypeTableResolver {
    private final DSLContext dsl;
    private final String schema;

    public TypeTableResolver(DSLContext dsl, String schema) {
        this.dsl = dsl;
        this.schema = (schema == null || schema.isBlank()) ? "public" : schema;
    }

    /** Map UIMA type URI -> hashed table name via registry. Returns null if not found. */
    public String tableForType(String uimaTypeUri) {
        return dsl.select(field(name("table_name"), String.class))
                .from(table(name(schema, "uima_type_registry")))
                .where(field(name("uima_type_uri"), String.class).eq(uimaTypeUri))
                .fetchOneInto(String.class);
    }

    /** System column: <hash>_<base> (lowercased & sanitized). */
    public String sys(String tableHash, String base) {
        String b = (base == null ? "" : base)
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toLowerCase(Locale.ROOT);
        return tableHash + "_" + b;
    }

    /** Feature column: <hash>_f_<featureShortName> (lowercased & sanitized). */
    public String feat(String tableHash, String featureShortName) {
        String f = (featureShortName == null ? "" : featureShortName)
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toLowerCase(Locale.ROOT);
        return tableHash + "_f_" + f;
    }
}
