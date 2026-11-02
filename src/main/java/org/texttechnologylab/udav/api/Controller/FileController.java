package org.texttechnologylab.udav.api.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.texttechnologylab.udav.api.service.FileService;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    // List names with optional search + pagination
    @GetMapping("/documents")
    public ResponseEntity<List<String>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String q
    ) throws Exception {
        return ResponseEntity.ok(service.listDocumentIds(page, size, q));
    }

    // Tags of the corpus filter: the collection ids of the imported documents
    @GetMapping("/tags")
    public ResponseEntity<List<String>> tags(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.listTags(q));
    }

    // Get full JSON by name
    @GetMapping("/documents/{id}")
    public ResponseEntity<String> get(@PathVariable String id) throws Exception {
        // not implemented yet
        return ResponseEntity.status(501).body("Not implemented");
    }
}
