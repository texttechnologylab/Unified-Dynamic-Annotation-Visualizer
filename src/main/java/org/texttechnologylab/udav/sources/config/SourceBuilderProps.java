package org.texttechnologylab.udav.sources.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.source-builder.enabled")
public record SourceBuilderProps(boolean enabled) {
}
