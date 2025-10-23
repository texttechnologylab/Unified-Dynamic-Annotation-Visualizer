package org.texttechnologylab.udav.sources;

import lombok.RequiredArgsConstructor;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.database.TypeTableResolver;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.pipeline.Pipeline;
import org.texttechnologylab.udav.pipeline.PipelineNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
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

    public void buildGeneratorTables(String schema) throws SQLException {

        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);

            dsl.createSchemaIfNotExists(DSL.name(schema)).execute();

            dsl.dropTableIfExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_FILENAME, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_NUMBER, org.jooq.impl.SQLDataType.DOUBLE.nullable(false))
                    .execute();

            dsl.dropTableIfExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            dsl.dropTableIfExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            dsl.dropTableIfExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TEXT, org.jooq.impl.SQLDataType.CLOB.nullable(false))
                    .execute();

            dsl.dropTableIfExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_STYLE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            dsl.dropTableIfExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS)).execute();
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_BEGIN, org.jooq.impl.SQLDataType.INTEGER.nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_END, org.jooq.impl.SQLDataType.INTEGER.nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();
        }
    }

    public void buildCustomTypes(Pipeline pipeline, String schema) {
        for (PipelineNode n : pipeline.getCustomTypes()) buildCustomType(n, schema);
    }

    private void buildCustomType(PipelineNode customTypeNode, String schema) {
        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);

            ArrayList<String> joinCols = null;
            try {
                JSONView joinColsView = customTypeNode.getConfig().get("settings").get("joinCols");
                if (joinColsView != null && joinColsView.isList()) {
                    joinCols = new ArrayList<>();
                    for (JSONView col : joinColsView) {
                        String colStr = normJoinName(col.toString());
                        if (!joinCols.contains(colStr)) joinCols.add(colStr);
                    }
                }
            } catch (Exception ignored) {
            }

            String joinPreset;
            try {
                joinPreset = customTypeNode.getConfig().get("settings").get("joinPreset").toString();
            } catch (Exception e) {
                joinPreset = (joinCols == null) ? "begin&End" : "manual";
            }

            JSONView subtypesView = customTypeNode.getConfig().get("contains");
            if (subtypesView == null || !subtypesView.isList()) {
                throw new IllegalArgumentException("\"contains\" must be a list of strings.");
            }

            TypeTableResolver resolver = new TypeTableResolver(dsl, schema);
            List<String> subtypeLogical = new ArrayList<>();
            List<String> subtypeHashes = new ArrayList<>();

            for (JSONView elementView : subtypesView) {
                String logicalType = elementView.toString().trim();
                if (subtypeLogical.stream().anyMatch(s -> s.equalsIgnoreCase(logicalType))) continue;
                String hash = resolver.tableForType(logicalType);
                if (hash == null) continue;
                subtypeLogical.add(logicalType);
                subtypeHashes.add(hash);
            }

            if (subtypeHashes.size() < 2) {
                System.out.println("Skipping custom type " + customTypeNode.getConfig().get("id") + " – need at least 2 valid subtypes.");
                return;
            }

            List<String> joinFieldNames;
            if ("begin&End".equalsIgnoreCase(joinPreset)) {
                joinFieldNames = new ArrayList<>(List.of("DOC_ID", "SOFA_ID", "FS_BEGIN", "FS_END"));
            } else {
                if (joinCols == null || joinCols.isEmpty()) {
                    throw new IllegalArgumentException("No joinCols defined for manual-join customType");
                }
                List<String> mapped = new ArrayList<>();
                for (String s : joinCols) {
                    String n = normJoinName(s);
                    if ("FILENAME".equals(n)) n = "DOC_ID";
                    if ("SOFA".equals(n)) n = "SOFA_ID";
                    mapped.add(n);
                }
                if (mapped.stream().noneMatch("DOC_ID"::equalsIgnoreCase)) mapped.add("DOC_ID");
                if (mapped.stream().noneMatch("SOFA_ID"::equalsIgnoreCase)) mapped.add("SOFA_ID");
                joinFieldNames = mapped;
            }

            buildCustomType_customJoinFields(customTypeNode, subtypeHashes, joinFieldNames, dsl, resolver, schema);

        } catch (Exception e) {
            System.out.println("There was an error creating the custom type: " + customTypeNode.toString());
        }
    }

    private void buildCustomType_customJoinFields(
            PipelineNode customTypeNode,
            List<String> subtypeHashes,
            List<String> joinFieldNames,
            DSLContext dsl,
            TypeTableResolver resolver,
            String schema) {

        String finalTableName = customTypeNode.getConfig().get("id").toString().toUpperCase(Locale.ROOT);
        String outName = finalTableName + "_RAW";

        List<Table<?>> joinTables = new ArrayList<>();
        Map<String, List<Field<?>>> mapTableToJoinFields = new HashMap<>();

        for (String hash : subtypeHashes) {
            Table<?> t = DSL.table(DSL.name(schema, hash));           // schema-qualified
            joinTables.add(t);

            List<Field<?>> joinFields = new ArrayList<>();
            for (String jf : joinFieldNames) {
                String norm = normJoinName(jf);
                String physical = switch (norm) {
                    case "DOC_ID" -> resolver.sys(hash, "doc_id");
                    case "SOFA_ID" -> resolver.sys(hash, "sofa_id");
                    case "FS_BEGIN" -> resolver.sys(hash, "fs_begin");
                    case "FS_END" -> resolver.sys(hash, "fs_end");
                    default -> resolver.feat(hash, norm);
                };
                joinFields.add(DSL.field(DSL.name(schema, hash, physical))); // schema-qualified
            }
            mapTableToJoinFields.put(hash, joinFields);
        }

        Condition joinCondition = DSL.trueCondition();
        for (int i = 0; i < joinTables.size() - 1; i++) {
            String L = subtypeHashes.get(i);
            String R = subtypeHashes.get(i + 1);
            List<Field<?>> leftF = mapTableToJoinFields.get(L);
            List<Field<?>> rightF = mapTableToJoinFields.get(R);
            for (int j = 0; j < leftF.size(); j++) {
                joinCondition = joinCondition.and(DSL.condition("{0} = {1}", leftF.get(j), rightF.get(j)));
            }
        }

        Table<?> joined = joinTables.getFirst();
        for (int i = 1; i < joinTables.size(); i++) {
            joined = joined.join(joinTables.get(i)).on(joinCondition);
        }

        List<SelectFieldOrAsterisk> selectFields = new ArrayList<>();
        selectFields.add(DSL.asterisk());

        String firstHash = subtypeHashes.getFirst();
        for (int j = 0; j < joinFieldNames.size(); j++) {
            Field<?> f = mapTableToJoinFields.get(firstHash).get(j);
            String alias = normJoinName(joinFieldNames.get(j));
            selectFields.add(f.as(alias));
        }

        // use schema-qualified names for temp and final tables
        Name OUT = DSL.name(schema, outName);
        Name FINAL = DSL.name(schema, finalTableName);

        dsl.dropTableIfExists(OUT).execute();
        dsl.createTable(OUT).as(dsl.select(selectFields).from(joined)).execute();

        for (String hash : subtypeHashes) {
            List<String> colsToDrop = new ArrayList<>();
            for (String jf : joinFieldNames) {
                String norm = normJoinName(jf);
                String physical = switch (norm) {
                    case "DOC_ID" -> resolver.sys(hash, "doc_id");
                    case "SOFA_ID" -> resolver.sys(hash, "sofa_id");
                    case "FS_BEGIN" -> resolver.sys(hash, "fs_begin");
                    case "FS_END" -> resolver.sys(hash, "fs_end");
                    default -> resolver.feat(hash, norm);
                };
                colsToDrop.add(physical.toUpperCase(Locale.ROOT));
            }
            if (!colsToDrop.isEmpty()) {
                dsl.alterTable(OUT).dropColumns(colsToDrop.toArray(String[]::new)).execute();
            }
        }

        cleanCustomTypeTable(subtypeHashes, OUT, FINAL, dsl, schema);
    }

    private void cleanCustomTypeTable(List<String> subtypeHashes, Name originalTableName, Name newTableName, DSLContext dsl, String schema) {

        List<String> columnNames = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, schema, originalTableName.last(), null)) { // filter by schema
                while (rs.next()) {
                    columnNames.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error while trying to load column names from DB", e);
        }
        if (columnNames.isEmpty()) return;

        Map<String, String> cleanedNames = new LinkedHashMap<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        for (String original : columnNames) {
            String cleaned = original;
            for (String hash : subtypeHashes) {
                String prefix = hash.toUpperCase(Locale.ROOT) + "_";
                if (original.toUpperCase(Locale.ROOT).startsWith(prefix)) {
                    cleaned = original.substring(prefix.length());
                    break;
                }
            }
            cleanedNames.put(original, cleaned);
            nameCounts.put(cleaned, nameCounts.getOrDefault(cleaned, 0) + 1);
        }

        List<Field<?>> selectFields = new ArrayList<>();
        Table<?> t = DSL.table(originalTableName);
        for (String col : columnNames) {
            String cleaned = cleanedNames.get(col);
            Field<Object> f = DSL.field(DSL.name(originalTableName.last(), col), Object.class);
            String alias = (!cleaned.equals(col) && nameCounts.get(cleaned) == 1)
                    ? (newTableName.last() + "_" + cleaned)
                    : (newTableName.last() + "_" + col);
            selectFields.add(f.as(alias));
        }

        dsl.transaction(cfg -> {
            DSLContext tx = DSL.using(cfg);
            tx.dropTableIfExists(newTableName).execute();
            tx.createTable(newTableName).as(tx.select(selectFields).from(t)).execute();
        });
    }
}
