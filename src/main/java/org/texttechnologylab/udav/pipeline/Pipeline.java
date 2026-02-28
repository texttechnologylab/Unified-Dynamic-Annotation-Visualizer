package org.texttechnologylab.udav.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.generators.*;
import org.texttechnologylab.udav.generators.common_properties.CommonProperties;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.*;
import org.texttechnologylab.udav.sources.DBAccess;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
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
        this.visualizedGenerators = findGeneratorsUsedByVisualizations();
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
            throw new IllegalArgumentException("Invalid pipeline JSON.");
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
        if (dbAccess.getDataSource() == null) throw new IllegalArgumentException("dataSource must not be null");
        if (pipelineId == null || pipelineId.isBlank()) throw new IllegalArgumentException("pipelineId must not be null/blank");

        final String json;
        try (Connection c = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(c);

            // qualify everything with public
            Table<?> T = DSL.table(DSL.name("public", "pipeline"));
            Field<String> F_JSON = DSL.field(DSL.name("public", "pipeline", "json"), String.class);
            Field<String> F_ID = DSL.field(DSL.name("public", "pipeline", "pipeline_id"), String.class);

            String val = dsl.select(F_JSON)
                    .from(T)
                    .where(F_ID.eq(pipelineId))
                    .fetchOne(F_JSON);

            if (val == null) {
                throw new IllegalArgumentException("No pipeline found with id \"" + pipelineId + "\".");
            }
            json = val;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load pipeline \"" + pipelineId + "\" from DB.", e);
        }

        // 2) Parse JSON and normalize to the same structure as fromJSON(...)
        try {
            ObjectMapper mapper = new ObjectMapper();

            // Accept both a single object or a { "pipelines": [...] } envelope
            Map<String, Object> root;
            Object parsed = mapper.readValue(json, new TypeReference<Object>() {
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

            Object first = pipelines.get(0);
            if (!(first instanceof Map<?, ?> pipelineMap)) {
                throw new IllegalArgumentException("Invalid pipeline JSON: pipeline entry is not an object.");
            }

            JSONView view = new JSONView(pipelineMap);
            Pipeline pipeline = generatePipelineFromJSONView(view, dbAccess);

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
                    if (sourceDefinition != null && sourceDefinition.contains("@")) continue; // TODO: Remove
                    Source sourceObj = (sourceDefinition == null)? null : decideSourceFromJSONDefinition(sourceDefinition, dbAccess);

                    GeneratorSettings settingsBundle = GeneratorSettings.fromConfig(sourcesEntry);
                    JSONView generatorsView = sourcesEntry.get("createsGenerators");

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

                        Generator generator = constructGenerator(generatorID, generatorType, generatorEntry, sourcesEntry, settingsBundle, dbAccess);

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
                            if (sourceObj == null) throw new IllegalArgumentException("Error for generator \"" + generatorID + "\": Non-derived generators need a source, which is not defined in group with id \"" + sourceID + "\".");
                            generator.setSource(sourceObj);
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
            throw new IllegalArgumentException("Invalid pipeline JSON.");
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
        for (Generator g : visualizedGenerators.values()) g.writeToDB();
        currentState = PipelineState.SAVED_GENERATORS_TO_DB;
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
        if (definition.trim().toUpperCase().endsWith(".JSON")) {
            return new SourceJson(definition);
        } else if (definition.trim().toUpperCase().endsWith(".JSON@N")) {
            return new SourceJsonN(definition);
        }
        return new SourceUIMA(definition, dbAccess);
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

    private static Generator constructGenerator(String id, String className, JSONView config, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (className.contains(".")) {
            throw new IllegalArgumentException("Class name can't contain \".\".");
        }
        Class<?> generatorClass = Class.forName(Generator.GENERATORS_PACKAGE_PATH + "." + className);
        return (Generator) generatorClass.getDeclaredConstructor(String.class, JSONView.class, JSONView.class, GeneratorSettings.class, DBAccess.class)
                .newInstance(id, config, configBundle, settingsBundle, dbAccess);
    }

    private static String[] keysOnlyInA(Map<String, ?> mapA, Map<String, ?> mapB) {
        List<String> uniqueKeys = new ArrayList<>();
        for (String key : mapA.keySet()) {
            if (!mapB.containsKey(key)) { uniqueKeys.add(key); }
        }
        return uniqueKeys.toArray(new String[0]);
    }
}
