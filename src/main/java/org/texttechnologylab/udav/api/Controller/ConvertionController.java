package org.texttechnologylab.udav.api.Controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/convertions")
public class ConvertionController {

    @PostMapping("/tikz")
    public ResponseEntity<Map<String, String>> svg2tikz(@RequestBody String body) throws Exception {

        ClassPathResource resource = new ClassPathResource("medial-axis.tex");

        byte[] fileBytes = Files.readAllBytes(resource.getFile().toPath());
        String content = new String(fileBytes, StandardCharsets.UTF_8);

        Map<String, String> response = new HashMap<>();
        response.put("content", content);

        return ResponseEntity.ok(response);
    }
}
