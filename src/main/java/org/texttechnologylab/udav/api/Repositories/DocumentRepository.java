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
        var cond = (q == null || q.isBlank())
                ? DSL.noCondition()
                : DSL.field("doc_id", String.class).likeIgnoreCase("%" + q + "%");
        return dsl.select(DSL.field("doc_id", String.class))
                .from(DSL.table("documents"))
                .where(cond)
                .orderBy(DSL.field("documents").asc())
                .offset(Math.max(0, page) * Math.max(1, size))
                .limit(Math.max(1, size))
                .fetchInto(String.class);
    }
}
