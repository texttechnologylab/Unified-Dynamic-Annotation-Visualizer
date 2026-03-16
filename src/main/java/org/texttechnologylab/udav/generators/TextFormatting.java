package org.texttechnologylab.udav.generators;

import lombok.Getter;
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
import org.texttechnologylab.udav.generators.sources.SourceUIMA;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;

import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

@Getter
public class TextFormatting extends GeneratorUIMA {

    public static final String DEFAULT_STYLE = "underline";


    private String UIMAsofaFile;
    private String UIMAsofaID;
    private CommonFeatureCategoryColors commonFeatureCategoryColors;
    private Set<Dataset> datasets;
    private String text;


    public TextFormatting(String id, JSONView configGenerator, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
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
        datasets = new HashSet<>();
        if (SourceDerived.class.equals(source.getClass())) {
            // Derived generator:
            SourceDerived sourceDerived = (SourceDerived) source;
            getUIMAFileAndSofaFromDerived(sourceDerived);
            text = null;
            for (Generator g : sourceDerived.getSourceGenerators()) {
                if (!TextFormatting.class.equals(g.getClass())) continue; // Ignore source generators of non-matching classes
                TextFormatting tf = (TextFormatting) g;
                if (text == null) { text = tf.getText(); } // TODO: Improve text checking
                for (Dataset d : tf.getDatasets()) { datasets.add(new Dataset(d)); }
            }
        } else if (SourceUIMA.class.equals(source.getClass())) {
            // UIMA generator:
            SourceUIMA sourceUIMA = (SourceUIMA) source;
            this.UIMAsofaID = settings.getStringSettingOrDefault("sofaID", null);
            String sofaFile = settings.getStringSettingOrDefault("sofaFile", null);
            if (sofaFile == null) {
                Set<String> allFiles = sourceUIMA.determineAllSourceFiles();
                settings.defineFilterListUniversalSetString("files", allFiles);
                FilterList<String> filterListSourceFiles = settings.generateStringFilterList("files");
                Set<String> sourceFiles = filterListSourceFiles.getWhitelist();
                if (sourceFiles == null || sourceFiles.isEmpty()) {
                    throw new IllegalArgumentException("No sofaFile defined for generator \"" + id + "\" and fileWhitelist is empty or undefined.");
                } else {
                    for (String f : sourceFiles) { this.UIMAsofaFile = f; break; }
                }
            } else {
                this.UIMAsofaFile = sofaFile;
            }
            String[] sofa = dbGetSofa(this.UIMAsofaFile, this.UIMAsofaID);
            UIMAsofaFile =      sofa[0];
            UIMAsofaID =        sofa[1];
            this.text =         sofa[2];
            String featureName = settings.getStringSettingOrDefault("featureName", null);
            String style = settings.getStringSettingOrDefault("style", null);
            if (style == null) style = DEFAULT_STYLE;
            String singleColorStr = settings.getStringSettingOrDefault("color", null);
            Color singleColor = null;
            if (singleColorStr != null) { try { singleColor = Color.decode(singleColorStr); } catch (NumberFormatException ignored) {}}
            FilterList<String> filterListCategories = settings.generateStringFilterList("categories");
            ArrayList<Dataset.Segment> segments = dbCreateTextFormattingSegments(featureName, UIMAsofaFile, UIMAsofaID, filterListCategories.getWhitelist(), filterListCategories.getBlacklist());
            String type = sourceUIMA.getUri() + "." + tempFeatureName;
            Dataset newDataset = new Dataset(tempFeatureName, type, style, singleColor, segments);
            datasets.add(newDataset);
            commonFeatureCategoryColors.addFeatureToCategoryCountMap(tempFeatureName, newDataset.categoryCountMap);
        } else {
            throw new IllegalArgumentException("Unsupported source for generator \"" + id + "\".");
        }
    }

