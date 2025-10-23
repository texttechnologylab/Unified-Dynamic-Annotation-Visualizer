package org.texttechnologylab.udav.api.Controller;// src/main/java/uni/textimager/sandbox/controller/VisualisationsController.java

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.texttechnologylab.udav.api.service.VisualisationsService;

@RestController
@RequestMapping("/api")
@Deprecated
public class VisualisationsController {

    private final VisualisationsService handler;

    public VisualisationsController(VisualisationsService handler) {
        this.handler = handler;
    }

    @GetMapping(value = "/visualisations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getVisualisations(
            @RequestParam(name = "pipelineId", defaultValue = "main") String pipelineId,
            @RequestParam(name = "pretty", defaultValue = "false") boolean pretty
    ) {
        return ResponseEntity.ok(handler.getVisualisationsJson(pipelineId, pretty));
    }

    /** Create new pipeline entry. 201 Created, Location header. 409 if exists. 400 if invalid JSON. */
    @PostMapping(value = "/visualisations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createVisualisations(
            @RequestParam(name = "pipelineId", defaultValue = "main") String pipelineId,
            @RequestBody String json
    ) {
        try {
            handler.create(pipelineId, json);
            return ResponseEntity
                    .created(java.net.URI.create("/api/visualisations?pipelineId=" + pipelineId))
                    .build();
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // 409
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // 400
        }
    }

    /** Replace existing pipeline entry. 204 if updated, 404 if missing. 400 if invalid JSON. */
    @PutMapping(value = "/visualisations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> replaceVisualisations(
            @RequestParam(name = "pipelineId") String pipelineId,
            @RequestBody String json
    ) {
        try {
            boolean updated = handler.replace(pipelineId, json);
            return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build(); // 400
        }
    }
}
