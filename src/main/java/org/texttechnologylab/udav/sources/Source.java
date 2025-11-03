package org.texttechnologylab.udav.sources;

import lombok.Getter;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.database.TypeTableResolver;
import org.texttechnologylab.udav.generators.CategoryNumberColorMapping;
import org.texttechnologylab.udav.generators.CategoryNumberMapping;
import org.texttechnologylab.udav.generators.TextFormatting;
import org.texttechnologylab.udav.generators.*;
import org.texttechnologylab.udav.generators.Generator;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.pipeline.PipelineNode;
import org.texttechnologylab.udav.pipeline.PipelineNodeType;


import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


@Getter
public class Source implements SourceInterface {

    public static final String DEFAULT_TYPE_POS = "posvalue";
    public static final String DEFAULT_TYPE_LEMMA = "Lemma";


    private final JSONView config;
    private final Map<String, PipelineNode> relevantGenerators;
    private final Map<String, PipelineNode> generatorsToBuild;
    private final String id;
    private final String type;
    private final String annotationTypeName;
    private final Map<String, String> featureNames;
    private final Collection<String> sourceFiles;
    private final DBAccess dbAccess;



    public Source(DBAccess dbAccess, JSONView config, Map<String, PipelineNode> relevantGenerators, Map<String, PipelineNode> generatorsToBuild) throws SQLException {
        this.dbAccess = dbAccess;
        this.config = config;
        this.relevantGenerators = relevantGenerators;
        this.generatorsToBuild = generatorsToBuild;

        this.id = config.get("id").toString();

        // Set Type and Annotation Type Name (they are the same by default)
        this.type = config.get("type").toString();
        String annotationTypeName;
        try {
            annotationTypeName = config.get("settings").get("annotationTypeName").toString();
        } catch (Exception e) {
            annotationTypeName = this.type;
        }
        this.annotationTypeName = annotationTypeName;

        // Set default feature names
        this.featureNames = AnnotationFeatures.featureNames_default(new HashMap<>());

        // Replace default feature names with provided ones
        this.featureNames.putAll(configSourceGetOverriddenFeatureNames());

        // Set all source files that are used in this source
        this.sourceFiles = generateSourceFiles(config);
    }

    // Don't leave out filtered generators that are part of a combi with at least one relevant generator to keep visualization results consistent
    @Override
    public Map<String, Generator> createGenerators() throws SQLException {
        HashMap<String, Generator> generators = new HashMap<>();

        JSONView createsGenerators = config.get("createsGenerators");
        for (JSONView generatorEntry : createsGenerators) {
            String generatorType = generatorEntry.get("type").toString();
            if (generatorType.equals("combi")) {
                ArrayList<PipelineNode> subGeneratorNodes = new ArrayList<>();
                boolean combiNeeded = false;
                for (JSONView subGeneratorEntry : generatorEntry.get("createsGenerators")) {
                    if (relevantGenerators.containsKey(subGeneratorEntry.get("id").toString())) combiNeeded = true;
                    subGeneratorNodes.add(generatorsToBuild.get(subGeneratorEntry.get("id").toString()));
                }
                if (!combiNeeded) {
                    String combiName = "unnamed combi";
                    try { combiName = generatorEntry.get("id").toString(); } catch (Exception ignored) {}
                    System.out.println("Skipping irrelevant combi-generator \"" + combiName + "\".");
                    continue;
                }
                generators.putAll(createGeneratorsCombi(subGeneratorNodes, generatorEntry));
            } else if (generatorType.equals("bundle")) {
                for (JSONView subGeneratorEntry : generatorEntry.get("createsGenerators")) {
                    if (!relevantGenerators.containsKey(subGeneratorEntry.get("id").toString())) {
                        System.out.println("Skipping irrelevant bundle-part generator \"" + subGeneratorEntry.get("id") + "\".");
                        continue;
                    }
                    String generatorID = generatorEntry.get("id").toString();
                    PipelineNode generatorNode = generatorsToBuild.get(generatorID);
                    generators.put(generatorID, createGenerator(generatorNode, generatorEntry, subGeneratorEntry));
                }
            } else {
                if (!relevantGenerators.containsKey(generatorEntry.get("id").toString())) {
                    System.out.println("Skipping irrelevant generator \"" + generatorEntry.get("id") + "\".");
                    continue;
                }
                String generatorID = generatorEntry.get("id").toString();
                PipelineNode generatorNode = generatorsToBuild.get(generatorID);
                generators.put(generatorID, createGenerator(generatorNode, null, generatorEntry));
            }

        }

        return generators;
    }


