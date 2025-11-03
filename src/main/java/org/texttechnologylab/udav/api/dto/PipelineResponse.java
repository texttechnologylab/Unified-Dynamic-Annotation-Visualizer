package org.texttechnologylab.udav.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record PipelineResponse(
        String name,
        JsonNode json
) {}
