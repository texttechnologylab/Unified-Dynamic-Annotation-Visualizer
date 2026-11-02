package org.texttechnologylab.udav.api.Controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.llm.base-url}")
    private String baseUrl;

    @Value("${app.llm.api-token}")
    private String apiToken;

    private ResponseEntity<String> notConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\":\"LLM_BASE_URL and LLM_API_TOKEN are not set\"}");
    }

    private boolean configured() {
        return baseUrl != null && !baseUrl.isBlank() && apiToken != null && !apiToken.isBlank();
    }

    @GetMapping("/models")
    public ResponseEntity<String> models() throws Exception {
        if (!configured()) return notConfigured();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/models"))
                .timeout(Duration.ofSeconds(30))
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
        if (!configured()) return notConfigured();

        // Vision models on modest hardware can take minutes for one answer
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat/completions"))
                .timeout(Duration.ofMinutes(5))
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
