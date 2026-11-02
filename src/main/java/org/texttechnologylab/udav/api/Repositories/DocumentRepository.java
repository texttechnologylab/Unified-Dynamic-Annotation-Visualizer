package org.texttechnologylab.udav.api.Repositories;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class DocumentRepository {

    private final DSLContext dsl;

    public DocumentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ----- helpers to build schema-qualified objects per call -----

    @Transactional(readOnly = true)
    public List<String> listDocumentIds(int page, int size, String q) {
        // The documents table is created by the DUUI importer. Before a corpus has been imported
        // there are no documents yet; the corpus filter asks on every page load, so this must
        // not be an error.
        if (!documentsTableExists()) {
            return List.of();
        }
        var cond = (q == null || q.isBlank())
                ? DSL.noCondition()
                : DSL.field("doc_id", String.class).likeIgnoreCase("%" + q + "%");
        return dsl.select(DSL.field("doc_id", String.class))
                .from(DSL.table("documents"))
                .where(cond)
                .orderBy(DSL.field("doc_id", String.class).asc())
                .offset(Math.max(0, page) * Math.max(1, size))
                .limit(Math.max(1, size))
                .fetchInto(String.class);
    }

    // Tags are the collection ids the DUUI importer stores with each document
    @Transactional(readOnly = true)
    public List<String> listTags(String q) {
        if (!documentsTableExists() || !columnExists("documents", "collection_id")) {
            return List.of();
        }
        Field<String> tag = DSL.field(DSL.name("collection_id"), String.class);
        Condition cond = tag.isNotNull();
        if (q != null && !q.isBlank()) {
            cond = cond.and(tag.likeIgnoreCase("%" + q + "%"));
        }
        return dsl.selectDistinct(tag)
                .from(DSL.table("documents"))
                .where(cond)
                .orderBy(tag.asc())
                .limit(100)
                .fetchInto(String.class);
    }

    /**
     * Ids of the documents whose collection id is one of the tags and whose import time
     * (sofas.created_at) lies within the dates, inclusive. Null or empty arguments are not applied.
     */
    @Transactional(readOnly = true)
    public Set<String> documentIdsMatching(Set<String> tags, LocalDate from, LocalDate to) {
        if (!documentsTableExists()) {
            return new HashSet<>();
        }
        Field<String> docId = DSL.field(DSL.name("d", "doc_id"), String.class);
        Field<OffsetDateTime> createdAt = DSL.field(DSL.name("s", "created_at"), OffsetDateTime.class);
        ZoneId zone = ZoneId.systemDefault();

        Condition cond = DSL.noCondition();
        if (tags != null && !tags.isEmpty()) {
            if (!columnExists("documents", "collection_id")) {
                return new HashSet<>();
            }
            cond = cond.and(DSL.field(DSL.name("d", "collection_id"), String.class).in(tags));
        }
        if (from != null) {
            cond = cond.and(createdAt.ge(from.atStartOfDay(zone).toOffsetDateTime()));
        }
        if (to != null) {
            cond = cond.and(createdAt.lt(to.plusDays(1).atStartOfDay(zone).toOffsetDateTime()));
        }
        return new HashSet<>(dsl.selectDistinct(docId)
                .from(DSL.table("documents").as("d"))
                .join(DSL.table("sofas").as("s")).on(DSL.field(DSL.name("s", "doc_id"), String.class).eq(docId))
                .where(cond)
                .fetchInto(String.class));
    }

    private boolean documentsTableExists() {
        Integer count = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "tables")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq(DSL.currentSchema()))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("documents"))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }

    // Databases imported before the column existed have no collection ids
    private boolean columnExists(String table, String column) {
        Integer count = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq(DSL.currentSchema()))
                .and(DSL.field(DSL.name("table_name"), String.class).eq(table))
                .and(DSL.field(DSL.name("column_name"), String.class).eq(column))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }
}
