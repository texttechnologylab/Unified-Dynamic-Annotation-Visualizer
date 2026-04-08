// uni.textimager.sandbox.api.Controller.DataController

package org.texttechnologylab.udav.api.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Setter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.texttechnologylab.udav.api.service.DataService;
import org.texttechnologylab.udav.api.service.PipelineService;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api")
public class DataController {

    private final PipelineService pipelineService;
    private final DataService handler;

    public DataController(PipelineService pipelineService, DataService handler) {
        this.pipelineService = pipelineService;
        this.handler = handler;
    }

    private static Map<String, String> toStringMap(Map<String, Object> src) {
        if (src == null) return new LinkedHashMap<>();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = e.getKey();
            if (key == null) continue;

            Object v = e.getValue();
            if (v == null) {
                out.put(key, null);
                continue;
            }

            if (v instanceof Iterable<?>) {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object item : (Iterable<?>) v) {
                    if (!first) sb.append(',');
                    sb.append(Objects.toString(item, ""));
                    first = false;
                }
                out.put(key, sb.toString());
            } else {
                out.put(key, Objects.toString(v, null));
            }
        }
        return out;
    }

    /**
     * Backward-compatible GET endpoint. Keeps existing query-parameter based filter passing.
     */
    @GetMapping(value = "/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getData(
            @RequestParam("id") String visId,
            @RequestParam(value = "pipelineId", defaultValue = "main-9") String pipelineId,
            @RequestParam Map<String, String> allParams,
            @RequestParam(value = "pretty", defaultValue = "false") boolean pretty
    ) throws Exception {

        // Extract legacy "filters=" style params into a LinkedHashMap to preserve order
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : allParams.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            // exclude controller-known params
            String kn = k.toLowerCase(Locale.ROOT);
            if (kn.equals("id") || kn.equals("pretty") || kn.equals("pipelineid")) continue;
            filters.put(k, e.getValue());
        }
        JsonNode widget = pipelineService.get(pipelineId).get("widgets").get(visId);

        String generatorId = widget.get("generator").get("id").asText();
        String chartType = widget.get("type").asText();

        String json = handler.buildArrayJson(generatorId, chartType, filters, null, pretty, pipelineId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json);
    }

    // ---- helpers & DTO ----

    /**
     * JSON-driven endpoint.
     * Expects a body of the shape:
     * {
     * "corpus": { ... },   // reserved for future: files, tags, date (not yet implemented)
     * "chart":  { ... }    // contains all existing chart filter key/values
     * }
     * <p>
     * Only "chart" values are applied to the current data pipeline. "corpus" is accepted and ignored for now.
     */
    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> postData(
            @RequestParam("pipelineId") String pipelineId,  
            @RequestParam("generatorId") String generatorId,
            @RequestParam("chartType") String chartType,
            @RequestParam(value = "pretty", defaultValue = "false") boolean pretty,
            @RequestBody FilterEnvelope body
    ) throws Exception {

        Map<String, String> filterValues = toStringMap(body.chart());
        Map<String, String> corpusValues = toStringMap(body.corpus());

        String json = handler.buildArrayJson(generatorId, chartType, filterValues, corpusValues, pretty, pipelineId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json);
    }

    @GetMapping(value = "/data/groups/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> resolveGroup(
            @RequestParam("pipelineId") String pipelineId,
            @RequestParam("templateGeneratorId") String templateGeneratorId,
            @RequestParam(value = "chartType", required = false) String chartType
    ) throws Exception {
        return ResponseEntity.ok(handler.resolveGroup(pipelineId, templateGeneratorId, chartType));
    }

    @PostMapping(value = "/data/groups/item", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getGroupItem(
            @RequestParam("pipelineId") String pipelineId,
            @RequestParam("templateGeneratorId") String templateGeneratorId,
            @RequestParam("chartType") String chartType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestBody FilterEnvelope body
    ) throws Exception {
        Map<String, String> filterValues = toStringMap(body.chart());
        Map<String, String> corpusValues = toStringMap(body.corpus());
        return ResponseEntity.ok(handler.groupItemByPage(
                pipelineId,
                templateGeneratorId,
                chartType,
                page,
                filterValues,
                corpusValues
        ));
    }

    @PostMapping(value = "/data/groups/itemById", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> getGroupItemById(
            @RequestParam("pipelineId") String pipelineId,
            @RequestParam("templateGeneratorId") String templateGeneratorId,
            @RequestParam("chartType") String chartType,
            @RequestParam("generatorId") String generatorId,
            @RequestBody FilterEnvelope body
    ) throws Exception {
        Map<String, String> filterValues = toStringMap(body.chart());
        Map<String, String> corpusValues = toStringMap(body.corpus());
        return ResponseEntity.ok(handler.groupItemById(
                pipelineId,
                templateGeneratorId,
                chartType,
                generatorId,
                filterValues,
                corpusValues
        ));
    }

    @PostMapping(value = "/data/groups/download")
    public ResponseEntity<byte[]> downloadGroup(
            @RequestParam("pipelineId") String pipelineId,
            @RequestParam("templateGeneratorId") String templateGeneratorId,
            @RequestParam("chartType") String chartType,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestBody(required = false) FilterEnvelope body
    ) throws Exception {
        Map<String, String> filterValues = toStringMap(body == null ? null : body.chart());
        Map<String, String> corpusValues = toStringMap(body == null ? null : body.corpus());

        byte[] zip = handler.buildGroupArchive(
                pipelineId,
                templateGeneratorId,
                chartType,
                format,
                filterValues,
                corpusValues
        );

        String safeFormat = (format == null || format.isBlank()) ? "json" : format.toLowerCase(Locale.ROOT);
        String filename = String.format(
                Locale.ROOT,
                "%s-%s-all.zip",
                templateGeneratorId.replaceAll("[^a-zA-Z0-9._-]", "_"),
                safeFormat.replaceAll("[^a-zA-Z0-9._-]", "_")
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(zip);
    }

    /**
     * Envelope DTO for the posted filters.
     */
    @Setter
    public static class FilterEnvelope {
        private Map<String, Object> corpus;
        private Map<String, Object> chart;

        public Map<String, Object> corpus() {
            return corpus;
        }

        public Map<String, Object> chart() {
            return chart;
        }

    }
}
