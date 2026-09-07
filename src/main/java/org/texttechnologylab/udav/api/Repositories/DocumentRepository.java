package org.texttechnologylab.udav.api.Repositories;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    private boolean documentsTableExists() {
        Integer count = dsl.selectCount()
                .from(DSL.table(DSL.name("information_schema", "tables")))
                .where(DSL.field(DSL.name("table_schema"), String.class).eq(DSL.currentSchema()))
                .and(DSL.field(DSL.name("table_name"), String.class).eq("documents"))
                .fetchOne(0, Integer.class);
        return count != null && count > 0;
    }
}
