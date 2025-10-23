package org.texttechnologylab.udav.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.generators.CategoryNumberColorMapping;
import org.texttechnologylab.udav.generators.CategoryNumberMapping;
import org.texttechnologylab.udav.generators.Generator;
import org.texttechnologylab.udav.generators.TextFormatting;
import org.texttechnologylab.udav.sources.DBAccess;
import org.texttechnologylab.udav.sources.Source;

import javax.sql.DataSource;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

@Getter
public class Pipeline {

    private final Map<String, PipelineNode> visualizations;
    private final Map<String, PipelineNode> generators;
    private final Map<String, PipelineNode> sources;
    private final List<PipelineNode> customTypes;
    private final Map<String, PipelineNode> filteredGenerators;
    private final Map<String, PipelineNode> filteredSources;

    private final String id;
    private final JSONView rootJSONView;


    private Pipeline(String id, Map<String, PipelineNode> visualizations, Map<String, PipelineNode> generators, Map<String, PipelineNode> sources, List<PipelineNode> customTypes, JSONView rootJSONView) {
        this.id = id;
        this.visualizations = visualizations;
        this.generators = generators;
        this.sources = sources;
        this.customTypes = customTypes;
        this.rootJSONView = rootJSONView;

        System.out.println("Filtering irrelevant pipeline nodes...");
        HashMap<String, PipelineNode> filteredSources = new HashMap<>();
        HashMap<String, PipelineNode> filteredGenerators = new HashMap<>();
        for (PipelineNode v : visualizations.values()) {
            filterPipeline(v, filteredSources, filteredGenerators);
        }
        System.out.println("Non-relevant Sources:");
        System.out.println(Arrays.toString(keysOnlyInA(sources, filteredSources)));
        System.out.println("Non-relevant Generators:");
        System.out.println(Arrays.toString(keysOnlyInA(generators, filteredGenerators)));

        this.filteredGenerators = filteredGenerators;
        this.filteredSources = filteredSources;
    }

