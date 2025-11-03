package org.texttechnologylab.udav.importer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duui.importer")
public record DUUIImporterProps(boolean enabled) {
}
