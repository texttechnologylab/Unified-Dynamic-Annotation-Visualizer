package org.texttechnologylab.udav.database;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * Wraps a jOOQ DSLContext to produce table and field references
 * using normalized names from NameMapper.
 */
public class QueryHelper {
    private final DSLContext ctx;

    /**
     * Initialize with a DSLContext.
     *
     * @param ctx the jOOQ DSLContext to use for query construction
     */
    public QueryHelper(DSLContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Create a jOOQ Table reference for the given domain table name.
     *
     * @param name the domain table name
     * @return a jOOQ Table instance with the mapped name
     */
    public Table<?> table(String name) {
        return DSL.table(DSL.name(NameMapper.mapTable(name)));
    }

    /**
     * Create a typed jOOQ Field reference for the given table and field.
     *
     * @param table the domain table name
     * @param field the domain field name
     * @param type  the Java class of the field's type
     * @param <T>   the field value type
     * @return a jOOQ Field of type T with the mapped name
     */
    public <T> Field<T> field(String table, String field, Class<T> type) {
        return DSL.field(DSL.name(NameMapper.mapField(table, field)), type);
    }

    /**
     * Create an untyped jOOQ Field reference for the given table and field.
     *
     * @param table the domain table name
     * @param field the domain field name
     * @return a jOOQ Field of Object type with the mapped name
     */
    public Field<Object> field(String table, String field) {
        return DSL.field(DSL.name(NameMapper.mapField(table, field)));
    }

    /**
     * Expose the underlying DSLContext.
     *
     * @return the DSLContext instance
     */
    public DSLContext dsl() {
        return ctx;
    }
}
