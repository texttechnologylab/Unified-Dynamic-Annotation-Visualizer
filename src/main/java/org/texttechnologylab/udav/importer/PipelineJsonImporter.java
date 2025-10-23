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
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.service.SourceBuildService;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.jooq.impl.DSL.*;

@Component
@ConditionalOnProperty(name = "app.pipeline-json-import.enabled", havingValue = "true")
public class PipelineJsonImporter implements ApplicationRunner {

    private static final String TABLE = "pipeline";
    private static final String COL_NAME = "pipeline_name";
    private static final String COL_JSON = "json";
    private static final String PIPELINE_ID = "pipeline_id";
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineJsonImporter.class);
    private final DataSource dataSource;
    private final Path folder;
    private final boolean replaceIfDifferent;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SourceBuildService sourceBuildService;
    @Value("${app.db.schema:public}")
    private String schema;

    public PipelineJsonImporter(
            DataSource dataSource,
            SourceBuildService sourceBuildService,
            @Value("${app.pipeline-json-import.folder:pipelines}") String folderPath,
            @Value("${app.pipeline-json-import.replace-if-different:false}") boolean replaceIfDifferent
    ) {
        this.dataSource = dataSource;
        this.sourceBuildService = sourceBuildService;
        this.folder = Paths.get(folderPath);
        this.replaceIfDifferent = replaceIfDifferent;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            LOGGER.warn("Pipeline folder does not exist or is not a directory: {}", folder.toAbsolutePath());
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // Ensure schema + table
            dsl.createSchemaIfNotExists(DSL.name(schema)).execute();

            Table<Record> T = table(name(schema, TABLE));
            Field<String> F_NAME = field(name(schema, TABLE, COL_NAME), String.class);
            Field<String> F_JSON = field(name(schema, TABLE, COL_JSON), String.class);
            Field<String> F_ID = field(name(schema, TABLE, PIPELINE_ID), String.class);

            dsl.createTableIfNotExists(T)
                    .column(F_NAME, SQLDataType.VARCHAR(255).nullable(false))
                    .column(F_JSON, SQLDataType.CLOB.nullable(false))
                    .column(F_ID, SQLDataType.VARCHAR(255).nullable(false))
                    .constraints(constraint("PK_" + TABLE).primaryKey(F_ID))
                    .execute();

            LOGGER.info("Ensured schema and table exist: {}.{}", schema, TABLE);

            try (Stream<Path> files = Files.list(folder)) {
                files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                        .forEach(p -> importOne(dsl, T, F_NAME, F_JSON, F_ID, p));
            }
        }
    }

    private void importOne(DSLContext dsl,
                           Table<Record> T,
                           Field<String> F_NAME,
                           Field<String> F_JSON,
                           Field<String> F_ID,
                           Path p) {
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            ParsedPipeline parsed = parseAndCanonicalize(raw);

            String canonicalJson = parsed.canonicalJson();
            String pipelineIdOriginal = parsed.pipelineId();
            String pipelineName = filenameWithoutExt(p.getFileName().toString());

            if (pipelineNameExists(dsl, T, F_NAME, pipelineName)) {
                LOGGER.warn("Pipeline with name {} already exists.", pipelineName);
                return;
            }

            boolean idExists = pipelineIdExists(dsl, T, F_ID, pipelineIdOriginal);

            if (!idExists) {
                dsl.insertInto(T)
                        .columns(F_NAME, F_JSON, F_ID)
                        .values(pipelineName, canonicalJson, pipelineIdOriginal)
                        .execute();

                LOGGER.info("Pipeline with name {} and id {} has been inserted.", pipelineName, pipelineIdOriginal);

                sourceBuildService.startBuild(pipelineIdOriginal, pipelineIdOriginal);
                return;
            }

            if (replaceIfDifferent) {
                String existingCanonical = dsl.select(F_JSON).from(T).where(F_ID.eq(pipelineIdOriginal)).fetchOne(F_JSON);

                String existingCanon = (existingCanonical == null) ? null : canonicalize(existingCanonical);
                String newCanon = canonicalize(canonicalJson);

                if (existingCanon != null && existingCanon.equals(newCanon)) {
                    LOGGER.warn("Skipped {} (unchanged)", pipelineIdOriginal);
                    return;
                }

                int updated = dsl.update(T)
                        .set(F_JSON, canonicalJson)
                        .set(F_NAME, pipelineName)
                        .where(F_ID.eq(pipelineIdOriginal))
                        .execute();

                LOGGER.info("Pipeline with id {} has been {} from file {}.", pipelineIdOriginal,
                        updated == 1 ? "updated" : "not updated", pipelineName);

                // 🔧 build sources for this pipeline in this schema
                sourceBuildService.startBuild(pipelineIdOriginal, pipelineIdOriginal);
                return;
            }

            // duplicate id, create a unique one and insert
            String uniqueId = ensureUniquePipelineId(dsl, T, F_ID, pipelineIdOriginal);
            dsl.insertInto(T)
                    .columns(F_NAME, F_JSON, F_ID)
                    .values(pipelineName, canonicalJson, uniqueId)
                    .execute();

            LOGGER.info("Inserted duplicate pipeline as id={} (original id {}, file {})", uniqueId, pipelineIdOriginal, pipelineName);

            sourceBuildService.startBuild(uniqueId, uniqueId);

        } catch (Exception e) {
            LOGGER.error("Failed to import pipeline from file {}: {}", p.getFileName(), e.getMessage());
        }
    }

    // --- Helpers (unchanged logic, but schema-qualified versions for existence checks) ---

    private ParsedPipeline parseAndCanonicalize(String raw) throws Exception {
        JsonNode root = mapper.readTree(raw);
        String pipelineId = extractPipelineId(root);
        String canonical = mapper.writeValueAsString(root);
        return new ParsedPipeline(canonical, pipelineId);
    }

    private String extractPipelineId(JsonNode root) {
        JsonNode pipelineNode = root;
        if (root.has("pipelines")) {
            JsonNode arr = root.get("pipelines");
            if (!arr.isArray() || arr.isEmpty() || !arr.get(0).isObject()) {
                throw new IllegalArgumentException("Invalid pipeline JSON: expected non-empty array at \"pipelines\".");
            }
            pipelineNode = arr.get(0);
        }
        JsonNode idNode = pipelineNode.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            throw new IllegalArgumentException("Invalid pipeline JSON: missing textual \"id\".");
        }
        return idNode.asText();
    }

    private boolean pipelineIdExists(DSLContext dsl, Table<Record> T, Field<String> F_ID, String pipelineId) {
        return dsl.fetchExists(selectOne().from(T).where(F_ID.eq(pipelineId)));
    }

    private boolean pipelineNameExists(DSLContext dsl, Table<Record> T, Field<String> F_NAME, String pipelineName) {
        return dsl.fetchExists(selectOne().from(T).where(F_NAME.eq(pipelineName)));
    }

    private String ensureUniquePipelineId(DSLContext dsl, Table<Record> T, Field<String> F_ID, String id) {
        if (!pipelineIdExists(dsl, T, F_ID, id)) return id;

        final int maxLen = 255;
        String base = id;
        int counter = 2;

        Matcher m = Pattern.compile("^(.*?)-(\\d+)$").matcher(id);
        if (m.matches()) {
            base = m.group(1);
            try {
                counter = Integer.parseInt(m.group(2)) + 1;
            } catch (NumberFormatException ignored) {
            }
        }

        while (true) {
            String suffix = "-" + counter;
            int keep = Math.max(1, maxLen - suffix.length());
            String trimmedBase = base.length() > keep ? base.substring(0, keep) : base;
            String candidate = trimmedBase + suffix;

            if (!pipelineIdExists(dsl, T, F_ID, candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private String filenameWithoutExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private String canonicalize(String json) throws Exception {
        JsonNode node = mapper.readTree(json);
        return mapper.writeValueAsString(node);
    }

    private record ParsedPipeline(String canonicalJson, String pipelineId) {
    }
}