    private Map<String, Generator> createGeneratorsCombi(Collection<PipelineNode> generators, JSONView configCombi) throws SQLException {
        // Step 1 - Find common traits for generators
        HashMap<String, Map<String, Color>> mapFeatureToCategoryColorMap = new HashMap<>();
        for (PipelineNode g : generators) {
            String generatorType = g.getConfig().get("type").toString();
            if (generatorType.equals("CategoryNumberColorMapping") || generatorType.equals("TextFormatting")) {
                mapFeatureToCategoryColorMap.put(generateFeatureNameCategory(configCombi, g.getConfig()), null);
            }
        }

        // Step 2 - Generate data for common traits
        Collection<String> combiSourceFiles = generateSourceFiles(configCombi, sourceFiles);
        Collection<String> combiCategoriesWhitelist = generateCategoriesWhitelist(configCombi, null);
        Collection<String> combiCategoriesBlacklist = generateCategoriesBlacklist(configCombi, null);
        for (String feature : mapFeatureToCategoryColorMap.keySet()) {
            mapFeatureToCategoryColorMap.put(feature, dbCreateCategoryColorMap(feature, combiSourceFiles, combiCategoriesWhitelist, combiCategoriesBlacklist));
        }

        // Step 3 - Create generators using the common data
        HashMap<String, Generator> combiGenerators = new HashMap<>();
        for (PipelineNode g : generators) {
            String generatorID = g.getConfig().get("id").toString();
            String generatorType = g.getConfig().get("type").toString();
            if (generatorType.equals("CategoryNumberColorMapping")) {
                String featureName = generateFeatureNameCategory(configCombi, g.getConfig());
                Collection<String> generatorSourceFiles = generateSourceFiles(g.getConfig(), combiSourceFiles);
                Collection<String> categoriesWhitelist = generateCategoriesWhitelist(configCombi, g.getConfig());
                Collection<String> categoriesBlacklist = generateCategoriesBlacklist(configCombi, g.getConfig());
                Map<String, Map<String, Double>> categoryNumberMap = dbCreateCategoryCountMap(featureName, generatorSourceFiles, categoriesWhitelist, categoriesBlacklist);
                Color singleColor = generateColor(configCombi, g.getConfig());
                if (singleColor == null) {
                    Map<String, Color> categoryColorMap = new HashMap<>(mapFeatureToCategoryColorMap.get(featureName));
                    categoryColorMap.keySet().retainAll(CategoryNumberMapping.calculateTotalFromCategoryCountMap(categoryNumberMap).keySet());
                    combiGenerators.put(generatorID, new CategoryNumberColorMapping(generatorID, categoryNumberMap, categoryColorMap));
                } else {
                    combiGenerators.put(generatorID, new CategoryNumberColorMapping(generatorID, categoryNumberMap, singleColor));
                }
            } else if (generatorType.equals("TextFormatting")) {
                String configSofaFile = generateBundleAttribute(configCombi, g.getConfig(), "sofaFile");
                String configSofaID = generateBundleAttribute(configCombi, g.getConfig(), "sofaID");
                String[] sofa = dbGetSofa(configSofaFile, configSofaID);
                String sofaFile = sofa[0];
                String sofaID = sofa[1];
                String sofaString = sofa[2];
                String featureName = generateFeatureNameCategory(configCombi, g.getConfig());
                String style = generateTextFormattingStyle(configCombi, g.getConfig());
                Color singleColor = generateColor(configCombi, g.getConfig());
                Map<String, Color> categoryColorMap;
                if (singleColor == null) {
                    categoryColorMap = mapFeatureToCategoryColorMap.get(featureName);
                } else {
                    categoryColorMap = mapFeatureToCategoryColorMap.get(featureName).keySet().stream().collect(Collectors.toMap(k -> k, k -> singleColor));
                }
                Collection<String> categoriesWhitelist = generateCategoriesWhitelist(configCombi, g.getConfig());
                Collection<String> categoriesBlacklist = generateCategoriesBlacklist(configCombi, g.getConfig());
                combiGenerators.put(generatorID, dbBuildTextFormatting(featureName, sofaFile, sofaID, categoriesWhitelist, categoriesBlacklist, categoryColorMap, generatorID, sofaString, style));
            } else { // Default case: Just treat the unknown bundle generator like a normal single generator.
                combiGenerators.put(generatorID, createGenerator(g, configCombi, g.getConfig()));
            }
        }

        return combiGenerators;
    }



