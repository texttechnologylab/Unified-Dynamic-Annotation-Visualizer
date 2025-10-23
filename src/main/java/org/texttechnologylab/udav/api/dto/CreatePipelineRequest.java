package org.texttechnologylab.udav.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;

public record CreatePipelineRequest(
        @NotBlank String name,
        JsonNode json
) {}
