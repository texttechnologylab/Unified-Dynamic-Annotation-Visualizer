package org.texttechnologylab.udav.generators;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.database.TypeTableResolver;
import org.texttechnologylab.udav.generators.common_properties.CommonFeatureCategoryColors;
import org.texttechnologylab.udav.generators.common_properties.CommonProperties;
import org.texttechnologylab.udav.generators.settings.FilterList;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.SourceDerived;
import org.texttechnologylab.udav.generators.sources.Source;
import org.texttechnologylab.udav.generators.sources.SourceUIMA;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;

import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class CategoryNumberColorMapping extends GeneratorUIMA {

    private Set<String> sourceFiles;
    private String[] features;
    private Map<String, Feature> mapFileToRootFeatures;

    private CommonFeatureCategoryColors commonFeatureCategoryColors;

    public CategoryNumberColorMapping(String id, JSONView configGenerator, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
    }


    @Override
    public Set<Class<? extends Source>> preSetup_getAllSourceClasses() {
        return Set.of(SourceDerived.class, SourceUIMA.class);
    }

    @Override
    public Set<Class<? extends CommonProperties>> preSetup_getAllCommonPropertyClasses() {
        return Set.of(CommonFeatureCategoryColors.class);
    }

    @Override
    public void preSetup_setCommonPropertiesObj(CommonProperties commonProperties) {
        if (commonFeatureCategoryColors != null || !(commonProperties instanceof CommonFeatureCategoryColors)) return; // common feature already set or wrong type
        commonFeatureCategoryColors = (CommonFeatureCategoryColors) commonProperties;
    }

    @Override
    public void setup() {}

    @Override
    public void setup_step1() throws SQLException {
        if (commonFeatureCategoryColors == null) commonFeatureCategoryColors = new CommonFeatureCategoryColors();
        if (SourceDerived.class.equals(source.getClass())) {
            // Derived generator:
            settings.defineFilterListUniversalSetString("files", null); // TODO: The universal set for derived is the set of files of all source generators
        } else if (SourceUIMA.class.equals(source.getClass())) {
            // UIMA generator:
            Set<String> allFiles = ((SourceUIMA) source).determineAllSourceFiles();
            settings.defineFilterListUniversalSetString("files", allFiles);
            FilterList<String> filterListSourceFiles = settings.generateStringFilterList("files");
            this.sourceFiles = filterListSourceFiles.getWhitelist();
            if (this.sourceFiles == null) throw new IllegalStateException("No source files defined for generator \"" + id + "\".");
            String featureName = settings.getStringSettingOrDefault("featureName", null);
            String singleColorStr = settings.getStringSettingOrDefault("color", null);
            Color singleColor = null;
            if (singleColorStr != null) { try { singleColor = Color.decode(singleColorStr); } catch (NumberFormatException ignored) {}}
            FilterList<String> filterListCategories = settings.generateStringFilterList("categories");
            Map<String, Map<String, Double>> categoryNumberMap = dbCreateCategoryCountMap(featureName, filterListCategories.getWhitelist(), filterListCategories.getBlacklist());
            Map<String, Double> categoryNumberMapFlat = calculateTotalFromCategoryCountMap(categoryNumberMap);
            commonFeatureCategoryColors.addFeatureToCategoryCountMap(tempFeatureName, categoryNumberMapFlat);
            features = new String[]{tempFeatureName};
            mapFileToRootFeatures = new HashMap<>();
            for (Map.Entry<String, Map<String, Double>> entry : categoryNumberMap.entrySet()) {
                String file = entry.getKey(); Map<String, Double> categoryNumber = entry.getValue();
                Map<String, Entry> entries = new HashMap<>();
                for (Map.Entry<String, Double> e : categoryNumber.entrySet()) {
                    entries.put(e.getKey(), new Entry(e.getKey(), e.getValue(), null));
                }
                Feature newFeature = new Feature(tempFeatureName, entries, singleColor);
                mapFileToRootFeatures.put(file, newFeature);
            }
        }
    }

    @Override
    public void setup_step2() {
        if (SourceDerived.class.equals(source.getClass())) {
            // Derived generator:
            return;
        }
    }

    @Override
    public void writeToDB() throws SQLException {
        if (features.length != 1) throw new IllegalStateException("CategoryNumber generator doesn't work for feature amounts other than 1 (yet).");

        final String schema = dbAccess.getSchema();
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_FILENAME, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_NUMBER, org.jooq.impl.SQLDataType.DOUBLE.nullable(false))
                    .execute();

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            // Table - CategoryNumber
            Table<?> T = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER));

            // Columns
            Field<String> F_GEN = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER,
                    DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> F_FILE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER,
                    DBConstants.TABLEATTR_FILENAME), String.class);
            Field<String> F_CAT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER,
                    DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);
            Field<Double> F_NUM = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYNUMBER,
                    DBConstants.TABLEATTR_GENERATORDATA_NUMBER), Double.class);

            List<Query> batch = new ArrayList<>();

            for (Map.Entry<String, Feature> featureEntry : mapFileToRootFeatures.entrySet()) {
                String file = featureEntry.getKey(); Feature feature = featureEntry.getValue();
                for (Entry e : feature.entries.values()) {
                    batch.add(
                            dsl.insertInto(T)
                                    .columns(F_GEN, F_FILE, F_CAT, F_NUM)
                                    .values(id, file, e.categoryName, e.number)
                    );
                }
            }

            if (!batch.isEmpty()) {
                dsl.batch(batch).execute();
            }

            // Table - CategoryColor
            T = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR));

            // Columns
            F_GEN = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR,
                    DBConstants.TABLEATTR_GENERATORID), String.class);
            F_CAT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR,
                    DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);
            Field<String> F_COL = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR,
                    DBConstants.TABLEATTR_GENERATORDATA_COLOR), String.class);

            batch = new ArrayList<>();

            Map<String, Color> categoryColorMap = commonFeatureCategoryColors.getCategoryColorMap(features[0]);
            for (Map.Entry<String, Feature> featureEntry : mapFileToRootFeatures.entrySet()) {
                Feature feature = featureEntry.getValue();
                Color singleColor = feature.singleColor;
                for (Entry e : feature.entries.values()) {
                    Color colorObj = singleColor == null? categoryColorMap.get(e.categoryName) : singleColor;
                    String color = String.format("#%02x%02x%02x", colorObj.getRed(), colorObj.getGreen(), colorObj.getBlue());
                    batch.add(
                            dsl.insertInto(T)
                                    .columns(F_GEN, F_CAT, F_COL)
                                    .values(id, e.categoryName, color)
                    );
                }
            }

            if (!batch.isEmpty()) {
                dsl.batch(batch).execute();
            }
        }
    }

    private static class Feature {
        private final String featureName;
        private final Map<String, Entry> entries;
        private final Color singleColor;

        private Feature(String featureName, Map<String, Entry> entries, Color singleColor) {
            this.featureName = featureName;
            this.entries = entries;
            this.singleColor = singleColor;
        }
    }

    private static class Entry {
        private final String categoryName;
        private final double number;
        private final Map<String, Feature> subFeatures;

        private Entry(String categoryName, double number, Map<String, Feature> subFeatures) {
            this.categoryName = categoryName;
            this.number = number;
            this.subFeatures = subFeatures;
        }
    }

    private static Map<String, Double> calculateTotalFromCategoryCountMap(Map<String, Map<String, Double>> categoryCountMap) {
        HashMap<String, Double> totals = new HashMap<>();

        for (Map<String, Double> innerMap : categoryCountMap.values()) {
            for (Map.Entry<String, Double> entry : innerMap.entrySet()) {
                String category = entry.getKey();
                Double count = entry.getValue();
                totals.merge(category, count, Double::sum);
            }
        }

        return totals;
    }


    private Map<String, Map<String, Double>> dbCreateCategoryCountMap(String featureName, Set<String> categoriesWhitelist, Set<String> categoriesBlacklist) throws SQLException {
        final String schema = DBConstants.DB_SCHEMA_UIMA;
        final String hash = ((SourceUIMA) source).getTableHash();

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);
            TypeTableResolver resolver = new TypeTableResolver(dsl, schema);

            var T          = DSL.table(DSL.name(schema, hash));
            var DOC_ID     = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "doc_id")),   String.class);
            var SOFA_ID    = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "sofa_id")),  String.class);
            Field<String> featureField = resolveFeatureField(dsl, schema, hash, featureName, null);


            var S       = DSL.table(DSL.name(schema, "sofas"));
            var S_DOC   = DSL.field(DSL.name(schema, "sofas", "doc_id"), String.class);
            var S_SOFA  = DSL.field(DSL.name(schema, "sofas", "sofa_id"), String.class);
            var S_URI   = DSL.field(DSL.name(schema, "sofas", "sofa_uri"), String.class);

            // Label = prefer URI, fallback to DOC_ID
            Field<String> LABEL = DSL.coalesce(S_URI, S_DOC);

            // Normalize input list to match LABEL (if ".xmi" -> strip)
            Set<String> normalized = new HashSet<>();
            for (String f : sourceFiles) {
                if (f == null) continue;
                String s = f.trim();
                normalized.add(s);
                if (s.endsWith(".xmi")) normalized.add(s.substring(0, s.length() - 4)); // doc_id form
            }

            Condition cond = LABEL.in(normalized);
            if (categoriesWhitelist != null && !categoriesWhitelist.isEmpty()) {
                cond = cond.and(featureField.in(categoriesWhitelist));
            }
            if (categoriesBlacklist != null && !categoriesBlacklist.isEmpty()) {
                cond = cond.and(featureField.notIn(categoriesBlacklist));
            }

            var recs = dsl
                    .select(LABEL, featureField, DSL.count())
                    .from(T)
                    .join(S).on(DOC_ID.eq(S_DOC).and(SOFA_ID.eq(S_SOFA)))
                    .where(cond)
                    .groupBy(LABEL, featureField)
                    .fetch();

            Map<String, Map<String, Double>> out = new HashMap<>();
            for (Record r : recs) {
                String label = r.get(LABEL);
                String cat   = r.get(featureField);
                if (cat == null || cat.isBlank()) cat = "(null)";
                Double cnt   = r.get(2, Integer.class).doubleValue();

                out.computeIfAbsent(label, k -> new HashMap<>())
                        .merge(cat, cnt, Double::sum);
            }
            return out;
        }
    }
}