    private Generator createGenerator(PipelineNode generator, JSONView configBundle, JSONView config) throws SQLException {
        String generatorID = generator.getConfig().get("id").toString();
        String generatorType = generator.getConfig().get("type").toString();
        Collection<String> sourceFiles = generateSourceFiles(configBundle, config);
        if (generatorType.equals("CategoryNumberMapping") || generatorType.equals("CategoryNumberColorMapping")) {
            Collection<String> categoriesWhitelist = generateCategoriesWhitelist(configBundle, config);
            Collection<String> categoriesBlacklist = generateCategoriesBlacklist(configBundle, config);
            Map<String, Map<String, Double>> categoryCountMap = dbCreateCategoryCountMap(generateFeatureNameCategory(configBundle, config), sourceFiles, categoriesWhitelist, categoriesBlacklist);
            Color singleColor = generateColor(configBundle, config);
            if (singleColor != null) {
                if (generatorType.equals("CategoryNumberColorMapping")) {
                    return new CategoryNumberColorMapping(generatorID, categoryCountMap, singleColor);
                } else {
                    return new CategoryNumberMapping(generatorID, categoryCountMap, singleColor);
                }
            } else if (generatorType.equals("CategoryNumberColorMapping")) {
                return new CategoryNumberColorMapping(generatorID, categoryCountMap);
            } else {
                return new CategoryNumberMapping(generatorID, categoryCountMap);
            }
        } else if (generatorType.equals("TextFormatting")) {
            String configSofaFile =  generateBundleAttribute(configBundle, config, "sofaFile");
            String configSofaID = generateBundleAttribute(configBundle, config, "sofaID");
            String[] sofa = dbGetSofa(configSofaFile);
            String sofaFile = sofa[0];
            String sofaID = sofa[1];
            String sofaString = sofa[2];

            String featureName = generateFeatureNameCategory(configBundle, config);
            String style = generateTextFormattingStyle(configBundle, config);
            Collection<String> categoriesWhitelist = generateCategoriesWhitelist(configBundle, config);
            Collection<String> categoriesBlacklist = generateCategoriesBlacklist(configBundle, config);
            Color singleColor = generateColor(configBundle, config);
            Map<String, Color> categoryColorMap = dbCreateCategoryColorMap(featureName, sourceFiles, categoriesWhitelist, categoriesBlacklist, singleColor);
            return dbBuildTextFormatting(featureName, sofaFile, sofaID, categoriesWhitelist, categoriesBlacklist, categoryColorMap, generatorID, sofaString, style);
        } else {
            throw new IllegalArgumentException("Unknown generator type: " + generator.getConfig().get("type") + " for source: " + id);
        }
    }

    private boolean isPosType() {
        if (this.type == null) return false;
        String t = this.type.trim();
        String tl = t.toLowerCase(Locale.ROOT);
        return t.equalsIgnoreCase("POS")
                || tl.endsWith(".lexmorph.type.pos.pos")
                || tl.contains(".lexmorph.type.pos.");
    }

