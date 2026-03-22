package org.texttechnologylab.udav.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.db.SchemaObjectNames;

import org.json.XML;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.stream.Stream;

import static org.jooq.impl.DSL.*;

@Order(1)
@Component
@ConditionalOnProperty(name = "app.json-data-import.enabled", havingValue = "true")
public class JsonDataImporter implements ApplicationRunner {

    private static final String TABLE = SchemaObjectNames.TABLE_JSON_DATA;
    private static final String COL_NAME = SchemaObjectNames.COL_JSON_DATA_SOURCEFILE_NAME;
    private static final String COL_JSON = SchemaObjectNames.COL_JSON_DATA_JSON;
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonDataImporter.class);

    private final DataSource dataSource;
    private final Path folder;
    private final boolean replaceIfDifferent;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.db.schema:public}")
    private String schema;

    public JsonDataImporter(
            DataSource dataSource,
            @Value("${app.json-data-import.folder:sourcefilesJSON}") String folderPath,
            @Value("${app.json-data-import.replace-if-different:false}") boolean replaceIfDifferent
    ) {
        this.dataSource = dataSource;
        this.folder = Paths.get(folderPath);
        this.replaceIfDifferent = replaceIfDifferent;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            LOGGER.warn("sourcefilesJSON folder does not exist or is not a directory: {}", folder.toAbsolutePath());
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // Ensure schema + table
            dsl.createSchemaIfNotExists(DSL.name(schema)).execute();

            Table<Record> T = table(name(schema, TABLE));
            Field<String> F_NAME = field(name(schema, TABLE, COL_NAME), String.class);
            Field<String> F_JSON = field(name(schema, TABLE, COL_JSON), String.class);

            dsl.createTableIfNotExists(T)
                    .column(F_NAME, SQLDataType.VARCHAR(255).nullable(false))
                    .column(F_JSON, SQLDataType.CLOB.nullable(false))
                    .constraints(constraint("PK_" + TABLE).primaryKey(F_NAME))
                    .execute();

            LOGGER.info("Ensured schema and table exist: {}.{}", schema, TABLE);

            try (Stream<Path> files = Files.list(folder)) {
                files.filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return Files.isRegularFile(p)
                                    && (name.endsWith(".json") || name.endsWith(".xml"));
                        })
                        .forEach(p -> importOne(dsl, T, F_NAME, F_JSON, p));
            }
        }
    }

    private void importOne(DSLContext dsl,
                           Table<Record> T,
                           Field<String> F_NAME,
                           Field<String> F_JSON,
                           Path p) {
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            String sourceFileName = p.getFileName().toString();
            String canonicalJson;

            if (sourceFileName.toLowerCase().endsWith(".xml")) {
                canonicalJson = convertXmlToJson(raw);
            } else {
                canonicalJson = canonicalize(raw);
            }

            boolean nameExists = sourceFileNameExists(dsl, T, F_NAME, sourceFileName);

            if (!nameExists) {
                dsl.insertInto(T)
                        .columns(F_NAME, F_JSON)
                        .values(sourceFileName, canonicalJson)
                        .execute();

                LOGGER.info("JSON data with name {} has been inserted.", sourceFileName);
                return;
            }

            if (replaceIfDifferent) {
                String existingJson = dsl.select(F_JSON).from(T).where(F_NAME.eq(sourceFileName)).fetchOne(F_JSON);

                String existingCanon = (existingJson == null) ? null : canonicalize(existingJson);
                String newCanon = canonicalize(canonicalJson);

                if (existingCanon != null && existingCanon.equals(newCanon)) {
                    LOGGER.warn("Skipped {} (unchanged)", sourceFileName);
                    return;
                }

                int updated = dsl.update(T)
                        .set(F_JSON, canonicalJson)
                        .where(F_NAME.eq(sourceFileName))
                        .execute();

                LOGGER.info("JSON data with name {} has been {}.", sourceFileName,
                        updated == 1 ? "updated" : "not updated");
                return;
            }

            LOGGER.warn("JSON data with name {} already exists. Skipping.", sourceFileName);

        } catch (Exception e) {
            LOGGER.error("Failed to import JSON data from file {}: {}", p.getFileName(), e.getMessage());
        }
    }

    // --- Helpers ---

    private boolean sourceFileNameExists(DSLContext dsl, Table<Record> T, Field<String> F_NAME, String name) {
        return dsl.fetchExists(selectOne().from(T).where(F_NAME.eq(name)));
    }

    private String canonicalize(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return mapper.writeValueAsString(node);
    }

    private String convertXmlToJson(String xml) {
        return XML.toJSONObject(xml).toString();
    }
}
