package org.texttechnologylab.udav.api.Repositories;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.texttechnologylab.udav.api.dto.UimaTypeRow;

import java.util.List;

@Repository
public class UIMATypeRepository {
    private final DSLContext dsl;

    public UIMATypeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public List<UimaTypeRow> list(int page, int size, String q) {
        int p = Math.max(0, page);
        int s = Math.max(1, size);

        var REG = DSL.table("uima_type_registry");
        var F_URI = DSL.field("uima_type_uri", String.class).as("uimaTypeUri");
        var F_CNT = DSL.field("row_count", Long.class).as("rowCount");

        var cond = (q == null || q.isBlank())
                ? DSL.noCondition()
                : DSL.field("uima_type_uri", String.class).likeIgnoreCase("%" + q + "%");

        return dsl.select(F_URI, F_CNT)
                .from(REG)
                .where(cond)
                .orderBy(F_CNT.desc().nullsLast())      // numeric sort, NULLS LAST
                .offset(p * s)
                .limit(s)
                .fetchInto(UimaTypeRow.class);          // ✅ maps by aliased field names
    }
}