    @Override
    public void setup_step2() {
        if (SourceDerived.class.equals(source.getClass())) {
            // Derived generator:
            return;
        }

        for (Dataset d : datasets) {
            d.categoryColorMap = commonFeatureCategoryColors.getCategoryColorMap(d.featureName);
            d.categoryColorMap.keySet().retainAll(d.categoryCountMap.keySet());
        }
    }

    @Override
    public void writeToDB() throws SQLException {
        final String schema = dbAccess.getSchema();
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TEXT, org.jooq.impl.SQLDataType.CLOB.nullable(false))
                    .execute();

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_STYLE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_TYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_BEGIN, org.jooq.impl.SQLDataType.INTEGER.nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_END, org.jooq.impl.SQLDataType.INTEGER.nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_CATEGORY, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            // ---------- Tables ----------
            Table<?> T_TEXT = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT));
            Table<?> T_STYLE = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE));
            Table<?> T_COLOR = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR));
            Table<?> T_SEGS = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS));

            // ---------- Columns (schema-qualified & quoted) ----------
            // TEXT
            Field<String> GID_TEXT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TXT_TEXT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT, DBConstants.TABLEATTR_GENERATORDATA_TEXT), String.class);

            // TYPESTYLE
            Field<String> GID_STYLE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TYP_STYLE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE, DBConstants.TABLEATTR_GENERATORDATA_TYPE), String.class);
            Field<String> STY_STYLE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE, DBConstants.TABLEATTR_GENERATORDATA_STYLE), String.class);

            // TYPECATEGORYCOLOR
            Field<String> GID_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TYP_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORDATA_TYPE), String.class);
            Field<String> CAT_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);
            Field<String> COL_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORDATA_COLOR), String.class);

            // TYPESEGMENTS
            Field<String> GID_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TYP_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_TYPE), String.class);
            Field<Integer> BEG_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_BEGIN), Integer.class);
            Field<Integer> END_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_END), Integer.class);
            Field<String> CAT_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);

            // ---------- Insert text ----------
            dsl.insertInto(T_TEXT)
                    .columns(GID_TEXT, TXT_TEXT)
                    .values(id, text)
                    .execute();

            if (datasets == null || datasets.isEmpty()) return;

            for (Dataset ds : datasets) {
                // STYLE row
                dsl.insertInto(T_STYLE)
                        .columns(GID_STYLE, TYP_STYLE, STY_STYLE)
                        .values(id, ds.type, ds.style)
                        .execute();

                // COLORS batch
                List<Query> batch = new ArrayList<>();
                for (String category : ds.categoryCountMap.keySet()) {
                    Color c = (ds.singleColor == null)? ds.categoryColorMap.get(category) : ds.singleColor;
                    String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                    batch.add(
                            dsl.insertInto(T_COLOR)
                                    .columns(GID_COLOR, TYP_COLOR, CAT_COLOR, COL_COLOR)
                                    .values(id, ds.type, category, hex)
                    );
                }
                if (!batch.isEmpty()) dsl.batch(batch).execute();
                batch.clear();

                // SEGMENTS batch
                for (Dataset.Segment s : ds.segments) {
                    batch.add(
                            dsl.insertInto(T_SEGS)
                                    .columns(GID_SEGS, TYP_SEGS, BEG_SEGS, END_SEGS, CAT_SEGS)
                                    .values(id, ds.type, s.begin, s.end, s.category)
                    );
                }
                if (!batch.isEmpty()) dsl.batch(batch).execute();
            }
        }
    }

    public static class Dataset {
        private final String featureName;
        private final String type;
        private final String style;
        private final Color singleColor;
        private final List<Segment> segments;
        private final Map<String, Double> categoryCountMap;
        private Map<String, Color> categoryColorMap;


        private Dataset(String featureName, String type, String style, Color singleColor, List<Segment> segments) {
            this.featureName = featureName;
            this.type = type;
            this.style = style;
            this.categoryColorMap = null;
            this.singleColor = singleColor;
            this.segments = segments;
            this.categoryCountMap = segmentsToCategoryCountMap(segments);
        }

        private Dataset(Dataset copyOf) {
            this.featureName = copyOf.featureName;
            this.type = copyOf.type;
            this.style = copyOf.style;
            this.categoryColorMap = new HashMap<>(copyOf.categoryColorMap);
            this.singleColor = copyOf.singleColor;
            this.segments = new ArrayList<>(copyOf.segments);
            this.categoryCountMap = new HashMap<>(copyOf.categoryCountMap);
        }

        private record Segment(int begin, int end, String category) {}


        private static Map<String, Double> segmentsToCategoryCountMap(List<Dataset.Segment> segments) {
            HashMap<String, Double> categoryCount = new HashMap<>();
            for (Dataset.Segment s : segments) {
                if (categoryCount.containsKey(s.category)) {
                    categoryCount.put(s.category, categoryCount.get(s.category) + 1.0);
                } else {
                    categoryCount.put(s.category, 0.0);
                }
            }
            return categoryCount;
        }
    }

    private void getUIMAFileAndSofaFromDerived(SourceDerived sourceDerived) {
        UIMAsofaFile = null; UIMAsofaID = null;
        for (Generator g : sourceDerived.getSourceGenerators()) {
            if (!TextFormatting.class.equals(g.getClass())) continue; // Ignore source generators of non-matching classes
            TextFormatting tf = (TextFormatting) g;
            if (UIMAsofaFile == null && UIMAsofaID == null) {
                UIMAsofaFile = tf.UIMAsofaFile;
                UIMAsofaID = tf.UIMAsofaID;
            } else {
                if (!Objects.equals(UIMAsofaID, tf.UIMAsofaID) || !Objects.equals(UIMAsofaFile, tf.UIMAsofaFile)) {
                    UIMAsofaFile = null; UIMAsofaID = null; break;
                }
            }
        }
    }

    private ArrayList<Dataset.Segment> dbCreateTextFormattingSegments(String featureName, String sofaFile, String sofaId, Set<String> categoriesWhitelist, Set<String> categoriesBlacklist) throws SQLException {
        final String schema = DBConstants.DB_SCHEMA_UIMA;
        final String hash = ((SourceUIMA) source).getTableHash();

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);
            TypeTableResolver resolver = new TypeTableResolver(dsl, schema);

            var T          = DSL.table(DSL.name(schema, hash));
            var DOC_ID     = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "doc_id")),   String.class);
            var SOFA_ID    = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "sofa_id")),  String.class);
            var FS_BEGIN   = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "fs_begin")), Integer.class);
            var FS_END     = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "fs_end")),   Integer.class);
            Field<String> featureField = resolveFeatureField(dsl, schema, hash, featureName, null);

            var S       = DSL.table(DSL.name(schema, "sofas"));
            var S_DOC   = DSL.field(DSL.name(schema, "sofas", "doc_id"), String.class);
            var S_SOFA  = DSL.field(DSL.name(schema, "sofas", "sofa_id"), String.class);
            var S_URI   = DSL.field(DSL.name(schema, "sofas", "sofa_uri"), String.class);

            // Normalize sofaFile and resolve the SOFA row; this also normalizes sofaId if null
            String[] resolved = dbGetSofa(sofaFile, sofaId);
            String label     = resolved[0]; // URI or DOC_ID
            String resolvedId= resolved[1];

            // We need the DOC_ID for the chosen label + sofaId
            String baseDoc = label.endsWith(".xmi") ? label.substring(0, label.length() - 4) : label;
            String docForSofa = dsl.select(S_DOC)
                    .from(S)
                    .where(S_SOFA.eq(resolvedId)
                            .and(S_URI.equalIgnoreCase(label).or(S_DOC.equalIgnoreCase(baseDoc))))
                    .orderBy(S_SOFA.asc())
                    .limit(1)
                    .fetchOne(S_DOC);

            if (docForSofa == null) {
                throw new IllegalArgumentException("Could not resolve DOC_ID for label=" + label + " and sofa_id=" + resolvedId);
            }

            Condition cond = DOC_ID.eq(docForSofa).and(SOFA_ID.eq(resolvedId));
            if (categoriesWhitelist != null && !categoriesWhitelist.isEmpty()) {
                cond = cond.and(featureField.in(categoriesWhitelist));
            }
            if (categoriesBlacklist != null && !categoriesBlacklist.isEmpty()) {
                cond = cond.and(featureField.notIn(categoriesBlacklist));
            }

            var recs = dsl
                    .select(FS_BEGIN, FS_END, featureField)
                    .from(T)
                    .where(cond)
                    .orderBy(FS_BEGIN.asc(), FS_END.asc())
                    .fetch();

            ArrayList<Dataset.Segment> segments = new ArrayList<>(recs.size());
            for (Record r : recs) {
                Integer b = r.get(FS_BEGIN);
                Integer e = r.get(FS_END);
                String cat = r.get(featureField);
                if (cat == null || cat.isBlank()) cat = "(null)";
                if (b != null && e != null && b >= 0 && e >= b) {
                    segments.add(new Dataset.Segment(b, e, cat));
                }
            }
            return segments;
        }
    }

    private String[] dbGetSofa(String wantedSofaFile, String wantedSofaId) throws SQLException {
        final String schema = DBConstants.DB_SCHEMA_UIMA;

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            var SOFAS    = DSL.table(DSL.name(schema, "sofas"));
            var S_DOC    = DSL.field(DSL.name(schema, "sofas", "doc_id"), String.class);
            var S_SOFAID = DSL.field(DSL.name(schema, "sofas", "sofa_id"), String.class);
            var S_URI    = DSL.field(DSL.name(schema, "sofas", "sofa_uri"), String.class);
            var S_STR    = DSL.field(DSL.name(schema, "sofas", "sofa_string"), String.class);

            // Normalize input: accept URI, doc_id, or "ID... .xmi"
            String in;
            if (wantedSofaFile != null && !wantedSofaFile.isBlank()) { in = wantedSofaFile.trim(); }
            else { throw new IllegalArgumentException("No source files available to resolve SOFA."); }
            String baseDoc = in.endsWith(".xmi") ? in.substring(0, in.length() - 4) : in;


            // Try to find the row by (uri==in) OR (doc_id==baseDoc)
            Condition byUriOrDoc = S_URI.equalIgnoreCase(in).or(S_DOC.equalIgnoreCase(baseDoc));

            // If sofaId not provided, pick the first available for that doc/uri
            String sofaId = wantedSofaId;
            if (sofaId == null || sofaId.isBlank()) {
                sofaId = dsl.select(S_SOFAID)
                        .from(SOFAS)
                        .where(byUriOrDoc)
                        .orderBy(S_SOFAID.asc())
                        .limit(1)
                        .fetchOne(S_SOFAID);
                if (sofaId == null) {
                    throw new IllegalArgumentException("No SOFA found for identifier: " + in + " (tried doc_id=" + baseDoc + ")");
                }
            }

            // Load the row and prefer filtering by DOC_ID + SOFA_ID (works even if URI is null)
            Record r = dsl.select(S_DOC, S_SOFAID, S_URI, S_STR)
                    .from(SOFAS)
                    .where(byUriOrDoc.and(S_SOFAID.eq(sofaId)))
                    .orderBy(S_SOFAID.asc())
                    .limit(1)
                    .fetchOne();

            if (r == null) {
                throw new IllegalArgumentException("SOFA row not found for identifier=" + in + " and sofa_id=" + sofaId);
            }

            String docId      = r.get(S_DOC);
            String sofaString = r.get(S_STR);
            String sofaUri    = r.get(S_URI);

            if (sofaString == null) {
                throw new IllegalArgumentException("SOFA string is NULL for doc_id=" + docId + ", sofa_id=" + sofaId);
            }

            // For "file" we return URI if present, else DOC_ID (so downstream labels remain meaningful)
            String resolvedFileLabel = (sofaUri != null && !sofaUri.isBlank()) ? sofaUri : docId;

            return new String[]{ resolvedFileLabel, sofaId, sofaString };
        }
    }
}
