package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.api.DummyDataProvider;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartRegistry;
import org.texttechnologylab.udav.generators.sources.SourceJsonN;
import org.texttechnologylab.udav.pipeline.Pipeline;
import org.texttechnologylab.udav.sources.DBAccess;
import org.texttechnologylab.udav.widgets.Widget;
import org.texttechnologylab.udav.widgets.jsontocsv.JsonToCsvConverter;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DataService {

    private final ObjectMapper mapper;
    private final DummyDataProvider provider;
    private final ChartRegistry charts;
    private final PipelineService pipelineService;
    private final DataSource dataSource;

    private final FileService fileService;

    // Widgets read an empty file set as "no restriction", so a corpus filter that matches no
    // document has to pass a file name that no generator data carries. The value reaches the
    // database as a query parameter, so it must be valid text (PostgreSQL rejects NUL bytes).
    private static final String NO_DOCUMENT = "<no document matches the corpus filter>";

    public DataService(ObjectMapper mapper,
                       DummyDataProvider provider,
                       ChartRegistry charts,
                       PipelineService pipelineService,
                       DataSource dataSource,
                       FileService fileService) {
        this.mapper = mapper;
        this.provider = provider;
        this.charts = charts;
        this.pipelineService = pipelineService;
        this.dataSource = dataSource;
        this.fileService = fileService;
    }

    public String buildArrayJson(String id, String type,
                                 Map<String, String> filters,
                                 Map<String, String> corpus,
                                 boolean pretty,
                                 String schema) {
        JsonNode node = ensureDatasetList(renderNode(id, type, filters, corpus, schema));
        try {
            return pretty ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                    : mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "[]";
        }
    }

    public JsonNode buildDataJson(String pipelineId,
                                  String generatorId,
                                  String chartType,
                                  Integer page,
                                  Integer size,
                                  Boolean includeIds,
                                  Map<String, String> filters,
                                  Map<String, String> corpus) throws Exception {
        boolean isTemplate = isTemplateGeneratorId(generatorId);

        int requestedPage = page == null ? 0 : page;
        int requestedSize = Math.max(1, size == null ? 1 : size);

        GroupResolution group = isTemplate
                ? resolveGroupInternal(pipelineId, generatorId)
                : new GroupResolution(Collections.singletonList(generatorId));

        List<String> ids = group.ids().stream().filter(id -> id != null && !id.isBlank()).toList();
        ObjectNode out = mapper.createObjectNode();
        ObjectNode meta = out.putObject("meta");
        meta.put("total", ids.size());
        meta.put("pageSize", requestedSize);

        if (ids.isEmpty()) {
            meta.put("page", 0);
            meta.set("ids", mapper.createArrayNode());
            out.set("data", mapper.createArrayNode());
            return out;
        }

        int maxPage = (ids.size() - 1) / requestedSize;
        int clampedPage = Math.max(0, Math.min(requestedPage, maxPage));
        int from = clampedPage * requestedSize;
        int to = Math.min(ids.size(), from + requestedSize);
        List<String> pageIds = ids.subList(from, to);

        meta.put("page", clampedPage);

        ArrayNode idsNode = meta.putArray("ids");
        if (includeIds == null || includeIds) {
            for (String id : ids) {
                idsNode.add(id);
            }
        }

        if (requestedSize == 1) {
            out.set("data", ensureDatasetList(renderNode(pageIds.get(0), chartType, filters, corpus, pipelineId)));
            return out;
        }

        ArrayNode data = out.putArray("data");
        for (String id : pageIds) {
            ObjectNode itemNode = data.addObject();
            itemNode.set("data", ensureDatasetList(renderNode(id, chartType, filters, corpus, pipelineId)));
        }
        return out;
    }

    public byte[] buildGroupArchive(String pipelineId,
                                    String templateGeneratorId,
                                    String chartType,
                                    String format,
                                    Map<String, String> filters,
                                    Map<String, String> corpus) throws Exception {
        GroupResolution group = isTemplateGeneratorId(templateGeneratorId)
                ? resolveGroupInternal(pipelineId, templateGeneratorId)
                : new GroupResolution(Collections.singletonList(templateGeneratorId));
        String normalizedFormat = normalizeExportFormat(format);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(byteStream, StandardCharsets.UTF_8)) {
            int index = 0;
            for (String generatorId : group.ids()) {
                JsonNode data = renderNode(generatorId, chartType, filters, corpus, pipelineId);
                byte[] content = renderExportContent(normalizedFormat, chartType, data, filters, corpus);
                String entryName = String.format(
                        Locale.ROOT,
                        "%03d-%s.%s",
                        index,
                        sanitizeFilenamePart(generatorId),
                        normalizedFormat
                );

                ZipEntry entry = new ZipEntry(entryName);
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
                index++;
            }
        }

        return byteStream.toByteArray();
    }

    private JsonNode renderNode(String id, String type,
                                Map<String, String> filters,
                                Map<String, String> corpus,
                                String schema) {

        Set<String> files = corpusFiles(corpus);

        Map<String, String> mutableFilters = new LinkedHashMap<>(Optional.ofNullable(filters).orElseGet(Collections::emptyMap));
        ValueMode vm = ValueMode.from(mutableFilters.get("valueMode"));
        mutableFilters.remove("valueMode");

        // Prefer handler if present
        if (charts.has(type)) {
            return charts.get(type).render(id, mutableFilters, files, vm, schema);
        }

        // fallback to your legacy provider paths if no handler found
        try {
            return mapper.readTree(provider.getJsonFor(id, type));
        } catch (Exception ignored) {
            return mapper.createArrayNode();
        }
    }

    // The corpus filter: the selected files, narrowed by tags (collection ids) and import dates
    private Set<String> corpusFiles(Map<String, String> corpus) {
        if (corpus == null) return Collections.emptySet();
        Set<String> files = Parsing.parseCsvSet(corpus.get("files"));
        Set<String> tags = Parsing.parseCsvSet(corpus.get("tags"));
        LocalDate from = parseDate(corpus.get("dateFrom"));
        LocalDate to = parseDate(corpus.get("dateTo"));
        if (tags.isEmpty() && from == null && to == null) return files;

        Set<String> matching = fileService.documentIdsMatching(tags, from, to);
        if (!files.isEmpty()) matching.retainAll(files);
        return matching.isEmpty() ? Set.of(NO_DOCUMENT) : matching;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private GroupResolution resolveGroupInternal(String pipelineId, String templateGeneratorId) throws Exception {
        JsonNode pipeline = pipelineService.get(pipelineId);
        JsonNode sources = pipeline.path("sources");
        if (sources.isArray()) {
            for (JsonNode sourceNode : sources) {
                JsonNode creates = sourceNode.path("createsGenerators");
                if (!creates.isArray()) {
                    continue;
                }

                for (JsonNode generatorNode : creates) {
                    String generatorId = textOrNull(generatorNode.get("id"));
                    if (!Objects.equals(generatorId, templateGeneratorId)) {
                        continue;
                    }

                    if (!isGeneratorGroup(generatorNode)) {
                        return new GroupResolution(Collections.singletonList(templateGeneratorId));
                    }

                    List<String> subSourceIds = loadSubSourceIds(pipelineId, sourceNode);
                    return new GroupResolution(expandIds(templateGeneratorId, subSourceIds));
                }
            }
        }

        // Backward compatibility: support legacy top-level generators array.
        JsonNode topLevelGenerators = pipeline.path("generators");
        if (topLevelGenerators.isArray() && sources.isArray()) {
            for (JsonNode generatorNode : topLevelGenerators) {
                String generatorId = textOrNull(generatorNode.get("id"));
                if (!Objects.equals(generatorId, templateGeneratorId)) {
                    continue;
                }

                String rawSource = textOrNull(generatorNode.get("source"));
                if (!isGeneratorGroup(generatorNode)) {
                    return new GroupResolution(Collections.singletonList(templateGeneratorId));
                }

                String sourceRef = Pipeline.stripNSuffix(rawSource);
                JsonNode sourceNode = findSourceById(sources, sourceRef);
                if (sourceNode == null) {
                    return new GroupResolution(Collections.emptyList());
                }
                List<String> subSourceIds = loadSubSourceIds(pipelineId, sourceNode);
                return new GroupResolution(expandIds(templateGeneratorId, subSourceIds));
            }
        }

        return new GroupResolution(Collections.emptyList());
    }

    private byte[] renderExportContent(String format,
                                       String chartType,
                                       JsonNode data,
                                       Map<String, String> filters,
                                       Map<String, String> corpus) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("type", chartType);
            payload.set("data", data);
            ObjectNode meta = payload.putObject("meta");
            ObjectNode metadata = meta.putObject("metadata");
            if (corpus != null) {
                corpus.forEach((k, v) -> metadata.put(k, v));
            }
            if (filters != null) {
                filters.forEach((k, v) -> metadata.put(k, v));
            }

            if ("json".equals(format)) {
                ObjectNode out = mapper.createObjectNode();
                out.set("metadata", metadata.deepCopy());
                out.set("data", data);
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(out);
            }

            if ("csv".equals(format)) {
                String csv = Widget.tryConstructWidget(chartType)
                        .map(widget -> {
                            try {
                                return widget.toCsv(payload);
                            } catch (Exception ignored) {
                                return null;
                            }
                        })
                        .orElse(null);
                if (csv == null) {
                    JsonToCsvConverter converter = new JsonToCsvConverter(mapper);
                    csv = converter.convert(data);
                }
                return csv.getBytes(StandardCharsets.UTF_8);
            }

            if ("tex".equals(format)) {
                String tex = Widget.tryConstructWidget(chartType)
                        .map(widget -> {
                            try {
                                return widget.toTex(payload);
                            } catch (Exception ignored) {
                                return null;
                            }
                        })
                        .orElse(null);
                if (tex == null) {
                    tex = "% TEX export is not available for this widget without SVG source.\n";
                }
                return tex.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Fallback to empty payload if one dataset fails to serialize.
        }

        return new byte[0];
    }

    private static String normalizeExportFormat(String format) {
        String normalized = Optional.ofNullable(format).orElse("json").trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("json") || normalized.equals("csv") || normalized.equals("tex")) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported bulk export format: " + format);
    }

    private static String sanitizeFilenamePart(String input) {
        if (input == null || input.isBlank()) {
            return "dataset";
        }
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private JsonNode findSourceById(JsonNode sources, String sourceId) {
        if (!sources.isArray() || sourceId == null) {
            return null;
        }
        for (JsonNode sourceNode : sources) {
            String currentId = textOrNull(sourceNode.get("id"));
            if (Objects.equals(sourceId, currentId)) {
                return sourceNode;
            }
        }
        return null;
    }

    private List<String> loadSubSourceIds(String pipelineId, JsonNode sourceNode) {
        String uri = textOrNull(sourceNode.get("uri"));
        String normalized = Pipeline.stripNSuffix(uri);
        if (!isDbJsonBackedSource(normalized)) {
            return Collections.emptyList();
        }

        try {
            SourceJsonN sourceJsonN = new SourceJsonN(normalized, new DBAccess(dataSource, pipelineId));
            return new ArrayList<>(sourceJsonN.getSubSourcesIdToObjectMap().keySet());
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<String> expandIds(String idTemplate, List<String> subSourceIds) {
        List<String> ids = new ArrayList<>();
        Set<String> usedIds = new LinkedHashSet<>();
        int fallbackIndex = 0;

        for (String subSourceId : subSourceIds) {
            String safeSubSourceId = (subSourceId == null || subSourceId.isBlank())
                    ? Integer.toString(fallbackIndex)
                    : subSourceId;

            String candidate = idTemplate.contains("@ID@")
                    ? idTemplate.replace("@ID@", safeSubSourceId)
                    : idTemplate + "_" + safeSubSourceId;

            if (usedIds.contains(candidate)) {
                int suffix = 0;
                String fallback;
                do {
                    fallback = idTemplate.contains("@ID@")
                            ? idTemplate.replace("@ID@", Integer.toString(suffix))
                            : idTemplate + "_" + suffix;
                    suffix++;
                } while (usedIds.contains(fallback));
                candidate = fallback;
            }

            usedIds.add(candidate);
            ids.add(candidate);
            fallbackIndex++;
        }

        return ids;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        String text = node.asText(null);
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isDbJsonBackedSource(String definition) {
        if (definition == null) return false;
        String normalized = definition.trim().toUpperCase(Locale.ROOT);
        return normalized.endsWith(".JSON") || normalized.endsWith(".XML");
    }

    private static boolean isTemplateGeneratorId(String generatorId) {
        return generatorId != null && generatorId.contains("@ID@");
    }

    private static boolean isGeneratorGroup(JsonNode generatorNode) {
        return generatorNode != null && generatorNode.path("generatorGroup").asBoolean(false);
    }

    private JsonNode ensureDatasetList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return mapper.createArrayNode();
        }
        // API contract: data is always a list of datasets, independent of widget payload shape.
        ArrayNode wrapped = mapper.createArrayNode();
        wrapped.add(node);
        return wrapped;
    }

    private record GroupResolution(List<String> ids) {}

    // -------- parsing utils used by legacy filters --------
    static final class Parsing {
        static Set<String> parseCsvSet(String csv) {
            if (csv == null || csv.isBlank()) return Collections.emptySet();
            return Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
