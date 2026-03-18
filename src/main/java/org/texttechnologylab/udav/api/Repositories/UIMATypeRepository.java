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
        var JSON = DSL.table("json_data");

        var F_URI = DSL.field("uima_type_uri", String.class);
        var F_SRC = DSL.field("sourcefile_name", String.class);
        var F_CNT = DSL.field("row_count", Long.class);

        var condRegistry = (q == null || q.isBlank())
                ? DSL.noCondition()
                : F_URI.likeIgnoreCase("%" + q + "%");

        var condJson = (q == null || q.isBlank())
                ? DSL.noCondition()
                : F_SRC.likeIgnoreCase("%" + q + "%");

        var registryQuery = dsl
                .select(
                        F_URI.as("uimaTypeUri"),
                        F_CNT.as("rowCount")
                )
                .from(REG)
                .where(condRegistry)
                .and(F_CNT.greaterThan(0L));

        var jsonQuery = dsl
                .select(
                        F_SRC.as("uimaTypeUri"),
                        DSL.val(-1L).as("rowCount")
                )
                .from(JSON)
                .where(condJson);

        var combined = registryQuery
                .unionAll(jsonQuery)
                .asTable("combined");

        var uri = combined.field("uimaTypeUri", String.class);
        var count = combined.field("rowCount", Long.class);

        return dsl
                .select(uri, count)
                .from(combined)
                .orderBy(count.desc().nullsLast())
                .offset(p * s)
                .limit(s)
                .fetchInto(UimaTypeRow.class);
    }
}