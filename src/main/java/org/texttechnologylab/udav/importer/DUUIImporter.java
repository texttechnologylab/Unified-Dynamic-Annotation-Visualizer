package org.texttechnologylab.udav.importer;

import lombok.RequiredArgsConstructor;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.dkpro.core.io.xmi.XmiWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIDockerDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIUIMADriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUIAsynchronousProcessor;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.reader.DUUIFileReaderLazy;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.texttechnologylab.udav.importer.config.DUUIImporterProps;
import org.texttechnologylab.udav.importer.config.DbProps;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "duui.importer", name = "enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class DUUIImporter implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DUUIImporter.class);

    private final DbProps db;
    private final DUUIImporterProps duuiProps;
    private final PostImportRowCounter postImportRowCounter;
    private final PostImportIndexBuilder postImportIndexBuilder;

    private DUUIComposer composer;
    private TypeSystemDescription externalTypeSystem;

    private void init() throws IOException, URISyntaxException, UIMAException, SAXException {
        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();

        composer = new DUUIComposer()
                .withSkipVerification(resolveBooleanProp("skipVerification", "DUUI_IMPORTER_SKIP_VERIFICATION", false))
                .withLuaContext(ctx)
                .withWorkers(duuiProps.workers());

        DUUIUIMADriver uimaDriver = new DUUIUIMADriver();
        DUUIDockerDriver dockerDriver = new DUUIDockerDriver();

        composer.addDriver(uimaDriver, dockerDriver);

        String tsPath = duuiProps.typeSystemPath();

        if (tsPath != null && !tsPath.isBlank()) {
            File tsFile = new File(tsPath);

            if (!tsFile.isFile()) {
                throw new IOException("Configured DUUI type system file not found: " + tsPath);
            }

            LOGGER.info("Loading external type system from: {}", tsPath);

            TypeSystemDescription tsd = UIMAFramework.getXMLParser()
                    .parseTypeSystemDescription(new XMLInputSource(tsFile));

            tsd.resolveImports();

            composer.setInstantiatedTypeSystem(tsd);
            externalTypeSystem = tsd;
        }
    }

    public void execute() throws Exception {
        boolean storeCoveredText = resolveBooleanProp(
                "storeCoveredText",
                "DUUI_IMPORTER_STORE_COVERED_TEXT",
                false
        );

        boolean prepareDbSchema = resolveBooleanProp(
                "prepareDbSchema",
                "DUUI_IMPORTER_PREPARE_DB_SCHEMA",
                true
        );

        int dbScale = resolveIntProp("dbWorkers", "DUUI_IMPORTER_DB_WORKERS", 1);

        DUUIFileReaderLazy corpusReader =
                new DUUIFileReaderLazy(
                        duuiProps.inputPath(),
                        duuiProps.inputFileEnding(),
                        resolveIntProp("readerBatchSize", "DUUI_IMPORTER_READER_BATCH_SIZE", 10)
                );

        DUUIAsynchronousProcessor processor = new DUUIAsynchronousProcessor(corpusReader);

        composer.add(new DUUIUIMADriver.Component(
                createEngineDescription(RemoveMetaInformation.class, externalTypeSystem))
                .withScale(duuiProps.workers())
                .build());

        if (resolveBooleanProp("debugXmi", "DUUI_IMPORTER_DEBUG_XMI", false)) {
            String target = resolveStringProp("debugXmiPath", "DUUI_IMPORTER_DEBUG_XMI_PATH", "/tmp/export");

            composer.add(new DUUIUIMADriver.Component(
                    createEngineDescription(
                            XmiWriter.class,
                            externalTypeSystem,
                            XmiWriter.PARAM_TARGET_LOCATION, target,
                            XmiWriter.PARAM_PRETTY_PRINT, false,
                            XmiWriter.PARAM_OVERWRITE, true,
                            XmiWriter.PARAM_VERSION, "1.1",
                            XmiWriter.PARAM_COMPRESSION, "GZIP"
                    ))
                    .withScale(1)
                    .build());
        }

        if (prepareDbSchema) {
            LOGGER.info("Adding DB schema-preparation stage. DDL will run with scale=1 before parallel DB COPY writers.");

            composer.add(new DUUIUIMADriver.Component(
                    createEngineDescription(
                            JooqDatabaseWriter.class,
                            externalTypeSystem,
                            JooqDatabaseWriter.PARAM_JDBC_URL, db.getUrl(),
                            JooqDatabaseWriter.PARAM_DB_USER, db.getUser(),
                            JooqDatabaseWriter.PARAM_DB_PASS, db.getPass(),
                            JooqDatabaseWriter.PARAM_SCHEMA, db.getSchema(),
                            JooqDatabaseWriter.PARAM_BATCH_SIZE, db.getBatchSize(),
                            JooqDatabaseWriter.PARAM_MAX_IDENT, db.getMaxIdent(),
                            JooqDatabaseWriter.PARAM_SQL_DIALECT, db.getDialect(),
                            JooqDatabaseWriter.PARAM_PIPELINE_HASH, buildPipelineHash(),
                            JooqDatabaseWriter.PARAM_STORE_COVERED_TEXT, storeCoveredText,
                            JooqDatabaseWriter.PARAM_ALLOW_DDL, true,
                            JooqDatabaseWriter.PARAM_PREPARE_SCHEMA_ONLY, true
                    ))
                    .withScale(1)
                    .build());
        }

        LOGGER.info(
                "Adding DB COPY writer stage. dbWorkers={} storeCoveredText={} allowDdl={}",
                dbScale,
                storeCoveredText,
                !prepareDbSchema
        );

        composer.add(new DUUIUIMADriver.Component(
                createEngineDescription(
                        JooqDatabaseWriter.class,
                        externalTypeSystem,
                        JooqDatabaseWriter.PARAM_JDBC_URL, db.getUrl(),
                        JooqDatabaseWriter.PARAM_DB_USER, db.getUser(),
                        JooqDatabaseWriter.PARAM_DB_PASS, db.getPass(),
                        JooqDatabaseWriter.PARAM_SCHEMA, db.getSchema(),
                        JooqDatabaseWriter.PARAM_BATCH_SIZE, db.getBatchSize(),
                        JooqDatabaseWriter.PARAM_MAX_IDENT, db.getMaxIdent(),
                        JooqDatabaseWriter.PARAM_SQL_DIALECT, db.getDialect(),
                        JooqDatabaseWriter.PARAM_PIPELINE_HASH, buildPipelineHash(),
                        JooqDatabaseWriter.PARAM_STORE_COVERED_TEXT, storeCoveredText,
                        JooqDatabaseWriter.PARAM_ALLOW_DDL, !prepareDbSchema,
                        JooqDatabaseWriter.PARAM_PREPARE_SCHEMA_ONLY, false
                ))
                .withScale(dbScale)
                .build());

        try {
            composer.run(processor, "Importer");
        } finally {
            if (composer != null) {
                composer.shutdown();
            }
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        init();
        execute();
        postImportIndexBuilder.buildIndexes();
        postImportRowCounter.updateRowCounts();
    }

    private String buildPipelineHash() {
        List<String> parts = new ArrayList<>();

        parts.add("writerSchemaVersion=6-no-rowhash-no-pk");
        parts.add("inputEnding=" + duuiProps.inputFileEnding());
        parts.add("typeSystemPath=" + duuiProps.typeSystemPath());
        parts.add("removeMetaInformation=true");
        parts.add("debugXmi=" + resolveBooleanProp("debugXmi", "DUUI_IMPORTER_DEBUG_XMI", false));
        parts.add("storeCoveredText=" + resolveBooleanProp("storeCoveredText", "DUUI_IMPORTER_STORE_COVERED_TEXT", false));

        String explicit = System.getenv("DUUI_IMPORTER_PIPELINE_HASH_EXTRA");

        if (explicit != null && !explicit.isBlank()) {
            parts.add("extra=" + explicit);
        }

        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(String.join("\n", parts));
    }

    private boolean resolveBooleanProp(String methodName, String envName, boolean defaultValue) {
        Object reflected = tryCallNoArg(methodName);

        if (reflected instanceof Boolean b) return b;
        if (reflected instanceof String s && !s.isBlank()) return Boolean.parseBoolean(s);

        String env = System.getenv(envName);

        if (env == null || env.isBlank()) return defaultValue;

        return Boolean.parseBoolean(env);
    }

    private int resolveIntProp(String methodName, String envName, int defaultValue) {
        Object reflected = tryCallNoArg(methodName);

        if (reflected instanceof Number n) return n.intValue();

        if (reflected instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        String env = System.getenv(envName);

        if (env == null || env.isBlank()) return defaultValue;

        try {
            return Integer.parseInt(env.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String resolveStringProp(String methodName, String envName, String defaultValue) {
        Object reflected = tryCallNoArg(methodName);

        if (reflected instanceof String s && !s.isBlank()) return s;

        String env = System.getenv(envName);

        if (env == null || env.isBlank()) return defaultValue;

        return env;
    }

    private Object tryCallNoArg(String methodName) {
        try {
            Method m = duuiProps.getClass().getMethod(methodName);
            return m.invoke(duuiProps);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
