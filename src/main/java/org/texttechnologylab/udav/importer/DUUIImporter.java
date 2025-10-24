package org.texttechnologylab.udav.importer;

import lombok.RequiredArgsConstructor;
import org.apache.uima.UIMAException;
import org.dkpro.core.io.xmi.XmiWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
import org.xml.sax.SAXException;
import org.texttechnologylab.udav.importer.config.DbProps;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "duui.importer", name = "enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class DUUIImporter implements ApplicationRunner {
    private static final int iWorkers = 2;
    private static DUUIComposer pComposer = null;
    private final DbProps db;
    private final PostImportRowCounter postImportRowCounter;

    @BeforeAll
    public static void init() throws IOException, URISyntaxException, UIMAException, SAXException {

        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();

        pComposer = new DUUIComposer()
                .withSkipVerification(true)     // wir überspringen die Verifikation aller Componenten =)
                .withLuaContext(ctx)            // wir setzen den definierten Kontext
                .withWorkers(iWorkers);         // wir geben dem Composer eine Anzahl an Threads mit.

        DUUIUIMADriver uima_driver = new DUUIUIMADriver();
        DUUIDockerDriver dockerDriver = new DUUIDockerDriver();

        // Hinzufügen der einzelnen Driver zum Composer
        pComposer.addDriver(uima_driver, dockerDriver);
    }

    @DisplayName("NLP")
    public void execute() throws Exception {

        DUUIFileReaderLazy pCorpusReader = new DUUIFileReaderLazy("./src/main/resources/input", ".xmi", 10);

        DUUIAsynchronousProcessor processor = new DUUIAsynchronousProcessor(pCorpusReader);

        pComposer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/textimager-duui-spacy-single-de_core_news_sm:0.1.4")
                .withScale(iWorkers)
                .withImageFetching()
                .build());

        // remove
        pComposer.add(new DUUIUIMADriver.Component(createEngineDescription(RemoveMetaInformation.class))
                .withScale(iWorkers)
                .build());

        // write into XMI for debugging purposes.
        pComposer.add(new DUUIUIMADriver.Component(createEngineDescription(XmiWriter.class,
                XmiWriter.PARAM_TARGET_LOCATION, "/tmp/export",
                XmiWriter.PARAM_PRETTY_PRINT, true,
                XmiWriter.PARAM_OVERWRITE, true,
                XmiWriter.PARAM_VERSION, "1.1",
                XmiWriter.PARAM_COMPRESSION, "GZIP"
        )).withScale(iWorkers).build());

        pComposer.add(new DUUIUIMADriver.Component(
                createEngineDescription(
                        JooqDatabaseWriter.class,
                        JooqDatabaseWriter.PARAM_JDBC_URL, db.getUrl(),
                        JooqDatabaseWriter.PARAM_DB_USER, db.getUser(),
                        JooqDatabaseWriter.PARAM_DB_PASS, db.getPass(),
                        JooqDatabaseWriter.PARAM_SCHEMA, db.getSchema(),
                        JooqDatabaseWriter.PARAM_BATCH_SIZE, db.getBatchSize(),
                        JooqDatabaseWriter.PARAM_MAX_IDENT, db.getMaxIdent(),
                        JooqDatabaseWriter.PARAM_SQL_DIALECT, db.getDialect()
                )).withScale(1).build());

        pComposer.run(processor, "Beispiel");

        pComposer.shutdown();


    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        init();
        execute();
        postImportRowCounter.updateRowCounts();
    }
}
