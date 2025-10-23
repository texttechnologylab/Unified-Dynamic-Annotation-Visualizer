package org.texttechnologylab.udav.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdatePipelineRequest(
        JsonNode json
) {}
