package org.texttechnologylab.udav.sources;

import lombok.RequiredArgsConstructor;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.pipeline.Pipeline;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class SourceBuildOps {

    private final DataSource dataSource;

    private static String normJoinName(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    public void savePipelinesVisualizationsJSONs(Collection<Pipeline> pipelines, String schema) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);

            dsl.createSchemaIfNotExists(DSL.name(schema)).execute();

            Name T = DSL.name(schema, DBConstants.TABLENAME_VISUALIZATIONJSONS);
            Name C1 = DSL.name(schema, DBConstants.TABLENAME_VISUALIZATIONJSONS, DBConstants.TABLEATTR_PIPELINEID);
            Name C2 = DSL.name(schema, DBConstants.TABLENAME_VISUALIZATIONJSONS, DBConstants.TABLEATTR_JSONSTR);

            dsl.dropTableIfExists(T).execute();

            dsl.createTableIfNotExists(T)
                    .column(DBConstants.TABLEATTR_PIPELINEID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_JSONSTR, org.jooq.impl.SQLDataType.CLOB.nullable(false))
                    .constraints(DSL.constraint("PK_" + DBConstants.TABLENAME_VISUALIZATIONJSONS)
                            .primaryKey(DBConstants.TABLEATTR_PIPELINEID))
                    .execute();

            Table<?> table = DSL.table(T);
            Field<String> fId = DSL.field(C1, String.class);
            Field<String> fJson = DSL.field(C2, String.class);

            for (Pipeline p : pipelines) {
                String pipelineID = p.getId();
                String visualizationsJSON = p.getRootJSONView().get("widgets").toJson(false);

                dsl.insertInto(table).columns(fId, fJson)
                        .values(pipelineID, visualizationsJSON)
                        .onConflict(fId).doUpdate().set(fJson, visualizationsJSON)
                        .execute();
            }
        }
    }
}
