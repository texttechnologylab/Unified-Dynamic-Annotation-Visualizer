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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.db.SchemaObjectNames;

import org.json.XML;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final String folderPath;
    private final boolean replaceIfDifferent;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.db.schema:public}")
    private String schema;

    @Autowired
    public JsonDataImporter(
            DataSource dataSource,
            @Value("${app.json-data-import.folder:sourcefilesJSON}") String folderPath,
            @Value("${app.json-data-import.replace-if-different:false}") boolean replaceIfDifferent
    ) {
        this(dataSource, folderPath, replaceIfDifferent, new PathMatchingResourcePatternResolver());
    }

    JsonDataImporter(
            DataSource dataSource,
            String folderPath,
            boolean replaceIfDifferent,
            ResourcePatternResolver resourceResolver
    ) {
        this.dataSource = dataSource;
        this.folderPath = folderPath;
        this.replaceIfDifferent = replaceIfDifferent;
        this.resourceResolver = resourceResolver;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<ImportCandidate> candidates = resolveImportCandidates();
        if (candidates.isEmpty()) {
            LOGGER.warn("No JSON/XML import files found for configured source '{}'.", folderPath);
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

            candidates.forEach(candidate -> importOne(dsl, T, F_NAME, F_JSON, candidate));
        }
    }

    List<ImportCandidate> resolveImportCandidates() throws IOException {
        String configured = normalizeConfiguredFolder(folderPath);

        if (configured.startsWith("classpath*:") || configured.startsWith("classpath:")) {
            return resolveClasspathCandidates(configured);
        }

        Path filesystemPath = resolveFilesystemPath(configured);
        if (configured.startsWith("file:")) {
            if (Files.exists(filesystemPath) && Files.isDirectory(filesystemPath)) {
                return resolveFilesystemCandidates(filesystemPath);
            }
            LOGGER.warn("Configured JSON import file URI is not a directory: {}", filesystemPath.toAbsolutePath());
            return List.of();
        }

        if (filesystemPath != null && Files.exists(filesystemPath) && Files.isDirectory(filesystemPath)) {
            return resolveFilesystemCandidates(filesystemPath);
        }

        String classpathFolder = normalizeClasspathFolder(configured);
        List<ImportCandidate> candidates = resolveClasspathCandidates("classpath*:" + classpathFolder);
        if (candidates.isEmpty()) {
            String filesystemDescription = filesystemPath == null ? configured : filesystemPath.toAbsolutePath().toString();
            LOGGER.warn(
                    "Configured JSON import folder was not found as filesystem directory '{}' or classpath folder '{}'.",
                    filesystemDescription,
                    classpathFolder
            );
        }
        return candidates;
    }

    private List<ImportCandidate> resolveFilesystemCandidates(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> isSupportedFileName(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> new ImportCandidate(
                            p.getFileName().toString(),
                            p.toAbsolutePath().toString(),
                            () -> Files.readString(p, StandardCharsets.UTF_8)
                    ))
                    .toList();
        }
    }

    private List<ImportCandidate> resolveClasspathCandidates(String location) throws IOException {
        String pattern = toClasspathSearchPattern(location);
        return Arrays.stream(resourceResolver.getResources(pattern))
                .filter(Resource::exists)
                .filter(resource -> resource.getFilename() != null)
                .filter(resource -> isSupportedFileName(resource.getFilename()))
                .sorted(Comparator.comparing(Resource::getFilename))
                .map(resource -> new ImportCandidate(
                        resource.getFilename(),
                        resource.getDescription(),
                        () -> readResource(resource)
                ))
                .toList();
    }

    private void importOne(DSLContext dsl,
                           Table<Record> T,
                           Field<String> F_NAME,
                           Field<String> F_JSON,
                           ImportCandidate candidate) {
        try {
            String raw = candidate.readContent();
            String sourceFileName = candidate.fileName();
            String canonicalJson;

            if (sourceFileName.toLowerCase(Locale.ROOT).endsWith(".xml")) {
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
            LOGGER.error("Failed to import JSON data from file {}: {}", candidate.description(), e.getMessage());
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

    private static String normalizeConfiguredFolder(String value) {
        if (value == null || value.isBlank()) {
            return "sourcefilesJSON";
        }
        return value.trim().replace('\\', '/');
    }

    private static Path resolveFilesystemPath(String location) {
        if (location.startsWith("file:")) {
            return Paths.get(URI.create(location));
        }
        return Paths.get(location);
    }

    private static String normalizeClasspathFolder(String location) {
        String normalized = location;
        if (normalized.startsWith("classpath*:")) {
            normalized = normalized.substring("classpath*:".length());
        } else if (normalized.startsWith("classpath:")) {
            normalized = normalized.substring("classpath:".length());
        }

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        String srcResourcesPrefix = "src/main/resources/";
        if (normalized.startsWith(srcResourcesPrefix)) {
            normalized = normalized.substring(srcResourcesPrefix.length());
        } else {
            int srcResourcesIndex = normalized.indexOf("/" + srcResourcesPrefix);
            if (srcResourcesIndex >= 0) {
                normalized = normalized.substring(srcResourcesIndex + srcResourcesPrefix.length() + 1);
            }
        }

        String resourcesPrefix = "resources/";
        if (normalized.startsWith(resourcesPrefix)) {
            normalized = normalized.substring(resourcesPrefix.length());
        } else {
            int resourcesIndex = normalized.indexOf("/" + resourcesPrefix);
            if (resourcesIndex >= 0) {
                normalized = normalized.substring(resourcesIndex + resourcesPrefix.length() + 1);
            }
        }

        return normalized;
    }

    private static String toClasspathSearchPattern(String location) {
        String normalized = normalizeClasspathFolder(location);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return "classpath*:" + normalized + "/*";
    }

    private static boolean isSupportedFileName(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".json") || normalized.endsWith(".xml");
    }

    private static String readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record ImportCandidate(String fileName, String description, ContentReader reader) {
        String readContent() throws IOException {
            return reader.read();
        }
    }

    @FunctionalInterface
    interface ContentReader {
        String read() throws IOException;
    }
}
