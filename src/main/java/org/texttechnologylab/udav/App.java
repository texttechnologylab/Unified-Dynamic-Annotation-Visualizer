package org.texttechnologylab.udav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.texttechnologylab.udav.importer.config.DUUIImporterProps;
import org.texttechnologylab.udav.importer.config.DbProps;
import org.texttechnologylab.udav.importer.config.JsonDataImporterProps;
import org.texttechnologylab.udav.importer.config.PipelineImporterProps;
import org.texttechnologylab.udav.sources.config.SourceBuilderProps;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({DbProps.class, DUUIImporterProps.class, SourceBuilderProps.class, JsonDataImporterProps.class, PipelineImporterProps.class})
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