    private String generateFeatureNameCategory(JSONView configBundle, JSONView config) {
        // explicit override wins
        String featureNameCategory = generateBundleAttribute(configBundle, config, "featureName");
        if (featureNameCategory != null && !featureNameCategory.isBlank()) {
            return featureNameCategory.trim();
        }

        // otherwise, choose sensible defaults per source type
        if (isPosType()) {
            // prefer coarse, then posValue, then value
            String v = featureNames.getOrDefault("coarseValue",
                    featureNames.getOrDefault("posValue",
                            featureNames.getOrDefault("value", "posValue")));
            return (v == null || v.isBlank()) ? "posValue" : v;
        } else {
            // NE, Lemma, etc.: most commonly "value"
            String v = featureNames.getOrDefault("value", "value");
            return (v == null || v.isBlank()) ? "value" : v;
        }
    }


    private String generateTextFormattingStyle(JSONView configBundle, JSONView config) {
        String textFormattingType = generateBundleAttribute(configBundle, config, "style");
        if (textFormattingType != null) return textFormattingType;
        return TextFormatting.DEFAULT_STYLE;
    }

    private Color generateColor(JSONView configBundle, JSONView config) {
        String colorStr = generateBundleAttribute(configBundle, config, "color");
        if (colorStr == null) return null;
        try { return Color.decode(colorStr);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private String generateBundleAttribute(JSONView configBundle, JSONView config, String attribute) {
        try { return config.get("settings").get(attribute).toString(); } catch (Exception ignored) {}
        try { return configBundle.get("settings").get(attribute).toString(); } catch (Exception ignored) {}
        return null;
    }

    private Collection<String> generateCategoriesWhitelist(JSONView configBundle, JSONView config) {
        Set<String> categoriesBundleWhitelist = configGetStringSet(configBundle, "categoriesWhitelist", false);
        Set<String> categoriesWhitelist = configGetStringSet(config, "categoriesWhitelist", false);

        if (categoriesWhitelist == null & categoriesBundleWhitelist == null) return null;
        if (categoriesWhitelist == null) return categoriesBundleWhitelist;
        if (categoriesBundleWhitelist == null) return categoriesWhitelist;
        categoriesWhitelist.retainAll(categoriesBundleWhitelist);
        return categoriesWhitelist;
    }
    private Collection<String> generateCategoriesBlacklist(JSONView configBundle, JSONView config) {
        Set<String> categoriesBundleBlacklist = configGetStringSet(configBundle, "categoriesBlacklist", false);
        Set<String> categoriesBlacklist = configGetStringSet(config, "categoriesBlacklist", false);

        if (categoriesBlacklist == null & categoriesBundleBlacklist == null) return null;
        if (categoriesBlacklist == null) return categoriesBundleBlacklist;
        if (categoriesBundleBlacklist == null) return categoriesBlacklist;
        categoriesBlacklist.addAll(categoriesBundleBlacklist);
        return categoriesBlacklist;
    }

    private Collection<String> generateSourceFiles(JSONView configBundle, JSONView config) {
        Collection<String> sourceFilesBundleWhitelist = generateSourceFiles(configBundle, sourceFiles);
        return generateSourceFiles(config, sourceFilesBundleWhitelist);
    }
    private Collection<String> generateSourceFiles(JSONView config, Collection<String> allSourceFiles) {
        Collection<String> sourceFilesWhitelist = configGetSourceFiles(config, "sourceFilesWhitelist");
        Collection<String> sourceFilesBlacklist = configGetSourceFiles(config, "sourceFilesBlacklist");

        if (sourceFilesWhitelist.isEmpty()) {
            // If no source files are provided, use all files
            sourceFilesWhitelist.addAll(allSourceFiles);
        } else if (!allSourceFiles.containsAll(sourceFilesWhitelist)) {
            System.out.println("Warning: Source file whitelist contains elements that are unknown or excluded on a higher level. Removing those elements.");
            sourceFilesWhitelist.retainAll(allSourceFiles);
        }
        // Remove blacklisted files from the whitelist
        sourceFilesWhitelist.removeAll(sourceFilesBlacklist);

        return sourceFilesWhitelist;
    }

    private Collection<String> generateSourceFiles(JSONView config) throws SQLException {
        return generateSourceFiles(config, dbGetAllSourceFiles());
    }


    private TextFormatting dbBuildTextFormatting(String featureName, String sofaFile, String sofaID, Collection<String> categoriesWhitelist, Collection<String> categoriesBlacklist, Map<String, Color> categoryColorMap, String generatorID, String text, String style) throws SQLException {
        ArrayList<TextFormatting.Dataset.Segment> segments = dbCreateTextFormattingSegments(featureName, sofaFile, sofaID, categoriesWhitelist, categoriesBlacklist);
        TextFormatting.Dataset ds = new TextFormatting.Dataset(featureName, style, categoryColorMap, segments);
        return new TextFormatting(generatorID, sofaFile, sofaID, text, new ArrayList<>(List.of(ds)));
    }

    private ArrayList<TextFormatting.Dataset.Segment> dbCreateTextFormattingSegments(String featureName,
                                                                                     String sofaFile,
                                                                                     String sofaId,
                                                                                     Collection<String> categoriesWhitelist,
                                                                                     Collection<String> categoriesBlacklist) throws SQLException {
        final String schema = "public";

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // Resolve per-type table
            TypeTableResolver resolver = new TypeTableResolver(dsl, schema);
            String hash = resolver.tableForType(this.annotationTypeName);
            if (hash == null) {
                throw new IllegalStateException("No table registered for UIMA type: " + this.annotationTypeName);
            }

            var T          = DSL.table(DSL.name(schema, hash));
            var DOC_ID     = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "doc_id")),   String.class);
            var SOFA_ID    = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "sofa_id")),  String.class);
            var FS_BEGIN   = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "fs_begin")), Integer.class);
            var FS_END     = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "fs_end")),   Integer.class);
            org.jooq.Field<String> F_CATEGORY = resolveFeatureField(dsl, schema, hash, featureName, null);

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
                cond = cond.and(F_CATEGORY.in(categoriesWhitelist));
            }
            if (categoriesBlacklist != null && !categoriesBlacklist.isEmpty()) {
                cond = cond.and(F_CATEGORY.notIn(categoriesBlacklist));
            }

            var recs = dsl
                    .select(FS_BEGIN, FS_END, F_CATEGORY)
                    .from(T)
                    .where(cond)
                    .orderBy(FS_BEGIN.asc(), FS_END.asc())
                    .fetch();

            ArrayList<TextFormatting.Dataset.Segment> segments = new ArrayList<>(recs.size());
            for (Record r : recs) {
                Integer b = r.get(FS_BEGIN);
                Integer e = r.get(FS_END);
                String cat = r.get(F_CATEGORY);
                if (cat == null || cat.isBlank()) cat = "(null)";
                if (b != null && e != null && b >= 0 && e >= b) {
                    segments.add(new TextFormatting.Dataset.Segment(b, e, cat));
                }
            }
            return segments;
        }
    }



    private Map<String, Color> dbCreateCategoryColorMap(String featureName, Collection<String> sourceFiles, Collection<String> categoriesWhitelist, Collection<String> categoriesBlacklist) throws SQLException {
        return dbCreateCategoryColorMap(featureName, sourceFiles, categoriesWhitelist, categoriesBlacklist, null);
    }

    private Map<String, Color> dbCreateCategoryColorMap(String featureName, Collection<String> sourceFiles, Collection<String> categoriesWhitelist, Collection<String> categoriesBlacklist, Color singleColor) throws SQLException {
        Map<String, Map<String, Double>> categoryCountMap = dbCreateCategoryCountMap(featureName, sourceFiles, categoriesWhitelist, categoriesBlacklist);
        Map<String, Double> totalCategories = CategoryNumberMapping.calculateTotalFromCategoryCountMap(categoryCountMap);
        if (singleColor == null) return CategoryNumberColorMapping.categoryColorMapFromCategoriesNumberMap(totalCategories);
        return totalCategories.keySet().stream().collect(Collectors.toMap(k -> k, k -> singleColor));
    }

    private Map<String, Map<String, Double>> dbCreateCategoryCountMap(String featureName,
                                                                      Collection<String> wantedSourceFiles,
                                                                      Collection<String> categoriesWhitelist,
                                                                      Collection<String> categoriesBlacklist) throws SQLException {
        final String schema = "public";

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // Resolve per-type table
            TypeTableResolver resolver = new TypeTableResolver(dsl, schema);
            String hash = resolver.tableForType(this.annotationTypeName);
            if (hash == null) {
                throw new IllegalStateException("No table registered for UIMA type: " + this.annotationTypeName);
            }

            var T          = DSL.table(DSL.name(schema, hash));
            var DOC_ID     = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "doc_id")),   String.class);
            var SOFA_ID    = DSL.field(DSL.name(schema, hash, resolver.sys(hash, "sofa_id")),  String.class);
            Field<String> F_CATEGORY = resolveFeatureField(dsl, schema, hash, featureName, null);


            var S       = DSL.table(DSL.name(schema, "sofas"));
            var S_DOC   = DSL.field(DSL.name(schema, "sofas", "doc_id"), String.class);
            var S_SOFA  = DSL.field(DSL.name(schema, "sofas", "sofa_id"), String.class);
            var S_URI   = DSL.field(DSL.name(schema, "sofas", "sofa_uri"), String.class);

            // Label = prefer URI, fallback to DOC_ID
            Field<String> LABEL = DSL.coalesce(S_URI, S_DOC);

            // Normalize input list to match LABEL (if ".xmi" -> strip)
            Collection<String> filesFilter = (wantedSourceFiles == null || wantedSourceFiles.isEmpty())
                    ? this.sourceFiles
                    : wantedSourceFiles;
            Set<String> normalized = new HashSet<>();
            for (String f : filesFilter) {
                if (f == null) continue;
                String s = f.trim();
                normalized.add(s);
                if (s.endsWith(".xmi")) normalized.add(s.substring(0, s.length() - 4)); // doc_id form
            }

            Condition cond = LABEL.in(normalized);
            if (categoriesWhitelist != null && !categoriesWhitelist.isEmpty()) {
                cond = cond.and(F_CATEGORY.in(categoriesWhitelist));
            }
            if (categoriesBlacklist != null && !categoriesBlacklist.isEmpty()) {
                cond = cond.and(F_CATEGORY.notIn(categoriesBlacklist));
            }

            var recs = dsl
                    .select(LABEL, F_CATEGORY, DSL.count())
                    .from(T)
                    .join(S).on(DOC_ID.eq(S_DOC).and(SOFA_ID.eq(S_SOFA)))
                    .where(cond)
                    .groupBy(LABEL, F_CATEGORY)
                    .fetch();

            Map<String, Map<String, Double>> out = new HashMap<>();
            for (Record r : recs) {
                String label = r.get(LABEL);
                String cat   = r.get(F_CATEGORY);
                if (cat == null || cat.isBlank()) cat = "(null)";
                Double cnt   = r.get(2, Integer.class).doubleValue();

                out.computeIfAbsent(label, k -> new HashMap<>())
                        .merge(cat, cnt, Double::sum);
            }
            return out;
        }
    }



    private String[] dbGetSofa(String wantedSofaFile, String wantedSofaId) throws SQLException {
        final String schema = "public"; // adjust if needed

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            var SOFAS    = DSL.table(DSL.name(schema, "sofas"));
            var S_DOC    = DSL.field(DSL.name(schema, "sofas", "doc_id"), String.class);
            var S_SOFAID = DSL.field(DSL.name(schema, "sofas", "sofa_id"), String.class);
            var S_URI    = DSL.field(DSL.name(schema, "sofas", "sofa_uri"), String.class);
            var S_STR    = DSL.field(DSL.name(schema, "sofas", "sofa_string"), String.class);

            // Normalize input: accept URI, doc_id, or "ID... .xmi"
            String in = (wantedSofaFile != null && !wantedSofaFile.isBlank())
                    ? wantedSofaFile.trim()
                    : this.sourceFiles.stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No source files available to resolve SOFA."));
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

    private String[] dbGetSofa(String wantedSofaFile) throws SQLException {
        return dbGetSofa(wantedSofaFile, "_InitialView");
    }



    private boolean containsOnlyGenerators(Collection<PipelineNode> nodes) {
        return nodes.stream().allMatch(node -> node.getType() == PipelineNodeType.GENERATOR);
    }

    private Map<String, String> configSourceGetOverriddenFeatureNames() {
        try {
            JSONView featureNames = config.get("featureNames");
            if (featureNames.isMap()) {
                Map<?, ?> map = featureNames.asMap();
                boolean allStrings = map.keySet().stream().allMatch(k -> k instanceof String)
                        && map.values().stream().allMatch(v -> v instanceof String);
                if (allStrings) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> stringMap = (Map<String, String>) map;
                    return stringMap;
                }
            }
            return new HashMap<>();
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private Set<String> configGetStringSet(JSONView config, String key, boolean returnEmptyIfNotConfigured) {
        try {
            JSONView sourceFiles = config.get("settings").get(key);
            if (sourceFiles.isList()) {
                List<?> list = sourceFiles.asList();
                boolean allStrings = list.stream().allMatch(item -> item instanceof String);
                if (allStrings) {
                    @SuppressWarnings("unchecked")
                    List<String> stringList = (List<String>) list;
                    return new HashSet<>(stringList);
                }
            }
            if (returnEmptyIfNotConfigured) {
                return new HashSet<>();
            }
            return null;
        } catch (Exception ignored) {
            if (returnEmptyIfNotConfigured) {
                return new HashSet<>();
            }
            return null;
        }
    }
    private Collection<String> configGetSourceFiles(JSONView config, String key) {
        return configGetStringSet(config, key, true);
    }

    private Set<String> dbGetAllSourceFiles() throws SQLException {
        return dbAccess.getSourceFiles();
    }

    // Lowercase + underscore sanitize like the writer does
    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    /** Return the feature field <schema>.<hash>.<hash>_f_<short> by trying candidates until one exists. */
    private org.jooq.Field<String> resolveFeatureField(org.jooq.DSLContext dsl,
                                                       String schema,
                                                       String tableHash,
                                                       String desiredShort,
                                                       java.util.List<String> extraCandidates) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();

        if (desiredShort != null && !desiredShort.isBlank()) candidates.add(desiredShort.trim());

        if (isPosType()) {
            // common POS feature short names
            candidates.add("coarseValue");
            candidates.add("posValue");
            candidates.add("value");
        } else {
            // NE / Lemma etc.
            candidates.add("value");
            candidates.add("identifier");
            candidates.add("label");
            candidates.add("lemmaValue");
        }

        if (extraCandidates != null) {
            for (String c : extraCandidates) if (c != null && !c.isBlank()) candidates.add(c.trim());
        }

        for (String shortName : candidates) {
            String physical = sanitize(tableHash + "_f_" + shortName);
            boolean exists = dsl.fetchExists(
                    DSL.selectOne()
                            .from(DSL.table(DSL.name("information_schema", "columns")))
                            .where(DSL.field(DSL.name("table_schema"), String.class).eq(schema))
                            .and(DSL.field(DSL.name("table_name"),  String.class).eq(tableHash))
                            .and(DSL.field(DSL.name("column_name"), String.class).eq(physical))
            );
            if (exists) {
                return DSL.field(DSL.name(schema, tableHash, physical), String.class);
            }
        }

        throw new IllegalStateException(
                "No matching feature column in " + schema + "." + tableHash +
                        " for desired '" + desiredShort + "'. Tried: " + candidates
        );
    }

}
