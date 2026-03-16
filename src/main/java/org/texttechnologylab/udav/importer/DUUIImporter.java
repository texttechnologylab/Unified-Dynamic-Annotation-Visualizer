package org.texttechnologylab.udav.importer;

import lombok.RequiredArgsConstructor;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;
import org.dkpro.core.io.xmi.XmiWriter;
import org.junit.jupiter.api.DisplayName;
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
import java.net.URISyntaxException;

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
    private DUUIComposer composer;
    private TypeSystemDescription externalTypeSystem;

    private void init() throws IOException, URISyntaxException, UIMAException, SAXException {
        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();

        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(ctx)
                .withWorkers(duuiProps.workers());

        DUUIUIMADriver uimaDriver = new DUUIUIMADriver();
        DUUIDockerDriver dockerDriver = new DUUIDockerDriver();

        composer.addDriver(uimaDriver, dockerDriver);

        String tsPath = duuiProps.typeSystemPath();
        if (tsPath != null && !tsPath.isBlank()) {
            File tsFile = new File(tsPath);
            if (tsFile.isFile()) {
                LOGGER.info("Loading external type system from: {}", tsPath);
                TypeSystemDescription tsd = UIMAFramework.getXMLParser()
                        .parseTypeSystemDescription(new XMLInputSource(tsFile));
                tsd.resolveImports();

                composer.setInstantiatedTypeSystem(tsd);
                externalTypeSystem = tsd;
            } else {
                LOGGER.warn("DUUI_IMPORTER_TYPE_SYSTEM_PATH is set but file not found: {}", tsPath);
            }
        }
    }

    @DisplayName("NLP")
    public void execute() throws Exception {
        DUUIFileReaderLazy corpusReader =
                new DUUIFileReaderLazy(duuiProps.inputPath(), duuiProps.inputFileEnding(), 10);

        DUUIAsynchronousProcessor processor = new DUUIAsynchronousProcessor(corpusReader);

        // Docker NLP
//        composer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/textimager-duui-spacy-single-de_core_news_sm:0.1.4")
//                .withScale(duuiProps.workers())
//                .withImageFetching()
//                .build());

        // Cleanup
        composer.add(new DUUIUIMADriver.Component(
                createEngineDescription(RemoveMetaInformation.class, externalTypeSystem))
                .withScale(duuiProps.workers())
                .build());

        // Debug XMI is a major bottleneck; keep it OFF by default.
        // Enable by setting env DUUI_IMPORTER_DEBUG_XMI=true
        boolean debugXmi = Boolean.parseBoolean(System.getenv().getOrDefault("DUUI_IMPORTER_DEBUG_XMI", "false"));
        if (debugXmi) {
            String target = System.getenv().getOrDefault("DUUI_IMPORTER_DEBUG_XMI_PATH", "/tmp/export");
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
                    .withScale(1)   // do not scale disk writers
                    .build());
        }

        int dbScale = Math.max(1, Math.min(duuiProps.workers(), 4)); // start conservative; raise if DB can handle it
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
                        JooqDatabaseWriter.PARAM_SQL_DIALECT, db.getDialect()
                ))
                .withScale(dbScale)
                .build());

        composer.run(processor, "Importer");
        composer.shutdown();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        init();
        execute();
        postImportRowCounter.updateRowCounts();
    }
}
