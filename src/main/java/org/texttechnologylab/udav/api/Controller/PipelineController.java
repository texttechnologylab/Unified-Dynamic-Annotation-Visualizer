package org.texttechnologylab.udav.api.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.texttechnologylab.udav.api.service.PipelineService;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService service;

    public PipelineController(PipelineService service) {
        this.service = service;
    }

    // List names with optional search + pagination
    @GetMapping
    public ResponseEntity<List<String>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String q
    ) throws Exception {
        return ResponseEntity.ok(service.listIds(page, size, q));
    }

    // Get full JSON by name
    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> get(@PathVariable String id) throws Exception {
        JsonNode json = service.get(id);
        return ResponseEntity.ok(json);
    }

    // Create new pipeline
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody JsonNode json) throws Exception {
        String id = service.create(json);
        return ResponseEntity.created(
                // location header: /api/pipelines/{name}
                java.net.URI.create("/api/pipelines/" + id)
        ).build();
    }

    // Update/replace JSON of an existing pipeline
    @PutMapping()
    public ResponseEntity<Void> update(@Valid @RequestBody JsonNode json) throws Exception {
        service.update(json);
        return ResponseEntity.noContent().build();
    }

    // Delete by name
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
