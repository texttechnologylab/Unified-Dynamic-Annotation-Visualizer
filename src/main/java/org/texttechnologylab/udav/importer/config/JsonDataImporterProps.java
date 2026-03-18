package org.texttechnologylab.udav.importer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.json-data-import")
public record JsonDataImporterProps (boolean enabled) {
}