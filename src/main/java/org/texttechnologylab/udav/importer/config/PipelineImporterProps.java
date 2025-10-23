package org.texttechnologylab.udav.importer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.pipeline-json-import")
public record PipelineImporterProps (boolean enabled) {
}
