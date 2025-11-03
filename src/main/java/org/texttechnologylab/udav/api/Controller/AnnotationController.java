package org.texttechnologylab.udav.api.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.texttechnologylab.udav.api.dto.UimaTypeRow;
import org.texttechnologylab.udav.api.service.UIMATypeService;

import java.util.List;

@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private final UIMATypeService service;

    public AnnotationController(UIMATypeService service) {
        this.service = service;
    }

    // List names with optional search + pagination
    @GetMapping
    public ResponseEntity<List<UimaTypeRow>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String q
    ) throws Exception {
        return ResponseEntity.ok(service.list(page, size, q));
    }
}
