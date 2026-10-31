// uni.textimager.sandbox.api.Controller.DataController

package org.texttechnologylab.udav.api.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper;

    public DataController(PipelineService pipelineService, DataService handler, ObjectMapper mapper) {
        this.pipelineService = pipelineService;
        this.handler = handler;
        this.mapper = mapper;
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

    // ---- helpers & DTO ----

    /**
     * JSON-driven endpoint.
     * Expects a body of the shape:
     * {
     * "corpus": { ... },   // files, tags, date
     * "chart":  { ... }    // contains all existing chart filter key/values
     * }
     * <p>
     * The "chart" values and corpus.files are applied. Tags and date are accepted but not applied yet.
     */
    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> postData(
            @RequestParam("pipelineId") String pipelineId,  
            @RequestParam("generatorId") String generatorId,
            @RequestParam("chartType") String chartType,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "includeIds", required = false) Boolean includeIds,
            @RequestParam(value = "pretty", defaultValue = "false") boolean pretty,
            @RequestBody FilterEnvelope body
    ) throws Exception {

        Map<String, String> filterValues = toStringMap(body.chart());
        Map<String, String> corpusValues = toStringMap(body.corpus());

        JsonNode node = handler.buildDataJson(
                pipelineId,
                generatorId,
                chartType,
                page,
                size,
                includeIds,
                filterValues,
                corpusValues
        );
        String json = pretty
                ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                : mapper.writeValueAsString(node);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json);
    }
    @PostMapping(value = "/data/export")
    public ResponseEntity<byte[]> downloadData(
            @RequestParam("pipelineId") String pipelineId,
            @RequestParam("generatorId") String generatorId,
            @RequestParam("chartType") String chartType,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestBody(required = false) FilterEnvelope body
    ) throws Exception {
        Map<String, String> filterValues = toStringMap(body == null ? null : body.chart());
        Map<String, String> corpusValues = toStringMap(body == null ? null : body.corpus());

        byte[] zip = handler.buildGroupArchive(
                pipelineId,
                generatorId,
                chartType,
                format,
                filterValues,
                corpusValues
        );

        String safeFormat = (format == null || format.isBlank()) ? "json" : format.toLowerCase(Locale.ROOT);
        String filename = String.format(
                Locale.ROOT,
                "%s-%s-all.zip",
                generatorId.replaceAll("[^a-zA-Z0-9._-]", "_"),
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
