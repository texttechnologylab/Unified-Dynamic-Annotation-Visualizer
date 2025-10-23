package org.texttechnologylab.udav.importer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.texttechnologylab.udav.importer.dialect.SqlDialect;
import org.texttechnologylab.udav.importer.service.DataInserterService;
import org.texttechnologylab.udav.importer.service.NameSanitizer;
import org.texttechnologylab.udav.importer.service.SchemaGeneratorService;
import org.texttechnologylab.udav.importer.service.XmiParserService;

import javax.xml.parsers.DocumentBuilderFactory;

@Configuration
public class ImporterConfig {
    @Bean
    public DocumentBuilderFactory documentBuilderFactory() {
        return DocumentBuilderFactory.newInstance();
    }

    @Bean
    public NameSanitizer nameSanitizer() {
        return new NameSanitizer();
    }

    @Bean
    public XmiParserService xmiParserService(NameSanitizer nameSanitizer) {
        return new XmiParserService(nameSanitizer);
    }

    @Bean
    public SchemaGeneratorService schemaGeneratorService(JdbcTemplate jdbcTemplate, SqlDialect sqlDialect) {
        return new SchemaGeneratorService(jdbcTemplate, sqlDialect);
    }

    @Bean
    public DataInserterService dataInserterService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new DataInserterService(namedParameterJdbcTemplate);
    }
}
