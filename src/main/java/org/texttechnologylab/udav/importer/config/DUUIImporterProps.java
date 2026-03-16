package org.texttechnologylab.udav.importer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "duui.importer")
public record DUUIImporterProps(
    boolean enabled,
    String inputPath,
    String inputFileEnding,
    String typeSystemPath,
    int workers,
    int casPoolSize
) {
    public DUUIImporterProps {
        workers = workers > 0 ? workers : 4;
        casPoolSize = casPoolSize > 0 ? casPoolSize : workers * 2;
    }
}
