// src/main/java/uni/textimager/sandbox/importer/DatabaseGenerator.java
package org.texttechnologylab.udav.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.texttechnologylab.udav.importer.dialect.SqlDialect;
import org.texttechnologylab.udav.importer.service.DataInserterService;
import org.texttechnologylab.udav.importer.service.NameSanitizer;
import org.texttechnologylab.udav.importer.service.SchemaGeneratorService;
import org.texttechnologylab.udav.importer.service.XmiParserService;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Application runner that generates database schema and populates tables from XMI files.
 * <ul>
 *   <li>Reads .xmi files from configured input directory.</li>
 *   <li>Parses each file into EntityRecord objects.</li>
 *   <li>Computes maximum attribute lengths per table for schema generation.</li>
 *   <li>Creates or updates tables based on computed lengths.</li>
 *   <li>Inserts all parsed records into respective tables.</li>
 *   <li>Creates metadata table TableNames and records each table name.</li>
 * </ul>
 */
//@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.database-generator.enabled", havingValue = "true", matchIfMissing = true)
@Deprecated
public class DatabaseGenerator implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final SqlDialect dialect;
    private final XmiParserService parser;
    private final NameSanitizer sanitizer;
    private final SchemaGeneratorService schemaGen;
    private final DataInserterService dataInserter;

    @Value("${app.input-dir}")
    private String inputDir;

    /**
     * Construct DatabaseGenerator with required services and JDBC components.
     *
     * @param jdbc         JdbcTemplate for executing SQL statements
     * @param dialect      database-specific SQL dialect implementation
     * @param parser       service to parse XMI files into EntityRecord objects
     * @param sanitizer    utility to sanitize tag and attribute names
     * @param schemaGen    service to generate database schema based on attribute lengths
     * @param dataInserter service to batch-insert EntityRecord instances into tables
     */
    public DatabaseGenerator(
            JdbcTemplate jdbc,
            SqlDialect dialect,
            XmiParserService parser,
            NameSanitizer sanitizer,
            SchemaGeneratorService schemaGen,
            DataInserterService dataInserter) {
        this.jdbc = jdbc;
        this.dialect = dialect;
        this.parser = parser;
        this.sanitizer = sanitizer;
        this.schemaGen = schemaGen;
        this.dataInserter = dataInserter;
    }

    /**
     * Execute on application startup:
     * <ol>
     *   <li>Scan input directory for .xmi files.</li>
     *   <li>Parse each file and collect EntityRecord objects.</li>
     *   <li>Track maximum string lengths per table column for schema sizing.</li>
     *   <li>Generate or update tables via SchemaGeneratorService.</li>
     *   <li>Insert parsed records via DataInserterService.</li>
     *   <li>Create metadata table "TableNames" and insert table names.</li>
     * </ol>
     *
     * @param args application arguments (ignored)
     * @throws Exception on file, I/O or parsing errors
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        Map<String, Map<String, Integer>> maxLengths = new LinkedHashMap<>();
        List<EntityRecord> allRecords = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Path.of(inputDir), "*.xmi")) {
            for (Path file : directoryStream) {
                String fileName = file.getFileName().toString();
                for (EntityRecord entityRecord : parser.parse(file)) {
                    String table = sanitizer.toClassName(entityRecord.tag());
                    maxLengths.computeIfAbsent(table, k -> new HashMap<>())
                            .merge("filename", fileName.length(), Math::max);
                    entityRecord.attributes().forEach((raw, val) ->
                            maxLengths.get(table)
                                    .merge(sanitizer.sanitize(raw), val.length(), Math::max)
                    );
                    allRecords.add(entityRecord);
                }
            }
        }

        schemaGen.generateSchema(maxLengths);
        dataInserter.insertRecords(allRecords);

        // Insert metadata about tables
        String metaTable = "TableNames";
        String metaPk = "tablenames_id";
        String ddlMeta = "CREATE TABLE IF NOT EXISTS " + metaTable + " ("
                + dialect.autoIncrementPrimaryKey(metaPk) + ", "
                + "table_name " + dialect.varcharType(255) + ", "
                + "PRIMARY KEY(" + metaPk + "))";
        jdbc.execute(ddlMeta);

        String insertMeta = "INSERT INTO " + metaTable + "(table_name) VALUES(?)";
        List<Object[]> batchArgs = maxLengths.keySet().stream()
                .map(name -> new Object[]{name})
                .toList();
        jdbc.batchUpdate(insertMeta, batchArgs);
    }
}
