package org.texttechnologylab.udav.api.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.texttechnologylab.udav.api.service.PipelineService;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    // List names with optional search + pagination
    @GetMapping
    public ResponseEntity<List<Map<String, String>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String q) throws Exception {
        return ResponseEntity.ok(service.listSummaries(page, size, q));
    }

    // Get full JSON by name
    @GetMapping("/{id}")
    public ResponseEntity<String> get(
            @PathVariable String id,
            @RequestParam(name = "pretty", defaultValue = "false") boolean pretty) throws Exception {
        JsonNode node = service.get(id);
        String json = pretty
                ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                : mapper.writeValueAsString(node);
        return ResponseEntity.ok(json);
    }

    // Create new pipeline
    @PostMapping
    public ResponseEntity<JsonNode> create(@Valid @RequestBody JsonNode json) throws Exception {
        service.create(json);
        return ResponseEntity.ok(json);
    }

    // Update/replace JSON of an existing pipeline
    @PutMapping()
    public ResponseEntity<JsonNode> update(@Valid @RequestBody JsonNode json) throws Exception {
        service.update(json);
        return ResponseEntity.ok(json);
    }

    // Delete by name
    @DeleteMapping("/{id}")
    public ResponseEntity<JsonNode> delete(@PathVariable String id) throws Exception {
        JsonNode json = service.get(id);
        service.delete(id);
        return ResponseEntity.ok(json);
    }
}
