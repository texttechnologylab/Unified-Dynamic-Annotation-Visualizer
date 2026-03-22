package org.texttechnologylab.udav.api.Repositories;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.texttechnologylab.udav.api.dto.UimaTypeRow;
import org.texttechnologylab.udav.db.SchemaObjectNames;

import java.util.List;

@Repository
public class UIMATypeRepository {

    private final DSLContext dsl;

    @Value("${app.db.schema:public}")
    private String schema;

    public UIMATypeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public List<UimaTypeRow> list(int page, int size, String q) {

        int p = Math.max(0, page);
        int s = Math.max(1, size);

        var REG = DSL.table(DSL.name(schema, "uima_type_registry"));
        var JSON = DSL.table(DSL.name(schema, SchemaObjectNames.TABLE_JSON_DATA));

        var F_URI = DSL.field(DSL.name("uima_type_uri"), String.class);
        var F_SRC = DSL.field(DSL.name(SchemaObjectNames.COL_JSON_DATA_SOURCEFILE_NAME), String.class);
        var F_CNT = DSL.field(DSL.name("row_count"), Long.class);

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
