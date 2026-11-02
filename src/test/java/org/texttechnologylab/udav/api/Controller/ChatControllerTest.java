package org.texttechnologylab.udav.api.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Without LLM_BASE_URL and LLM_API_TOKEN the proxy answers 503 instead of failing on a relative URI.
 */
class ChatControllerTest {

    @Test
    void unconfiguredProxyAnswers503() throws Exception {
        ChatController controller = new ChatController();
        ReflectionTestUtils.setField(controller, "baseUrl", "");
        ReflectionTestUtils.setField(controller, "apiToken", "");

        assertEquals(503, controller.models().getStatusCode().value());
        assertEquals(503, controller.completions("{}").getStatusCode().value());
    }
}
