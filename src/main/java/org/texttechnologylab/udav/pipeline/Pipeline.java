package org.texttechnologylab.udav.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.generators.*;
import org.texttechnologylab.udav.generators.common_properties.CommonProperties;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.*;
import org.texttechnologylab.udav.sources.DBAccess;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

@Getter
public class Pipeline {

    private final String id;
    private final JSONView rootJSONView;
    private final Map<String, Generator> generators;
    private final Map<String, Generator> baseGenerators;
    private final Map<String, Generator> visualizedGenerators;
    private final DBAccess dbAccess;

    private PipelineState currentState;


    private Pipeline(String id, JSONView rootJSONView, HashMap<String, Generator> generators, HashMap<String, Generator> baseGenerators, DBAccess dbAccess) {
        this.id = id;
        this.rootJSONView = rootJSONView;
        this.generators = generators;
        this.baseGenerators = baseGenerators;
        // Persist/build every declared generator, not only widget-referenced ones.
        this.visualizedGenerators = new LinkedHashMap<>(generators);
        this.dbAccess = dbAccess;

        currentState = PipelineState.CREATED_GENERATORS;
    }

    public static Pipeline fromJSON(String path, DBAccess dbAccess) {
        try (InputStream in = Pipeline.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("File not found: " + path);
            }

            ObjectMapper mapper = new ObjectMapper();
            // Read the root and accept either:
            //  A) {"pipelines":[ { ... } ]}
            //  B) { ... }  // a single pipeline object
            JsonNode root = mapper.readTree(in);

            JsonNode pipelineNode;
            if (root.has("pipelines")) {                    // old format
                JsonNode arr = root.get("pipelines");
                if (!arr.isArray()) {
                    throw new IllegalArgumentException("Invalid pipeline JSON: \"pipelines\" must be an array.");
                }
                if (arr.size() != 1) {
                    String append = (arr.size() > 1)
                            ? "Multiple pipelines defined. If you want to read multiple pipelines, use function Pipeline.multipleFromJSON()."
                            : "No pipelines defined.";
                    throw new IllegalArgumentException("Invalid pipeline JSON: " + append);
                }
                pipelineNode = arr.get(0);
            } else {                                        // new format (single object)
                pipelineNode = root;
            }

            if (!pipelineNode.isObject()) {
                throw new IllegalArgumentException("Invalid pipeline JSON.");
            }

            Map<String, Object> pipelineMap = mapper.convertValue(pipelineNode, new TypeReference<>() {
            });
            JSONView view = new JSONView(pipelineMap);
            return generatePipelineFromJSONView(view, dbAccess);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Load a pipeline by id from the DB.
     * Expects table: pipeline(pipeline_id TEXT/VARCHAR PRIMARY KEY, json CLOB/TEXT).
     * JSON can be either:
     * (A) {"pipelines":[ { ...the pipeline... } ]}
     * (B) { ...the pipeline... }   // single object without the "pipelines" wrapper
     */
    public static Pipeline fromDB(DBAccess dbAccess, String pipelineId) {
        return fromDB(dbAccess, dbAccess, pipelineId);
    }

    /**
     * Load a pipeline row from {@code readAccess}'s schema, but build all generators
     * (and write their data) using {@code writeAccess}'s schema.
     * Use this when the pipeline table lives in a shared schema (app.db.schema) but each
     * pipeline's generator data lives in its own schema (the pipeline id).
     */
    public static Pipeline fromDB(DBAccess readAccess, DBAccess writeAccess, String pipelineId) {
        if (readAccess.getDataSource() == null) throw new IllegalArgumentException("dataSource must not be null");
        if (pipelineId == null || pipelineId.isBlank()) throw new IllegalArgumentException("pipelineId must not be null/blank");

        final String json;
        try (Connection c = readAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(c);

            String pipelineSchema = readAccess.getSchema();
            Table<?> T = DSL.table(DSL.name(pipelineSchema, "pipeline"));
            Field<String> F_JSON = DSL.field(DSL.name(pipelineSchema, "pipeline", "json"), String.class);
            Field<String> F_ID = DSL.field(DSL.name(pipelineSchema, "pipeline", "pipeline_id"), String.class);

            String val = dsl.select(F_JSON)
                    .from(T)
                    .where(F_ID.eq(pipelineId))
                    .fetchOne(F_JSON);

            if (val == null) {
                throw new IllegalArgumentException("No pipeline found with id \"" + pipelineId + "\".");
            }
            json = val;
            System.out.println("JSON:");
            System.out.println(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load pipeline \"" + pipelineId + "\" from DB.", e);
        }

        // 2) Parse JSON and normalize to the same structure as fromJSON(...)
        try {
            ObjectMapper mapper = new ObjectMapper();

            // Accept both a single object or a { "pipelines": [...] } envelope
            Map<String, Object> root;
            Object parsed = mapper.readValue(json, new TypeReference<>() {
            });
            if (parsed instanceof Map<?, ?> m) {
                //noinspection unchecked
                root = (Map<String, Object>) m;
            } else {
                throw new IllegalArgumentException("Invalid pipeline JSON: root must be an object.");
            }

            List<?> pipelines;
            Object maybePipelines = root.get("pipelines");

            if (maybePipelines instanceof List<?> list) {
                pipelines = list;
            } else {
                // treat the whole root as a single pipeline object
                pipelines = List.of(root);
            }

            if (pipelines.size() != 1) {
                String append = (pipelines.size() > 1)
                        ? "Multiple pipelines found in DB JSON. Store a single pipeline or use a selector."
                        : "No pipeline object found in DB JSON.";
                throw new IllegalArgumentException("Invalid pipeline JSON: " + append);
            }

            Object first = pipelines.getFirst();
            if (!(first instanceof Map<?, ?> pipelineMap)) {
                throw new IllegalArgumentException("Invalid pipeline JSON: pipeline entry is not an object.");
            }

            JSONView view = new JSONView(pipelineMap);
            System.out.println("Parsed JSONView from DB:");
            System.out.println(view.toJson(true));
            Pipeline pipeline = generatePipelineFromJSONView(view, writeAccess);

            // Sanity check: if the DB row was envelope-form with a different id, warn but continue
            String loadedId = pipeline.getId();
            if (!pipelineId.equals(loadedId)) {
                System.out.println("Warning: DB pipeline_name = \"" + pipelineId + "\" but JSON id = \"" + loadedId + "\".");
            }

            return pipeline;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline JSON loaded from DB.", e);
        }
    }

    public static Pipeline generatePipelineFromJSONView(JSONView pipelineView, DBAccess dbAccess) {
        try {
            Map<String, Object> merged = mergeGeneratorsIntoSources(pipelineView.asMap());
            Map<String, Object> expanded = expandNTemplates(merged, dbAccess);
            pipelineView = new JSONView(expanded);

            String id = getJSONViewString(pipelineView, "id");
            JSONView sourcesView = pipelineView.get("sources");
            HashMap<String, Generator> generators = new HashMap<>();
            HashMap<String, Generator> baseGenerators = new HashMap<>(); // Non-derived generators (they are not based on any other generator and therefore can be built first)
            HashMap<String, HashMap<String, Generator>> generatorsGroups = new HashMap<>();

            int generatorsTotalPreviousCount = 0;
            boolean acceptMissingDerivedGenerators = false;
            boolean failedToFindAllExtendsGenerators;
            String lastGeneratorMissingExtends = null;
            do {
                failedToFindAllExtendsGenerators = false;
                for (JSONView sourcesEntry : sourcesView) {
                    String sourceID = getJSONViewString(sourcesEntry, "id");
                    String sourceDefinition = getJSONViewOptionalString(sourcesEntry, "uri"); // TODO: Use better key name as this could also be a non uri source
                    Source sourceObj = (sourceDefinition == null)? null : decideSourceFromJSONDefinition(sourceDefinition, dbAccess);

                    GeneratorSettings settingsBundle = GeneratorSettings.fromConfig(sourcesEntry);
                    JSONView generatorsView = sourcesEntry.get("createsGenerators");
                    boolean requiresSubSources = false;
                    for (JSONView generatorEntry : generatorsView) {
                        if (getJSONViewOptionalString(generatorEntry, "__udavSubSourceId") != null) {
                            requiresSubSources = true;
                            break;
                        }
                    }
                    if (requiresSubSources && sourceObj != null && !(sourceObj instanceof SourceN)
                            && isDbJsonBackedSource(stripNSuffix(sourceDefinition))) {
                        sourceObj = new SourceJsonN(stripNSuffix(sourceDefinition), dbAccess);
                    }

                    generatorsLoop:
                    for (JSONView generatorEntry : generatorsView) {
                        String generatorID = generatorEntry.get("id").toString();
                        if (generators.containsKey(generatorID)) {
                            if (!acceptMissingDerivedGenerators && generatorID.equals(lastGeneratorMissingExtends)) acceptMissingDerivedGenerators = true;
                            continue;
                        }

                        String generatorType = generatorEntry.get("type").toString();
                        JSONView generatorExtends = null;
                        try { generatorExtends = generatorEntry.get("extends");
                        } catch (Exception ignored) {}
                        ArrayList<Generator> extendsGenerators = null;
                        if (generatorExtends != null && generatorExtends.isList() && !generatorExtends.asList().isEmpty()) {
                            extendsGenerators = new ArrayList<>();
                            for (JSONView jv : generatorExtends) {
                                Object o = jv.getNode();
                                if (!String.class.equals(o.getClass())) continue;

                                if (generators.containsKey(o)) { extendsGenerators.add(generators.get(o)); }
                                else if (acceptMissingDerivedGenerators) { lastGeneratorMissingExtends = generatorID; }
                                else { failedToFindAllExtendsGenerators = true; continue generatorsLoop; }
                            }
                        }

                        Generator generator = Generator.constructGenerator(generatorID, generatorType, generatorEntry, sourcesEntry, settingsBundle, dbAccess);

                        Source generatorSourceObj = sourceObj;
                        String subSourceId = getJSONViewOptionalString(generatorEntry, "__udavSubSourceId");
                        if (subSourceId != null) {
                            if (!(sourceObj instanceof SourceN sourceN)) {
                                throw new IllegalArgumentException("Error for generator \"" + generatorID + "\": source does not support grouped expansion.");
                            }
                            Source resolvedSubSource = sourceN.getSubSourcesIdToObjectMap().get(subSourceId);
                            if (resolvedSubSource == null) {
                                throw new IllegalArgumentException("Error for generator \"" + generatorID + "\": sub-source \"" + subSourceId + "\" not found.");
                            }
                            generatorSourceObj = resolvedSubSource;
                        }

                        if (extendsGenerators == null) {
                            GeneratorSettings combinedSettings = generator.getSettings();
                            if (!combinedSettings.getBooleanSettingOrDefault("ignoreCombiCommonProperties", false)) {
                                if (!generatorsGroups.containsKey(sourceID)) { generatorsGroups.put(sourceID, new HashMap<>()); }
                                generatorsGroups.get(sourceID).put(generatorID, generator);
                            }
                            baseGenerators.put(generatorID, generator);
                        } else {
                            if (extendsGenerators.isEmpty()) {
                                throw new IllegalArgumentException("Error for generator \"" + generatorID + "\": derivable generator doesn't have any valid generator sources.");
                            }
                            generator.setSource(new SourceDerived(extendsGenerators));
                        }

                        if (generator.getSource() == null) {
                            if (generatorSourceObj == null) throw new IllegalArgumentException("Error for generator \"" + generatorID + "\": Non-derived generators need a source, which is not defined in group with id \"" + sourceID + "\".");
                            generator.setSource(generatorSourceObj);
                        }

                        generators.put(generatorID, generator);
                        acceptMissingDerivedGenerators = false;
                    }
                }
                // Escape loop if no new (derived) generators were added
                if (generators.size() > generatorsTotalPreviousCount) {
                    generatorsTotalPreviousCount = generators.size();
                    acceptMissingDerivedGenerators = false;
                } else if (!acceptMissingDerivedGenerators) acceptMissingDerivedGenerators = true; else break;
            } while (failedToFindAllExtendsGenerators);

            // Common Generator Properties
            for (Map<String, Generator> group : generatorsGroups.values()) {
                HashMap<Class<? extends CommonProperties>, Set<Generator>> commonPropertiesClassToGenerators = new HashMap<>();
                for (Generator generator : group.values()) {
                    if (SourceDerived.class.equals(generator.getSource().getClass())) continue; // Derived generators don't use common attributes => Skip this one
                    Set<Class<? extends CommonProperties>> commonPropertyClasses = generator.preSetup_getAllCommonPropertyClasses();
                    for (Class<? extends CommonProperties> entry : commonPropertyClasses) {
                        Set<Generator> generatorSet;
                        if (commonPropertiesClassToGenerators.containsKey(entry)) {
                            generatorSet = commonPropertiesClassToGenerators.get(entry);
                        } else {
                            generatorSet = new HashSet<>();
                            commonPropertiesClassToGenerators.put(entry, generatorSet);
                        }
                        generatorSet.add(generator);
                    }
                }

                for (Map.Entry<Class<? extends CommonProperties>, Set<Generator>> entry : commonPropertiesClassToGenerators.entrySet()) {
                    Class<? extends CommonProperties> commonPropertiesClass = entry.getKey();
                    Set<Generator> generatorsCommon = entry.getValue();
                    if (generatorsCommon.size() < 2) continue; // We only have 1 generator with that CommonProperty => don't use it
                    CommonProperties commonProperties = commonPropertiesClass.getDeclaredConstructor().newInstance();
                    for (Generator g : generatorsCommon) g.preSetup_setCommonPropertiesObj(commonProperties);
                }
            }

            return new Pipeline(id, pipelineView, generators, baseGenerators, dbAccess);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline JSON: " + e.getMessage(), e);
        }
    }

    public void setupGenerators() throws SQLException {
        if (currentState != PipelineState.CREATED_GENERATORS) throw new IllegalStateException("Pipeline not in correct state to setup generators.");
        for (Generator g : baseGenerators.values()) g.setup_step1();
        for (Generator g : baseGenerators.values()) g.setup_step2();
        for (Generator g : baseGenerators.values()) g.setup_step3();
        HashSet<Generator> alreadySetup = new HashSet<>();
        for (Generator g : visualizedGenerators.values()) { setupDependencyGenerators(g, alreadySetup); }
        currentState = PipelineState.SETUP_GENERATORS;
    }

    public void saveGeneratorsToDB() throws SQLException {
        if (currentState != PipelineState.SETUP_GENERATORS) throw new IllegalStateException("Pipeline not in correct state for saving generators to database.");

        ensureGeneratorTypeTableExists();

        for (Generator g : visualizedGenerators.values()) {
            try {
                g.writeToDB();
                replaceGeneratorTypeRow(g.getId(), g.getClass().getSimpleName());
            } catch (SQLException e) {
                throw new SQLException(
                        "Failed to persist generator \"" + g.getId() + "\" ("
                                + g.getClass().getName() + ") to database.",
                        e
                );
            }
        }

        currentState = PipelineState.SAVED_GENERATORS_TO_DB;
    }

    private void ensureGeneratorTypeTableExists() throws SQLException {
        final String schema = dbAccess.getSchema();
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);
            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORTYPE))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORTYPE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();
        }
    }

    private void replaceGeneratorTypeRow(String generatorId, String generatorType) throws SQLException {
        final String schema = dbAccess.getSchema();

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            Table<?> table = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORTYPE));
            Field<String> fieldGeneratorId = DSL.field(
                    DSL.name(schema, DBConstants.TABLENAME_GENERATORTYPE, DBConstants.TABLEATTR_GENERATORID),
                    String.class
            );
            Field<String> fieldGeneratorType = DSL.field(
                    DSL.name(schema, DBConstants.TABLENAME_GENERATORTYPE, DBConstants.TABLEATTR_GENERATORTYPE),
                    String.class
            );

            // Keep one authoritative row per generator id even if the table already contains duplicates.
            dsl.deleteFrom(table)
                    .where(fieldGeneratorId.eq(generatorId))
                    .execute();

            dsl.insertInto(table)
                    .columns(fieldGeneratorId, fieldGeneratorType)
                    .values(generatorId, generatorType)
                    .execute();
        }
    }

    public void saveToDB() throws SQLException {
        if (currentState != PipelineState.CREATED_GENERATORS) throw new IllegalStateException("Pipeline not in correct state for saving it to database.");
        setupGenerators();
        saveGeneratorsToDB();
    }


    public enum PipelineState { CREATED_GENERATORS, SETUP_GENERATORS, SAVED_GENERATORS_TO_DB }




    // --- Only private helper functions from here ---

    private void setupDependencyGenerators(Generator generator, Set<Generator> alreadySetup) throws SQLException {
        if (!SourceDerived.class.equals(generator.getSource().getClass())) return; // Arrived at Non-Derived generator, they have already been set up.
        if (alreadySetup.contains(generator)) return; // This generator has already been set up by this function
        alreadySetup.add(generator);
        SourceDerived derivedSource = (SourceDerived) generator.getSource();
        for (Generator g : derivedSource.getSourceGenerators()) { setupDependencyGenerators(g, alreadySetup); }
        generator.setup_step1(); generator.setup_step2(); generator.setup_step3();
    }

    private Map<String, Generator> findGeneratorsUsedByVisualizations() {
        HashMap<String, Generator> visualizedGenerators = new HashMap<>();
        JSONView widgetsView = rootJSONView.get("widgets");
        for (JSONView widgetView : widgetsView) {
            String widgetID = widgetView.get("id").toString();
            if (widgetID == null) { widgetID = "undefined"; }
            String generatorID = null;
            try { generatorID = widgetView.get("generator").get("id").toString(); }
            catch (Exception ignored) {}
            if (generatorID == null) { continue; }
            Generator generator = generators.get(generatorID);
            if (generator == null) { throw new IllegalArgumentException("There is no generator with ID \"" + generatorID + "\" to create widget \"" + widgetID +"\"."); }
            visualizedGenerators.put(generatorID, generator);
        }
        return visualizedGenerators;
    }

    private static Source decideSourceFromJSONDefinition(String definition, DBAccess dbAccess) throws SQLException, IOException {
        String normalizedDefinition = stripNSuffix(definition);
        if (isDbJsonBackedSource(normalizedDefinition)) {
            return new SourceJson(normalizedDefinition, dbAccess);
        }
        return new SourceUIMA(normalizedDefinition, dbAccess);
    }

    public static String stripNSuffix(String value) {
        if (value == null) return null;
        return value.trim();
    }

    private static String getJSONViewOptionalString(JSONView view, String name) {
        String outputString = null;
        try { outputString = getJSONViewString(view, name); }
        catch (Exception ignored) {}
        if ("".equals(outputString)) outputString = null;
        return outputString;
    }

    private static String getJSONViewString(JSONView view, String name) {
        return view.get(name).toString().trim();
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeGeneratorsIntoSources(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>(input);

        // Deep-copy sources into a new list so we don't mutate the original
        List<Map<String, Object>> originalSources = (List<Map<String, Object>>) input.get("sources");
        List<Map<String, Object>> newSources = new ArrayList<>();

        // Build a map of sourceId -> createsGenerators list for quick lookup
        Map<String, List<Map<String, Object>>> sourceGeneratorMap = new LinkedHashMap<>();
        // Track generator IDs already present per source to keep merge idempotent.
        Map<String, Set<String>> sourceGeneratorIds = new LinkedHashMap<>();

        for (Map<String, Object> source : originalSources) {
            Map<String, Object> newSource = new LinkedHashMap<>(source);
            String sourceId = stripNSuffix((String) source.get("id"));

            // Ensure createsGenerators exists; copy existing ones if present
            List<Map<String, Object>> existingGenerators =
                    (List<Map<String, Object>>) source.getOrDefault("createsGenerators", new ArrayList<>());
            List<Map<String, Object>> generatorList = new ArrayList<>(existingGenerators);

            Set<String> existingIds = new LinkedHashSet<>();
            for (Map<String, Object> existingGenerator : existingGenerators) {
                Object existingId = existingGenerator.get("id");
                if (existingId != null) {
                    String id = existingId.toString().trim();
                    if (!id.isEmpty()) {
                        existingIds.add(id);
                    }
                }
            }

            newSource.put("createsGenerators", generatorList);
            sourceGeneratorMap.put(sourceId, generatorList);
            sourceGeneratorIds.put(sourceId, existingIds);
            newSources.add(newSource);
        }

        // Iterate over standalone generators and merge them into the matching source
        List<Map<String, Object>> standaloneGenerators =
                (List<Map<String, Object>>) input.getOrDefault("generators", new ArrayList<>());

        for (Map<String, Object> generator : standaloneGenerators) {
            String sourceId = stripNSuffix((String) generator.get("source"));

            List<Map<String, Object>> targetList = sourceGeneratorMap.get(sourceId);
            if (targetList == null) {
                throw new IllegalArgumentException(
                        "Generator references unknown source id: " + sourceId);
            }

            Object generatorIdObj = generator.get("id");
            String generatorId = generatorIdObj == null ? null : generatorIdObj.toString().trim();
            if (generatorId != null && !generatorId.isEmpty()) {
                Set<String> seenIds = sourceGeneratorIds.get(sourceId);
                if (seenIds.contains(generatorId)) {
                    continue;
                }
                seenIds.add(generatorId);
            }

            // Copy the generator without the "source" key, as it's now implied by nesting
            Map<String, Object> strippedGenerator = new LinkedHashMap<>(generator);
            strippedGenerator.remove("source");
            targetList.add(strippedGenerator);
        }

        // Build the result: same top-level structure but with updated sources and no "generators"
        result.put("sources", newSources);
        result.remove("generators");

        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> expandNTemplates(Map<String, Object> input, DBAccess dbAccess) throws SQLException, IOException {
        Map<String, Object> result = new LinkedHashMap<>(input);
        List<Map<String, Object>> originalSources = (List<Map<String, Object>>) input.get("sources");
        if (originalSources == null) {
            return result;
        }

        List<Map<String, Object>> expandedSources = new ArrayList<>();
        Set<String> globalGeneratorIds = new LinkedHashSet<>();

        for (Map<String, Object> source : originalSources) {
            Map<String, Object> newSource = new LinkedHashMap<>(source);
            String sourceDefinition = (String) source.get("uri");
            Source sourceObj = sourceDefinition == null ? null : decideSourceFromJSONDefinition(sourceDefinition, dbAccess);

            List<Map<String, Object>> originalGenerators =
                    (List<Map<String, Object>>) source.getOrDefault("createsGenerators", new ArrayList<>());
            List<Map<String, Object>> expandedGenerators = new ArrayList<>();

            for (Map<String, Object> generator : originalGenerators) {
                boolean generatorGroup = booleanOrDefault(generator.get("generatorGroup"), false);

                if (!generatorGroup) {
                    Map<String, Object> copy = new LinkedHashMap<>(generator);
                    String existingId = stringOrNull(copy.get("id"));
                    if (existingId != null && !existingId.isBlank()) {
                        globalGeneratorIds.add(existingId.trim());
                    }
                    expandedGenerators.add(copy);
                    continue;
                }

                SourceN sourceN;
                if (sourceObj instanceof SourceN sN) {
                    sourceN = sN;
                } else if (sourceDefinition != null && isDbJsonBackedSource(stripNSuffix(sourceDefinition))) {
                    sourceObj = new SourceJsonN(stripNSuffix(sourceDefinition), dbAccess);
                    sourceN = (SourceN) sourceObj;
                } else {
                    String generatorId = stringOrNull(generator.get("id"));
                    throw new IllegalArgumentException("Generator \"" + generatorId + "\" is grouped but source does not support grouped expansion.");
                }

                String normalizedType = stringOrNull(generator.get("type"));
                String idTemplate = stringOrNull(generator.get("id"));

                int fallbackIndex = 0;
                for (Map.Entry<String, Source> subSourceEntry : sourceN.getSubSourcesIdToObjectMap().entrySet()) {
                    String subSourceId = subSourceEntry.getKey();
                    String resolvedId = resolveExpandedGeneratorId(idTemplate, subSourceId, globalGeneratorIds, fallbackIndex);
                    while (globalGeneratorIds.contains(resolvedId)) {
                        fallbackIndex++;
                        resolvedId = resolveExpandedGeneratorId(idTemplate, Integer.toString(fallbackIndex), globalGeneratorIds, fallbackIndex);
                    }
                    globalGeneratorIds.add(resolvedId);
                    fallbackIndex++;

                    Map<String, Object> expandedGenerator = new LinkedHashMap<>(generator);
                    expandedGenerator.put("type", normalizedType);
                    expandedGenerator.put("id", resolvedId);
                    expandedGenerator.remove("source");
                    expandedGenerator.put("__udavSubSourceId", subSourceId);
                    expandedGenerators.add(expandedGenerator);
                }
            }

            newSource.put("createsGenerators", expandedGenerators);
            expandedSources.add(newSource);
        }

        result.put("sources", expandedSources);
        return result;
    }

    private static String resolveExpandedGeneratorId(String idTemplate, String subSourceId, Set<String> usedIds, int fallbackIndex) {
        String safeSubSourceId = (subSourceId == null || subSourceId.isBlank()) ? Integer.toString(fallbackIndex) : subSourceId;
        String baseId = (idTemplate == null || idTemplate.isBlank()) ? "Generator" : idTemplate.trim();
        String candidate = baseId.contains("@ID@") ? baseId.replace("@ID@", safeSubSourceId) : baseId + "_" + safeSubSourceId;
        if (!usedIds.contains(candidate)) {
            return candidate;
        }

        int suffix = 0;
        String withFallback;
        do {
            withFallback = baseId.contains("@ID@")
                    ? baseId.replace("@ID@", Integer.toString(suffix))
                    : baseId + "_" + suffix;
            suffix++;
        } while (usedIds.contains(withFallback));
        return withFallback;
    }

    private static String stringOrNull(Object value) {
        if (value == null) return null;
        String stringValue = value.toString().trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private static boolean booleanOrDefault(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        String text = value.toString().trim();
        if (text.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(text);
    }

    private static boolean isDbJsonBackedSource(String definition) {
        if (definition == null) return false;
        String normalized = definition.trim().toUpperCase();
        return normalized.endsWith(".JSON") || normalized.endsWith(".XML");
    }
}
