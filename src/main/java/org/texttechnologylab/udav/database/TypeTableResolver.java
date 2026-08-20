package org.texttechnologylab.udav.database;

import org.jooq.DSLContext;
import org.jooq.Record4;

import java.util.*;

import static org.jooq.impl.DSL.*;

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

    /**
     * Converts annotation uri to table hash name.
     */
    public String tableForType(String uimaTypeUri) {
        return dsl.select(field(name("table_name"), String.class))
                .from(table(name(schema, "uima_type_registry")))
                .where(field(name("uima_type_uri"), String.class).eq(uimaTypeUri))
                .fetchOneInto(String.class);
    }

    /**
     * Resolve a UIMA type to its own table plus descendant type tables that currently have rows.
     * Falls back to the exact table for registries created before hierarchy metadata existed.
     */
    public List<String> tablesForTypeIncludingDescendants(String uimaTypeUri) {
        if (uimaTypeUri == null || uimaTypeUri.isBlank()) return List.of();
        if (!registryHasColumn("supertype_uri")) {
            String exact = tableForType(uimaTypeUri);
            return exact == null ? List.of() : List.of(exact);
        }

        var F_URI = field(name("uima_type_uri"), String.class);
        var F_TABLE = field(name("table_name"), String.class);
        var F_SUPER = field(name("supertype_uri"), String.class);
        var F_COUNT = field(name("row_count"), Long.class);

        List<Record4<String, String, String, Long>> rows = dsl
                .select(F_URI, F_TABLE, F_SUPER, F_COUNT)
                .from(table(name(schema, "uima_type_registry")))
                .fetch();

        Map<String, String> tableByType = new HashMap<>();
        Map<String, Boolean> rowsByType = new HashMap<>();
        Map<String, List<String>> childrenByParent = new HashMap<>();
        for (Record4<String, String, String, Long> row : rows) {
            String uri = row.value1();
            String table = row.value2();
            String superUri = row.value3();
            Long rowCount = row.value4();
            if (uri == null || table == null) continue;
            tableByType.put(uri, table);
            rowsByType.put(uri, rowCount == null || rowCount > 0L);
            if (superUri != null && !superUri.isBlank()) {
                childrenByParent.computeIfAbsent(superUri, ignored -> new ArrayList<>()).add(uri);
            }
        }

        LinkedHashSet<String> tables = new LinkedHashSet<>();
        String exact = tableByType.get(uimaTypeUri);
        if (exact == null) exact = tableForType(uimaTypeUri);
        if (exact != null) tables.add(exact);

        ArrayDeque<String> pending = new ArrayDeque<>(childrenByParent.getOrDefault(uimaTypeUri, List.of()));
        Set<String> seenTypes = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            String uri = pending.removeFirst();
            if (!seenTypes.add(uri)) continue;
            String table = tableByType.get(uri);
            if (table != null && rowsByType.getOrDefault(uri, false)) tables.add(table);
            pending.addAll(childrenByParent.getOrDefault(uri, List.of()));
        }
        return List.copyOf(tables);
    }

    private boolean registryHasColumn(String columnName) {
        Integer count = dsl.selectCount()
                .from(table(name("information_schema", "columns")))
                .where(field(name("table_schema"), String.class).eq(schema))
                .and(field(name("table_name"), String.class).eq("uima_type_registry"))
                .and(field(name("column_name"), String.class).eq(columnName))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    /**
     * System column: <hash>_<base> (lowercased & sanitized).
     */
    public String sys(String tableHash, String base) {
        String b = (base == null ? "" : base)
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toLowerCase(Locale.ROOT);
        return tableHash + "_" + b;
    }
}
