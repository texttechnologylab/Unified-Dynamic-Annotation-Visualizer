package org.texttechnologylab.udav.api.Controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.llm.base-url}")
    private String baseUrl;

    @Value("${app.llm.api-token}")
    private String apiToken;

    @GetMapping("/models")
    public ResponseEntity<String> models() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/models"))
                .header("Authorization", "Bearer " + apiToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return ResponseEntity
                .status(response.statusCode())
                .body(response.body());
    }

    @PostMapping("/completions")
    public ResponseEntity<String> completions(@RequestBody String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat/completions"))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return ResponseEntity
                .status(response.statusCode())
                .body(response.body());
    }

}