    public static Pipeline fromJSON(String path) throws IOException {
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
            return generatePipelineFromJSONView(view);
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
    public static Pipeline fromDB(DataSource dataSource, String pipelineId) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource must not be null");
        if (pipelineId == null || pipelineId.isBlank()) throw new IllegalArgumentException("pipelineId must not be null/blank");

        final String json;
        try (Connection c = dataSource.getConnection()) {
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
            Pipeline pipeline = generatePipelineFromJSONView(view);

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

    private static ArrayList<?> generateMapFromJSON(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = Pipeline.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            Map<String, Object> data = mapper.readValue(in, new TypeReference<>() {
            });
            if (!(data.get("pipelines") instanceof ArrayList<?> pipelines)) {
                throw new IllegalArgumentException("Invalid pipeline JSON.");
            }
            return pipelines;
        }
    }

    private static Pipeline generatePipelineFromJSONView(JSONView pipelineView) throws IllegalArgumentException {
        try {
            // Step 1: Generate all pipeline nodes from JSON
            String id = pipelineView.get("id").toString();
            JSONView sourcesView = pipelineView.get("sources");
            HashMap<String, PipelineNode> sources = new HashMap<>();
            HashMap<String, PipelineNode> generators = new HashMap<>();

            // Source-intrinsic generators
            for (JSONView sourcesEntry : sourcesView) {
                PipelineNode current = new PipelineNode(PipelineNodeType.SOURCE, Map.of(), sourcesEntry);
                String sourceID = sourcesEntry.get("id").toString();
                sources.put(sourceID, current);
                JSONView createsGenerators = sourcesEntry.get("createsGenerators");
                for (JSONView generatorEntry : createsGenerators) {
                    if (generatorEntry.get("type").toString().equals("combi")) {
                        JSONView createsSubGenerators = generatorEntry.get("createsGenerators");
                        for (JSONView subGeneratorEntry : createsSubGenerators) {
                            HashMap<String, PipelineNode> generatorDependencies = new HashMap<>();
                            generatorDependencies.put(sourceID, current);
                            PipelineNode generator = new PipelineNode(PipelineNodeType.GENERATOR, generatorDependencies, subGeneratorEntry);
                            generators.put(subGeneratorEntry.get("id").toString(), generator);
                        }
                    } else {
                        HashMap<String, PipelineNode> generatorDependencies = new HashMap<>();
                        generatorDependencies.put(sourceID, current);
                        PipelineNode generator = new PipelineNode(PipelineNodeType.GENERATOR, generatorDependencies, generatorEntry);
                        generators.put(generatorEntry.get("id").toString(), generator);
                    }
                }
            }

            // Derived generators
            JSONView derivedGeneratorsView = pipelineView.get("derivedGenerators");
            for (JSONView derivedGeneratorEntry : derivedGeneratorsView) {
                HashMap<String, PipelineNode> dependencies = new HashMap<>();
                String generatorID = derivedGeneratorEntry.get("id").toString();
                JSONView dependenciesGenerators = derivedGeneratorEntry.get("fromGenerators");
                for (JSONView dependencyEntry : dependenciesGenerators) {
                    String dependencyID = dependencyEntry.get("id").toString();
                    PipelineNode dependency = generators.get(dependencyID);
                    if (dependency == null) {
                        throw new IllegalArgumentException("Unknown dependency generator \"" + dependencyID + "\" for derived generator \"" + generatorID + "\". Generators used for a derived generator must be defined before the derived generator in the pipeline file.");
                    }
                    dependencies.put(dependencyID, dependency);
                }
                PipelineNode current = new PipelineNode(PipelineNodeType.DERIVED_GENERATOR, dependencies, derivedGeneratorEntry);
                generators.put(generatorID, current);
            }

            // Visualizations
            JSONView visualizationsView = pipelineView.get("widgets");
            HashMap<String, PipelineNode> visualizations = (HashMap<String, PipelineNode>) generatePipelineVisualizationsFromJSONView(visualizationsView, generators);

            // Step 2: Generate customTypes if defined
            List<PipelineNode> customTypes = generatePipelineCustomTypesFromJSONView(pipelineView);

            return new Pipeline(id, visualizations, generators, sources, customTypes, pipelineView);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline JSON.");
        }
    }

    private static List<PipelineNode> generatePipelineCustomTypesFromJSONView(JSONView pipelineView) {
        ArrayList<PipelineNode> customTypes = new ArrayList<>();
        try {
            JSONView customTypesView = pipelineView.get("customTypes");
            for (JSONView customTypeEntry : customTypesView) {
                // TODO: integrate customTypes into pipeline with dependencies and filter non-used types. Make it a map like the other PipelineNode collections.
                PipelineNode current = new PipelineNode(PipelineNodeType.CUSTOM_TYPE, new HashMap<>(), customTypeEntry);
                customTypes.add(current);
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return customTypes;
    }

    private static Map<String, PipelineNode> generatePipelineVisualizationsFromJSONView(JSONView visualizationsView, Map<String, PipelineNode> generators) {
        if (!visualizationsView.isList()) {
            throw new IllegalArgumentException("Invalid pipeline JSON: \"visualizations\" must be a list.");
        }
        HashMap<String, PipelineNode> visualizations = new HashMap<>();
        try {
            for (JSONView visualizationEntry : visualizationsView) {
                HashMap<String, PipelineNode> dependencies;
                String visualizationID = visualizationEntry.get("id").toString();
                if (visualizationEntry.get("type").toString().equals("combi")) {
                    dependencies = (HashMap<String, PipelineNode>) generatePipelineVisualizationsFromJSONView(visualizationEntry.get("widgets"), generators);
                    visualizations.putAll(dependencies);
                } else {
                    dependencies = new HashMap<>();
                    if (!visualizationEntry.hasNonNull("generator")) {
                        System.out.println("Visualization \"" + visualizationID + "\" has no generator defined. Skipping.");
                        continue;
                    }
                    String generatorID = visualizationEntry.get("generator").get("id").toString();
                    PipelineNode dependency = generators.get(generatorID);
                    if (dependency == null) {
                        throw new IllegalArgumentException("Unknown dependency generator \"" + generatorID + "\" for visualization \"" + visualizationID + "\".");
                    }
                    dependencies.put(generatorID, dependency);
                }
                PipelineNode current = new PipelineNode(PipelineNodeType.VISUALIZATION, dependencies, visualizationEntry);
                visualizations.put(visualizationID, current);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pipeline JSON.");
        }
        return visualizations;
    }

    private static void filterPipeline(PipelineNode current, Map<String, PipelineNode> filteredSources, Map<String, PipelineNode> filteredGenerators) {
        if (current.getType() == PipelineNodeType.SOURCE) {
            filteredSources.put(current.getConfig().get("id").toString(), current);
        } else if (current.getType() == PipelineNodeType.GENERATOR || current.getType() == PipelineNodeType.DERIVED_GENERATOR) {
            filteredGenerators.put(current.getConfig().get("id").toString(), current);
        }
        for (PipelineNode dependency : current.getDependencies().values()) {
            filterPipeline(dependency, filteredSources, filteredGenerators);
        }
    }

    private static String[] keysOnlyInA(Map<String, ?> mapA, Map<String, ?> mapB) {
        List<String> uniqueKeys = new ArrayList<>();
        for (String key : mapA.keySet()) {
            if (!mapB.containsKey(key)) {
                uniqueKeys.add(key);
            }
        }
        return uniqueKeys.toArray(new String[0]);
    }

    public Collection<Generator> generateGenerators(DBAccess dbAccess) throws SQLException {
        return generateGenerators(dbAccess, true, true);
    }

    public Collection<Generator> generateGenerators(DBAccess dbAccess, boolean onlyRelevantSources, boolean onlyRelevantGenerators) throws SQLException {
        Map<String, PipelineNode> sourceNodes = onlyRelevantSources ? filteredSources : sources;
        Map<String, PipelineNode> generatorNodes = onlyRelevantGenerators ? filteredGenerators : generators;
        HashMap<String, Generator> generatedGenerators = new HashMap<>();

        // Source-intrinsic generators
        for (PipelineNode sourceNode : sourceNodes.values()) {
            Source s = new Source(dbAccess, sourceNode.getConfig(), generatorNodes, sourceNode.getChildren());
            System.out.println("Source created: " + s.getConfig().get("id"));
            Map<String, Generator> sourceGenerators = s.createGenerators();
            generatedGenerators.putAll(sourceGenerators);
            System.out.println(sourceGenerators.size() + " Generators created for source " + s.getId());
        }

        // Derived generators
        for (PipelineNode generatorNode : generatorNodes.values()) {
            if (generatorNode.getType() != PipelineNodeType.DERIVED_GENERATOR) continue;
            String generatorID = generatorNode.getConfig().get("id").toString();
            HashMap<String, Generator> subGenerators = new HashMap<>();
            HashMap<String, JSONView> subGeneratorsConfig = new HashMap<>();
            HashMap<DerivedGeneratorSubtype, Integer> subtypesCounts = new HashMap<>();
            JSONView subNodes = generatorNode.getConfig().get("fromGenerators");
            for (JSONView node : subNodes) {
                String nodeID = node.get("id").toString();
                Generator subGenerator = generatedGenerators.get(nodeID);
                subGenerators.put(nodeID, subGenerator);
                subGeneratorsConfig.put(nodeID, node);
                if (subGenerator instanceof TextFormatting) {
                    subtypesCounts.merge(DerivedGeneratorSubtype.TEXT_FORMATTING, 1, Integer::sum);
                } else if (subGenerator instanceof CategoryNumberMapping) {
                    subtypesCounts.merge(DerivedGeneratorSubtype.CATEGORY_NUMBER, 1, Integer::sum);
                }
            }
            if (subtypesCounts.size() == 1) {
                // Single-subtype derived-generator
                if (subtypesCounts.containsKey(DerivedGeneratorSubtype.TEXT_FORMATTING)) {
                    ArrayList<TextFormatting.Dataset> datasets = new ArrayList<>();
                    HashSet<Integer> textLengths = new HashSet<>();
                    for (Generator s : subGenerators.values()) {
                        TextFormatting subTextFormatting = (TextFormatting) s;
                        textLengths.add(subTextFormatting.getText().length());
                        datasets.addAll(subTextFormatting.getDatasets());
                    }
                    if (textLengths.size() == 1) {
                        TextFormatting anySub = (TextFormatting) subGenerators.values().stream().findAny().orElse(null);
                        TextFormatting textFormatting = new TextFormatting(generatorID, null, null, anySub.getText(), datasets);
                        generatedGenerators.put(generatorID, textFormatting);
                    } else {
                        System.out.println("Didn't create generator \"" + generatorNode.getConfig().get("id") + "\". There was a text mismatch.");
                    }
                } else if (subtypesCounts.containsKey(DerivedGeneratorSubtype.CATEGORY_NUMBER)) {
                    Map<String, Map<String, Double>> categoryNumberMap = new HashMap<>();
                    Map<String, Color> categoryColorMap = new HashMap<>();
                    for (Map.Entry<String, Generator> entry : subGenerators.entrySet()) {
                        JSONView config = subGeneratorsConfig.get(entry.getKey());
                        CategoryNumberColorMapping subCategoryNumberMapping = (CategoryNumberColorMapping) entry.getValue();
                        int keepTop = -1;
                        try {
                            keepTop = Integer.parseInt(config.get("settings").get("keepTop").toString());
                        } catch (Exception ignored) {
                        }

                        if (keepTop == -1) {
                            // Todo: fertig machen, inklusive static color (einfach in color map machen?) und unnötige farben rausschmeißen => Zuerst CategoryNumber(Color)Mappings unifien
                        } else {
                            categoryNumberMap.putAll(CategoryNumberMapping.keepTotalTopN(subCategoryNumberMapping.getCategoryNumberMap(), keepTop));
                        }
                        categoryColorMap.putAll(subCategoryNumberMapping.getCategoryColorMap());
                        String capitalization = null;
                        try {
                            capitalization = config.get("settings").get("categoryCapitalization").toString();
                        } catch (Exception ignored) {
                        }
                        if (capitalization != null) { // Todo: Check for duplicates that exist now due to Lowercase/Uppercase (unify and print warning?)
                            categoryNumberMap = CategoryNumberMapping.capitalizeCategoryNumberKeys(categoryNumberMap, capitalization);
                            categoryColorMap = CategoryNumberMapping.capitalizeCategoryKeys(categoryColorMap, capitalization);
                        }
                    }

                    CategoryNumberColorMapping categoryNumberMapping = new CategoryNumberColorMapping(generatorID, categoryNumberMap, categoryColorMap);
                    generatedGenerators.put(generatorID, categoryNumberMapping);
                }
            } else if (subtypesCounts.size() > 1) {
                System.out.println("Didn't create generator \"" + generatorNode.getConfig().get("id") + "\". Sub-generators with different types not supported yet.");
            } else {
                System.out.println("Didn't create generator \"" + generatorNode.getConfig().get("id") + "\". This generator didn't have any sub-generators with accepted types.");
            }
        }

        return generatedGenerators.values();
    }
}
