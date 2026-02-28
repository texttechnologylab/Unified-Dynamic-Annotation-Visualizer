package org.texttechnologylab.udav.importer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jooq.*;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.jooq.impl.DSL.*;

public class JooqDatabaseWriter extends JCasAnnotator_ImplBase {

    // --- Parameters ---
    public static final String PARAM_JDBC_URL = "jdbcUrl";
    public static final String PARAM_DB_USER = "dbUser";
    public static final String PARAM_DB_PASS = "dbPass";
    public static final String PARAM_SCHEMA = "dbSchema";
    public static final String PARAM_BATCH_SIZE = "batchSize";
    public static final String PARAM_MAX_IDENT = "maxIdentifierLength";
    public static final String PARAM_SQL_DIALECT = "sqlDialect"; // e.g., POSTGRES, MARIADB
    private static final Logger LOGGER = LoggerFactory.getLogger(JooqDatabaseWriter.class);
    // Primitive UIMA type names -> jOOQ DataType
    private static final Map<String, DataType<?>> UIMA_PRIMITIVE_TO_SQL = Map.of(
            "uima.cas.String", SQLDataType.CLOB,         // use TEXT/CLOB
            "uima.cas.Integer", SQLDataType.INTEGER,
            "uima.cas.Float", SQLDataType.REAL,
            "uima.cas.Double", SQLDataType.DOUBLE,
            "uima.cas.Boolean", SQLDataType.BOOLEAN,
            "uima.cas.Long", SQLDataType.BIGINT,
            "uima.cas.Short", SQLDataType.SMALLINT,
            "uima.cas.Byte", SQLDataType.SMALLINT      // no BYTE in SQL standard; SMALLINT suffices
    );
    // === HASH + NAMING HELPERS ===
    private static final int TABLE_HASH_LEN = 16; // tweak if you want shorter/longer hashes
    // cache to avoid checking repeatedly in the same JVM
    private static final Set<String> seenTsFingerprints = ConcurrentHashMap.newKeySet();
    // Map <uimaTypeName -> tableName>
    private final Map<String, String> typeToTable = new ConcurrentHashMap<>();
    // Which tables are created
    private final Set<String> createdTables = ConcurrentHashMap.newKeySet();
    @ConfigurationParameter(name = PARAM_SCHEMA, mandatory = false, defaultValue = "public")
    private String schema;
    @ConfigurationParameter(name = PARAM_BATCH_SIZE, mandatory = false, defaultValue = "1000")
    private int batchSize;
    // 63 for Postgres; change if other RDBMS
    @ConfigurationParameter(name = PARAM_MAX_IDENT, mandatory = false, defaultValue = "63")
    private int maxIdentifierLength;
    private DSLContext dsl;
    // Guards so we only build schema/registry once per JVM
    private volatile boolean schemaEnsured = false;
    private HikariDataSource dataSource;

    private static Iterable<Type> iterable(Iterator<Type> it) {
        List<Type> out = new ArrayList<>();
        while (it.hasNext()) out.add(it.next());
        return out;
    }

    private static DocumentMetaData getOrCreateDocumentMeta(JCas jCas) {
        DocumentMetaData md;
        try {
            md = DocumentMetaData.get(jCas);
        } catch (IllegalArgumentException e) {
            md = new DocumentMetaData(jCas);
            md.setDocumentId(UUID.randomUUID().toString());
            md.setDocumentTitle("unknown");
            md.setDocumentUri(null);
            md.addToIndexes();
        }
        return md;
    }

    @SafeVarargs
    private static <T> T safe(T v, Supplier<T>... fallbacks) {
        if (v != null && !(v instanceof String s && s.isBlank())) return v;
        for (Supplier<T> fb : fallbacks) {
            T t = fb.get();
            if (t != null && (!(t instanceof String) || !((String) t).isBlank())) return t;
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static SQLDialect resolveDialect(String explicit, String url) throws ResourceInitializationException {
        if (!isBlank(explicit)) {
            try {
                return SQLDialect.valueOf(explicit.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ResourceInitializationException(e);
            }
        }
        if (!isBlank(url)) {
            String u = url.toLowerCase(Locale.ROOT);
            if (u.startsWith("jdbc:postgresql:")) return SQLDialect.POSTGRES;
            if (u.startsWith("jdbc:h2:")) return SQLDialect.H2;
            if (u.startsWith("jdbc:mysql:")) return SQLDialect.MYSQL;
            if (u.startsWith("jdbc:mariadb:")) return SQLDialect.MARIADB;
            if (u.startsWith("jdbc:sqlite:")) return SQLDialect.SQLITE;
            if (u.startsWith("jdbc:duckdb:")) return SQLDialect.DUCKDB;
            if (u.startsWith("jdbc:derby:")) return SQLDialect.DERBY;
        }
        return SQLDialect.DEFAULT;
    }

    private static void mdUpdate(MessageDigest md, String s) {
        md.update(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);

        String jdbcUrl = (String) context.getConfigParameterValue(PARAM_JDBC_URL);
        String dbUser = (String) context.getConfigParameterValue(PARAM_DB_USER);
        String dbPass = (String) context.getConfigParameterValue(PARAM_DB_PASS);
        this.schema = (String) context.getConfigParameterValue(PARAM_SCHEMA);
        this.batchSize = (Integer) context.getConfigParameterValue(PARAM_BATCH_SIZE);
        this.maxIdentifierLength = (Integer) context.getConfigParameterValue(PARAM_MAX_IDENT);
        // Default POSTGRES; alternatives: MARIADB, MYSQL, H2, etc.
        String sqlDialectName = (String) context.getConfigParameterValue(PARAM_SQL_DIALECT);

        if (isBlank(jdbcUrl)) {
            throw new ResourceInitializationException(
                    new IllegalArgumentException("JooqDatabaseWriter: jdbcUrl missing. Provide UIMA params or Spring properties (spring.datasource.url)."));
        }

        SQLDialect dialect = resolveDialect(sqlDialectName, jdbcUrl);
        this.schema = normalizeSchemaForDialect(this.schema, dialect);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(dbUser);
        cfg.setPassword(dbPass);
        cfg.setMaximumPoolSize(8);
        cfg.setMinimumIdle(1);
        cfg.setAutoCommit(true);
        // --- Infra ---
        cfg.setPoolName("JooqWriterPool"); // optional, makes thread names obvious
        try {
            this.dataSource = new HikariDataSource(cfg);
        } catch (IllegalArgumentException e) {
            throw new ResourceInitializationException(e);
        }

        this.dsl = DSL.using(this.dataSource, dialect,
                new Settings().withRenderQuotedNames(RenderQuotedNames.ALWAYS));

        ensureRegistryTables();
    }

    // Create registry + helper structures (portable jOOQ DDL)
    // Create registry + helper structures (portable jOOQ DDL)
    private void ensureRegistryTables() {
        if (schemaEnsured) return;

        dsl.createSchemaIfNotExists(DSL.name(schema)).execute();

        // registry of UIMA type -> table_name
        dsl.createTableIfNotExists(DSL.name(schema, "uima_type_registry"))
                .column("id", SQLDataType.BIGINT.identity(true))
                .column("uima_type_uri", SQLDataType.CLOB.nullable(false))
                .column("table_name", SQLDataType.CLOB.nullable(false))
                .column("row_count", SQLDataType.BIGINT.defaultValue(0L))
                .column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(DSL.currentOffsetDateTime()))
                .constraints(
                        DSL.constraint(DSL.name(cut("pk_uima_type_registry"))).primaryKey("id"),
                        DSL.constraint(DSL.name(cut("uq_type_uri"))).unique("uima_type_uri"),
                        DSL.constraint(DSL.name(cut("uq_table_name"))).unique("table_name")
                )
                .execute();

        // documents (unchanged in structure you already have)
        dsl.createTableIfNotExists(DSL.name(schema, "documents"))
                .column("doc_id", SQLDataType.CLOB.nullable(false))
                .column("uri", SQLDataType.CLOB.nullable(true))
                .column("language", SQLDataType.CLOB.nullable(true))
                .column("content_hash", SQLDataType.VARCHAR(64).nullable(true))
                .column("ts_hash", SQLDataType.VARCHAR(64).nullable(true))
                .constraints(DSL.constraint(DSL.name(cut("pk_documents"))).primaryKey("doc_id"))
                .execute();

        dsl.createTableIfNotExists(DSL.name(schema, "type_system_fingerprints"))
                .column("ts_hash", SQLDataType.CLOB.nullable(false))
                .column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(DSL.currentOffsetDateTime()))
                .constraints(DSL.constraint(DSL.name(cut("pk_ts_fingerprint"))).primaryKey("ts_hash"))
                .execute();

        // NEW: per-document, per-view SOFA store (metadata + text)
        dsl.createTableIfNotExists(DSL.name(schema, "sofas"))
                .column("doc_id", SQLDataType.CLOB.nullable(false))
                .column("sofa_id", SQLDataType.VARCHAR(128).nullable(false))
                .column("sofa_num", SQLDataType.INTEGER.nullable(true))
                .column("mime_type", SQLDataType.CLOB.nullable(true))
                .column("sofa_uri", SQLDataType.CLOB.nullable(true))
                .column("sofa_string", SQLDataType.CLOB.nullable(true))
                .column("sofa_hash", SQLDataType.VARCHAR(64).nullable(true))
                .column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(DSL.currentOffsetDateTime()))
                .constraints(
                        DSL.constraint(DSL.name(cut("pk_sofas"))).primaryKey("doc_id", "sofa_id")
                )
                .execute();

        // helpful index for lookups
        dsl.createIndexIfNotExists(DSL.name(cut("idx_sofas_doc_id")))
                .on(DSL.table(DSL.name(schema, "sofas")), DSL.field(DSL.name("doc_id")))
                .execute();

        // Migration: add row_count column if it doesn't exist (for existing tables)
        ensureRowCountColumn();

        schemaEnsured = true;
    }

    private void ensureRowCountColumn() {
        try {
            // Check if row_count column exists by trying to select from it
            dsl.select(field(name("row_count")))
                    .from(table(name(schema, "uima_type_registry")))
                    .limit(1)
                    .fetch();
        } catch (DataAccessException e) {
            // Column doesn't exist, add it
            if (e.getMessage() != null && e.getMessage().contains("column") && e.getMessage().contains("does not exist")) {
                try {
                    String alterSql = String.format("ALTER TABLE \"%s\".\"uima_type_registry\" ADD COLUMN \"row_count\" BIGINT DEFAULT 0", schema);
                    dsl.execute(alterSql);
                } catch (DataAccessException ignoreDuplicate) {
                }
            }
        }
    }

    private String toSafeTableName(String uimaTypeName) {
        return tableHash(uimaTypeName);
    }

    @Override
    public void process(JCas jCas) {
        TypeSystem ts = jCas.getTypeSystem();
        ensureTablesForTypeSystem(ts);

        DocumentMetaData md = getOrCreateDocumentMeta(jCas);
        String docId = safe(md.getDocumentId(), md::getDocumentUri, md::getDocumentTitle,
                () -> UUID.randomUUID().toString());
        String uri = safe(md.getDocumentUri(), md::getDocumentTitle, () -> docId);
        String lang = jCas.getDocumentLanguage();

        String tsHash = computeTypeSystemHash(ts);
        String contentHash = computeDocumentContentHash(jCas, ts);

        // If unchanged, still ensure FILES mapping and also SOFAs (idempotent upsert)
        if (isDocumentUpToDate(docId, tsHash, contentHash)) {
            collectAndUpsertSofas(jCas, docId);  // cheap no-op if already present
            return;
        }

        // Update / insert the document row with the new fingerprints
        upsertDocument(docId, uri, lang, tsHash, contentHash);

        // Persist all SOFAs for this document up-front
        collectAndUpsertSofas(jCas, docId);

        // === Normal per-type write, but with row_hash + ON CONFLICT DO NOTHING + sofa_id ===
        for (Type t : iterable(ts.getTypeIterator())) {
            if (isSkippableType(t)) continue;

            String tableNameHash = typeToTable.get(t.getName());
            if (tableNameHash == null) continue;

            String colRowHash = sysColName(tableNameHash, "row_hash");
            String colDocId = sysColName(tableNameHash, "doc_id");
            String colSofaId = sysColName(tableNameHash, "sofa_id");   // NEW
            String colBegin = sysColName(tableNameHash, "fs_begin");
            String colEnd = sysColName(tableNameHash, "fs_end");
            String colText = sysColName(tableNameHash, "covered_text");
            String colFsJson = sysColName(tableNameHash, "fs_json");

            boolean isAnno = ts.subsumes(ts.getType("uima.tcas.Annotation"), t);

            if (isAnno) {
                var idx = jCas.getCas().getAnnotationIndex(t);
                if (idx == null || idx.isEmpty()) continue;

                // Get document text length for bounds checking
                String docText = jCas.getDocumentText();
                int docLength = docText != null ? docText.length() : 0;

                List<Query> batch = new ArrayList<>(Math.min(idx.size(), batchSize));
                for (AnnotationFS fs : idx) {
                    String sofaId = sofaIdForFs(fs);

                    Map<Field<?>, Object> values = new LinkedHashMap<>();
                    values.put(field(name(colRowHash)), computeRowHash(tableNameHash, docId, ts, t, fs));
                    values.put(field(name(colDocId)), docId);
                    values.put(field(name(colSofaId)), sofaId);
                    values.put(field(name(colBegin)), fs.getBegin());
                    values.put(field(name(colEnd)), fs.getEnd());

                    // Safely extract covered text with bounds checking
                    int begin = fs.getBegin();
                    int end = fs.getEnd();
                    String coveredText = safeCoveredText(docText, docLength, begin, end);
                    values.put(field(name(colText)), coveredText);

                    for (Feature f : t.getFeatures()) {
                        if (!isPrimitive(f)) continue;
                        String col = featColName(tableNameHash, f);
                        values.putIfAbsent(field(name(col)), FeatureJsonSerializer.readPrimitive(fs, f));
                    }
                    values.put(field(name(colFsJson)), FeatureJsonSerializer.toJson(fs));

                    batch.add(insertIgnore(tableNameHash, values, colRowHash));
                    if (batch.size() >= batchSize) {
                        try {
                            dsl.batch(batch).execute();
                        } catch (Exception e) {
                            LOGGER.error("Error executing batch insert for table {}: {}", tableNameHash, e.getMessage());
                            // Continue with next batch instead of failing entire document
                        }
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    try {
                        dsl.batch(batch).execute();
                    } catch (Exception e) {
                        LOGGER.error("Error executing final batch insert for table {}: {}", tableNameHash, e.getMessage());
                    }
                }

            } else {
                var it = jCas.getCas().getIndexRepository().getAllIndexedFS(t);
                if (it == null) continue;

                List<Query> batch = new ArrayList<>(batchSize);
                it.forEachRemaining(fs -> {
                    String sofaId = sofaIdForFs(fs);

                    Map<Field<?>, Object> values = new LinkedHashMap<>();
                    values.put(field(name(colRowHash)), computeRowHash(tableNameHash, docId, ts, t, fs));
                    values.put(field(name(colDocId)), docId);
                    values.put(field(name(colSofaId)), sofaId);

                    for (Feature f : t.getFeatures()) {
                        if (isPrimitive(f)) {
                            String col = featColName(tableNameHash, f);
                            values.put(field(name(col)), FeatureJsonSerializer.readPrimitive(fs, f));
                        }
                    }
                    values.put(field(name(colFsJson)), FeatureJsonSerializer.toJson(fs));

                    batch.add(insertIgnore(tableNameHash, values, colRowHash));
                    if (batch.size() >= batchSize) {
                        try {
                            dsl.batch(batch).execute();
                        } catch (Exception e) {
                            LOGGER.error("Error executing batch insert for table {}: {}", tableNameHash, e.getMessage());
                            // Continue with next batch instead of failing entire document
                        }
                        batch.clear();
                    }
                });
                if (!batch.isEmpty()) {
                    try {
                        dsl.batch(batch).execute();
                    } catch (Exception e) {
                        LOGGER.error("Error executing final batch insert for table {}: {}", tableNameHash, e.getMessage());
                    }
                }
            }
        }
    }

    private synchronized void ensureTablesForTypeSystem(TypeSystem ts) {
        String tsHash = computeTypeSystemHash(ts);

        if (seenTsFingerprints.contains(tsHash)) return;

        if (fingerprintExists(tsHash)) {
            preloadTypeToTableFromRegistry(ts);
            seenTsFingerprints.add(tsHash);
            return;
        }

        for (Type t : iterable(ts.getTypeIterator())) {
            if (isSkippableType(t)) continue;

            String uimaType = t.getName();
            String tableNameHash = typeToTable.computeIfAbsent(uimaType, this::toSafeTableName);
            if (createdTables.contains(tableNameHash)) continue;

            String pkCol = pkColName(tableNameHash);
            String colRowH = sysColName(tableNameHash, "row_hash");
            String colDoc = sysColName(tableNameHash, "doc_id");
            String colSofa = sysColName(tableNameHash, "sofa_id");     // NEW
            String colBegin = sysColName(tableNameHash, "fs_begin");
            String colEnd = sysColName(tableNameHash, "fs_end");
            String colText = sysColName(tableNameHash, "covered_text");
            String colFsJs = sysColName(tableNameHash, "fs_json");

            List<Field<?>> cols = new ArrayList<>();
            cols.add(field(name(pkCol), SQLDataType.BIGINT.identity(true)));
            cols.add(field(name(colRowH), SQLDataType.VARCHAR(64).nullable(false)));
            cols.add(field(name(colDoc), SQLDataType.CLOB.nullable(false)));
            cols.add(field(name(colSofa), SQLDataType.VARCHAR(128).nullable(false))); // NEW

            boolean isAnno = ts.subsumes(ts.getType("uima.tcas.Annotation"), t);
            if (isAnno) {
                cols.add(field(name(colBegin), SQLDataType.INTEGER.nullable(false)));
                cols.add(field(name(colEnd), SQLDataType.INTEGER.nullable(false)));
                cols.add(field(name(colText), SQLDataType.CLOB.nullable(true)));
            }

            for (Feature f : t.getFeatures()) {
                if (!isPrimitive(f)) continue;
                DataType<?> dt = mapPrimitiveType(f.getRange().getName());
                cols.add(field(name(featColName(tableNameHash, f)), dt.nullable(true)));
            }

            cols.add(field(name(colFsJs), SQLDataType.JSON.nullable(true)));

            dsl.createTableIfNotExists(name(schema, tableNameHash))
                    .columns(cols)
                    .constraints(
                            constraint(name(cut("pk_" + tableNameHash))).primaryKey(field(name(pkCol))),
                            constraint(name(cut("uq_" + tableNameHash + "_rowhash"))).unique(field(name(colRowH)))
                    )
                    .execute();

            // composite indexes include sofa_id for cheap WHERE doc_id + view filters
            if (isAnno) {
                dsl.createIndexIfNotExists(name(cut("idx_" + tableNameHash + "_doc_sofa_begin")))
                        .on(table(name(schema, tableNameHash)),
                                field(name(colDoc)), field(name(colSofa)), field(name(colBegin)))
                        .execute();
            } else {
                dsl.createIndexIfNotExists(name(cut("idx_" + tableNameHash + "_doc_sofa")))
                        .on(table(name(schema, tableNameHash)),
                                field(name(colDoc)), field(name(colSofa)))
                        .execute();
            }

            upsertTypeRegistry(uimaType, tableNameHash);
            createdTables.add(tableNameHash);
        }

        insertFingerprint(tsHash);
        seenTsFingerprints.add(tsHash);
    }

    private boolean isSkippableType(Type t) {
        String n = t.getName();
        // Skip CAS internals and FeatureStructures with no features
        return n.startsWith("uima.cas.") && !n.equals("uima.tcas.Annotation");
    }

    private boolean isPrimitive(Feature f) {
        String rn = f.getRange().getName();
        return UIMA_PRIMITIVE_TO_SQL.containsKey(rn);
    }

    private DataType<?> mapPrimitiveType(String rangeName) {
        return UIMA_PRIMITIVE_TO_SQL.getOrDefault(rangeName, SQLDataType.CLOB);
    }

    private String cut(String s) {
        return s.length() <= maxIdentifierLength ? s : s.substring(0, maxIdentifierLength);
    }

    private void upsertDocument(String docId, String uri, String lang, String tsHash, String contentHash) {
        switch (dsl.dialect().family()) {
            case POSTGRES:
                dsl.insertInto(table(name(schema, "documents")))
                        .columns(field("doc_id"), field("uri"), field("language"), field("ts_hash"), field("content_hash"))
                        .values(docId, uri, lang, tsHash, contentHash)
                        .onConflict(field("doc_id")).doUpdate()
                        .set(field("uri"), uri)
                        .set(field("language"), lang)
                        .set(field("ts_hash"), tsHash)
                        .set(field("content_hash"), contentHash)
                        .execute();
                break;
            case H2:
            case MARIADB:
            case MYSQL:
            case DUCKDB:
            case SQLITE:
                dsl.mergeInto(table(name(schema, "documents")))
                        .usingDual()
                        .on(field("doc_id").eq(docId))
                        .whenMatchedThenUpdate()
                        .set(field("uri"), uri)
                        .set(field("language"), lang)
                        .set(field("ts_hash"), tsHash)
                        .set(field("content_hash"), contentHash)
                        .whenNotMatchedThenInsert(field("doc_id"), field("uri"), field("language"), field("ts_hash"), field("content_hash"))
                        .values(docId, uri, lang, tsHash, contentHash)
                        .execute();
                break;
            default:
                try {
                    dsl.insertInto(table(name(schema, "documents")))
                            .columns(field("doc_id"), field("uri"), field("language"), field("ts_hash"), field("content_hash"))
                            .values(docId, uri, lang, tsHash, contentHash)
                            .execute();
                } catch (DataAccessException ignoreDup) { /* ignore */ }
        }
    }

    private void upsertTypeRegistry(String uimaType, String tableNameHash) {
        switch (dsl.dialect().family()) {
            case POSTGRES:
                dsl.insertInto(table(name(schema, "uima_type_registry")))
                        .columns(field("uima_type_uri"), field("table_name"))
                        .values(uimaType, tableNameHash)
                        .onConflict(field("uima_type_uri")).doUpdate()
                        .set(field("table_name"), tableNameHash)
                        .execute();
                break;
            case H2:
            case MARIADB:
            case MYSQL:
            case DUCKDB:
            case SQLITE:
                dsl.mergeInto(table(name(schema, "uima_type_registry")))
                        .usingDual()
                        .on(field("uima_type_uri").eq(uimaType))
                        .whenMatchedThenUpdate()
                        .set(field("table_name"), tableNameHash)
                        .whenNotMatchedThenInsert(field("uima_type_uri"), field("table_name"))
                        .values(uimaType, tableNameHash)
                        .execute();
                break;
            default:
                try {
                    dsl.insertInto(table(name(schema, "uima_type_registry")))
                            .columns(field("uima_type_uri"), field("table_name"))
                            .values(uimaType, tableNameHash)
                            .execute();
                } catch (DataAccessException ignoreDup) { /* ignore */ }
        }

        typeToTable.put(uimaType, tableNameHash);
    }

    private String normalizeSchemaForDialect(String schema, SQLDialect dialect) {
        String s = (schema == null || schema.isBlank()) ? "public" : schema;
        if (dialect.family() == SQLDialect.H2 && "public".equalsIgnoreCase(s)) return "PUBLIC";
        if (dialect.family() == SQLDialect.POSTGRES) return s.toLowerCase(Locale.ROOT);
        return s;
    }

    private String sanitizeIdent(String s) {
        return (s == null ? "" : s.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT));
    }

    private String tableHash(String uimaTypeName) {
        String h = DigestUtils.sha256Hex(uimaTypeName).toLowerCase(Locale.ROOT);
        return cut(h.substring(0, Math.min(TABLE_HASH_LEN, h.length())));
    }

    private String pkColName(String tableHash) {
        // keep distinct suffix to avoid any collision with feature "id"
        return cut(tableHash + "_row_id");
    }

    private String sysColName(String tableHash, String base) {
        return cut(tableHash + "_" + sanitizeIdent(base));
    }

    private String featColName(String tableHash, Feature f) {
        String base = sanitizeIdent(f.getShortName() != null ? f.getShortName() : f.getName());
        // add _f_ to guarantee no clash with system columns like doc_id, fs_begin, ...
        return cut(tableHash + "_f_" + base);
    }

    private String computeTypeSystemHash(TypeSystem ts) {
        // Normalize order so hash is stable
        List<String> parts = new ArrayList<>();
        for (Type t : iterable(ts.getTypeIterator())) {
            if (isSkippableType(t)) continue;
            boolean isAnno = ts.subsumes(ts.getType("uima.tcas.Annotation"), t);
            List<String> feats = new ArrayList<>();
            for (Feature f : t.getFeatures()) {
                String rn = f.getRange().getName();
                if (!UIMA_PRIMITIVE_TO_SQL.containsKey(rn)) continue;
                String fname = (f.getShortName() != null ? f.getShortName() : f.getName());
                feats.add(fname + ":" + rn);
            }
            Collections.sort(feats);
            parts.add(t.getName() + "|" + (isAnno ? "A" : "F") + "|" + String.join(",", feats));
        }
        Collections.sort(parts);
        String joined = String.join("\n", parts);
        return DigestUtils.sha256Hex(joined);
    }

    private boolean fingerprintExists(String tsHash) {
        Integer cnt = dsl.selectCount()
                .from(table(name(schema, "type_system_fingerprints")))
                .where(field("ts_hash").eq(tsHash))
                .fetchOne(0, Integer.class);
        return cnt != null && cnt > 0;
    }

    private void insertFingerprint(String tsHash) {
        if (dsl.dialect().family() == SQLDialect.POSTGRES) {
            dsl.insertInto(table(name(schema, "type_system_fingerprints")))
                    .columns(field("ts_hash"))
                    .values(tsHash)
                    .onConflict(field("ts_hash")).doNothing()
                    .execute();
        } else {
            try {
                dsl.insertInto(table(name(schema, "type_system_fingerprints")))
                        .columns(field("ts_hash"))
                        .values(tsHash)
                        .execute();
            } catch (DataAccessException ignoreDup) { /* ignore */ }
        }
    }

    private void preloadTypeToTableFromRegistry(TypeSystem ts) {
        // Pull known mappings for current TS from uima_type_registry
        List<String> typeNames = new ArrayList<>();
        for (Type t : iterable(ts.getTypeIterator())) {
            if (!isSkippableType(t)) typeNames.add(t.getName());
        }
        if (typeNames.isEmpty()) return;

        var r = dsl.select(field("uima_type_uri", String.class), field("table_name", String.class))
                .from(table(name(schema, "uima_type_registry")))
                .where(field("uima_type_uri").in(typeNames))
                .fetch();

        for (var rec : r) {
            String uri = rec.get(0, String.class);
            String tbl = rec.get(1, String.class);
            if (uri != null && tbl != null) typeToTable.put(uri, tbl);
        }
    }

    // Add these imports if you don't have them:
// import java.nio.charset.StandardCharsets;
// import java.security.MessageDigest;
// import java.security.NoSuchAlgorithmException;

    private boolean isDocumentUpToDate(String docId, String tsHash, String contentHash) {
        var rec = dsl.select(field("ts_hash", String.class), field("content_hash", String.class))
                .from(table(name(schema, "documents")))
                .where(field("doc_id").eq(docId))
                .fetchOne();
        if (rec == null) return false;
        String tsH = rec.get(0, String.class);
        String cH = rec.get(1, String.class);
        return Objects.equals(tsHash, tsH) && Objects.equals(contentHash, cH);
    }

    private String computeDocumentContentHash(JCas jCas, TypeSystem ts) {
        // Stable order: by type name; within type, rely on index order (stable across identical CAS)
        MessageDigest md = DigestUtils.getSha256Digest();
        List<String> typeNames = new ArrayList<>();
        for (Type t : iterable(ts.getTypeIterator())) if (!isSkippableType(t)) typeNames.add(t.getName());
        Collections.sort(typeNames);

        // Get document text length for bounds checking
        String docText = jCas.getDocumentText();
        int docLength = docText != null ? docText.length() : 0;

        for (String typeName : typeNames) {
            Type t = ts.getType(typeName);
            String tableHash = tableHash(typeName);
            md.update(("T|" + typeName + "|" + tableHash + "\n").getBytes(StandardCharsets.UTF_8));

            boolean isAnno = ts.subsumes(ts.getType("uima.tcas.Annotation"), t);
            if (isAnno) {
                var idx = jCas.getCas().getAnnotationIndex(t);
                if (idx != null && !idx.isEmpty()) {
                    for (AnnotationFS fs : idx) {
                        int begin = fs.getBegin();
                        int end = fs.getEnd();

                        md.update(("A|" + begin + "|" + end + "|").getBytes(StandardCharsets.UTF_8));

                        // Safely get covered text with bounds checking
                        String ct = safeCoveredText(docText, docLength, begin, end);
                        if (ct != null) md.update(ct.getBytes(StandardCharsets.UTF_8));

                        // primitive features (name=value)
                        for (Feature f : t.getFeatures()) {
                            if (!isPrimitive(f)) continue;
                            Object v = FeatureJsonSerializer.readPrimitive(fs, f);
                            if (v == null) continue;
                            md.update(("|" + f.getShortName() + "=" + v).getBytes(StandardCharsets.UTF_8));
                        }
                        md.update((byte) '\n');
                    }
                }
            } else {
                var it = jCas.getCas().getIndexRepository().getAllIndexedFS(t);
                if (it != null) {
                    it.forEachRemaining(fs -> {
                        md.update("F|".getBytes(StandardCharsets.UTF_8));
                        for (Feature f : t.getFeatures()) {
                            if (!isPrimitive(f)) continue;
                            Object v = FeatureJsonSerializer.readPrimitive(fs, f);
                            if (v == null) continue;
                            md.update(("|" + f.getShortName() + "=" + v).getBytes(StandardCharsets.UTF_8));
                        }
                        md.update((byte) '\n');
                    });
                }
            }
        }
        return DigestUtils.sha256Hex(md.digest());
    }

    private String safeCoveredText(String docText, int docLength, int begin, int end) {
        try {
            // Bounds checking: ensure indices are valid
            if (begin < 0 || end > docLength || begin > end) {
                // Invalid span; return null (skip this covered text)
                return null;
            }

            // Safe to extract substring
            if (docText != null) {
                return docText.substring(begin, end);
            }
            return null;
        } catch (StringIndexOutOfBoundsException e) {
            // Fallback: shouldn't happen with checks above, but handle gracefully
            return null;
        }
    }

    private String computeRowHash(String tableNameHash,
                                  String docId,
                                  TypeSystem ts,
                                  Type t,
                                  org.apache.uima.cas.FeatureStructure fs) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            // Should never happen; fall back to Apache DigestUtils on a concatenated string
            StringBuilder sb = new StringBuilder(256);
            sb.append("tbl=").append(tableNameHash).append('|');
            sb.append("type=").append(t.getName()).append('|');
            sb.append("doc=").append(docId == null ? "" : docId).append('|');
            // view (best-effort)
            try {
                String viewName = (fs instanceof AnnotationFS a) ? a.getView().getViewName() : fs.getCAS().getViewName();
                if (viewName != null) sb.append("view=").append(viewName).append('|');
            } catch (Exception ignore) {
            }
            // begin/end + covered text if annotation
            try {
                if (ts.subsumes(ts.getType("uima.tcas.Annotation"), t) && fs instanceof AnnotationFS a) {
                    sb.append("b=").append(a.getBegin()).append('|');
                    sb.append("e=").append(a.getEnd()).append('|');
                    try {
                        String docText = a.getView().getDocumentText();
                        if (docText != null) {
                            int b = a.getBegin(), e = a.getEnd();
                            if (0 <= b && b <= e && e <= docText.length()) {
                                sb.append("ct=").append(docText, b, e).append('|');
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
            } catch (Exception ignore) {
            }

            // primitive features in stable order
            List<Feature> feats = new ArrayList<>();
            for (Feature f : t.getFeatures()) {
                if (UIMA_PRIMITIVE_TO_SQL.containsKey(f.getRange().getName())) feats.add(f);
            }
            feats.sort(Comparator.comparing(f -> {
                String s = f.getShortName();
                return s != null ? s : f.getName();
            }));

            for (Feature f : feats) {
                String fname = f.getShortName() != null ? f.getShortName() : f.getName();
                Object v = FeatureJsonSerializer.readPrimitive(fs, f);
                sb.append("f=").append(fname).append('=').append(v).append('|');
            }

            return DigestUtils.sha256Hex(sb.toString());
        }

        // Streaming / low-GC path
        mdUpdate(md, "tbl=" + tableNameHash + "|");
        mdUpdate(md, "type=" + t.getName() + "|");
        mdUpdate(md, "doc=" + (docId == null ? "" : docId) + "|");

        // view (best-effort; swallow if not available)
        try {
            String viewName = (fs instanceof AnnotationFS a) ? a.getView().getViewName() : fs.getCAS().getViewName();
            if (viewName != null) mdUpdate(md, "view=" + viewName + "|");
        } catch (Exception ignore) {
        }

        // begin/end + covered text if annotation
        try {
            if (ts.subsumes(ts.getType("uima.tcas.Annotation"), t) && fs instanceof AnnotationFS a) {
                mdUpdate(md, "b=" + a.getBegin() + "|");
                mdUpdate(md, "e=" + a.getEnd() + "|");
                try {
                    String docText = a.getView().getDocumentText();
                    if (docText != null) {
                        int b = a.getBegin(), e = a.getEnd();
                        if (0 <= b && b <= e && e <= docText.length()) {
                            mdUpdate(md, "ct=");
                            // feed substring directly to the digest (avoid building huge temp strings)
                            md.update(docText.substring(b, e).getBytes(StandardCharsets.UTF_8));
                            mdUpdate(md, "|");
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        } catch (Exception ignore) {
        }

        // primitive features in stable order
        List<Feature> feats = new ArrayList<>();
        for (Feature f : t.getFeatures()) {
            if (UIMA_PRIMITIVE_TO_SQL.containsKey(f.getRange().getName())) feats.add(f);
        }
        feats.sort(Comparator.comparing(f -> {
            String s = f.getShortName();
            return s != null ? s : f.getName();
        }));

        for (Feature f : feats) {
            String fname = f.getShortName() != null ? f.getShortName() : f.getName();
            Object v = FeatureJsonSerializer.readPrimitive(fs, f);
            mdUpdate(md, "f=" + fname + "=" + v + "|");
        }

        return DigestUtils.sha256Hex(md.digest());
    }

    private Query insertIgnore(String tableNameHash, Map<Field<?>, Object> values, String colRowHash) {
        return switch (dsl.dialect().family()) {
            case POSTGRES, SQLITE -> dsl.insertInto(table(name(schema, tableNameHash)))
                    .set(values)
                    .onConflict(field(name(colRowHash))).doNothing();
            case MARIADB, MYSQL -> dsl.insertInto(table(name(schema, tableNameHash)))
                    .set(values)
                    .onDuplicateKeyIgnore();
            default ->
                // Fallback: plain insert (duplicates will error; but doc-level skip makes that unlikely)
                    dsl.insertInto(table(name(schema, tableNameHash))).set(values);
        };
    }

    // Persist all views (SOFAs) for a document (idempotent upsert)
    private void collectAndUpsertSofas(JCas jCas, String docId) {
        org.apache.uima.cas.CAS base = jCas.getCas();
        for (java.util.Iterator<org.apache.uima.cas.CAS> it = base.getViewIterator(); it.hasNext(); ) {
            org.apache.uima.cas.CAS view = it.next();

            String sofaId = null;
            Integer sofaNum = null;
            String mime = null, uri = null, text = null;

            // 1) try SofaID + metadata
            try {
                var sfs = view.getSofa();
                if (sfs != null) {
                    sofaId = emptyToNull(sfs.getSofaID());
                    mime = sfs.getSofaMime();
                    uri = sfs.getSofaURI();
                    try {
                        sofaNum = sfs.getSofaNum();
                    } catch (Throwable ignore) {
                    }
                }
            } catch (Throwable ignore) {
            }

            // 2) fallback to view name
            if (sofaId == null) {
                try {
                    sofaId = emptyToNull(view.getViewName());
                } catch (Throwable ignore) {
                }
            }

            // 3) final fallback
            if (sofaId == null) sofaId = "_InitialView";

            try {
                text = view.getDocumentText();
            } catch (Throwable ignore) {
            }
            String textHash = org.apache.commons.codec.digest.DigestUtils.sha256Hex(text == null ? "" : text);

            upsertSofa(docId, sofaId, sofaNum, mime, uri, text, textHash);
        }
    }

    // Single upsert into SOFAS
    private void upsertSofa(String docId, String sofaId, Integer sofaNum, String mime, String uri, String text, String textHash) {
        Table<?> tbl = table(name(schema, "sofas"));
        Field<Object> fDoc = field(name("doc_id"));
        Field<Object> fId = field(name("sofa_id"));
        Field<Object> fNum = field(name("sofa_num"));
        Field<Object> fMime = field(name("mime_type"));
        Field<Object> fUri = field(name("sofa_uri"));
        Field<Object> fStr = field(name("sofa_string"));
        Field<Object> fHash = field(name("sofa_hash"));

        switch (dsl.dialect().family()) {
            case POSTGRES:
                dsl.insertInto(tbl)
                        .columns(fDoc, fId, fNum, fMime, fUri, fStr, fHash)
                        .values(docId, sofaId, sofaNum, mime, uri, text, textHash)
                        .onConflict(fDoc, fId).doUpdate()
                        .set(fNum, sofaNum)
                        .set(fMime, mime)
                        .set(fUri, uri)
                        .set(fStr, text)
                        .set(fHash, textHash)
                        .execute();
                break;
            case MARIADB:
            case MYSQL:
            case H2:
            case DUCKDB:
            case SQLITE:
                dsl.mergeInto(tbl)
                        .usingDual()
                        .on(fDoc.eq(docId).and(fId.eq(sofaId)))
                        .whenMatchedThenUpdate()
                        .set(fNum, sofaNum)
                        .set(fMime, mime)
                        .set(fUri, uri)
                        .set(fStr, text)
                        .set(fHash, textHash)
                        .whenNotMatchedThenInsert(fDoc, fId, fNum, fMime, fUri, fStr, fHash)
                        .values(docId, sofaId, sofaNum, mime, uri, text, textHash)
                        .execute();
                break;
            default:
                try {
                    dsl.insertInto(tbl)
                            .columns(fDoc, fId, fNum, fMime, fUri, fStr, fHash)
                            .values(docId, sofaId, sofaNum, mime, uri, text, textHash)
                            .execute();
                } catch (DataAccessException ignoreDup) {
                    // fallback: update
                    dsl.update(tbl)
                            .set(fNum, sofaNum)
                            .set(fMime, mime)
                            .set(fUri, uri)
                            .set(fStr, text)
                            .set(fHash, textHash)
                            .where(fDoc.eq(docId).and(fId.eq(sofaId)))
                            .execute();
                }
        }
    }

    // Resolve the sofa/view id for any FS
    private String sofaIdForFs(org.apache.uima.cas.FeatureStructure fs) {
        String id = null;

        // 1) try SofaID from the FS's view/CAS
        try {
            org.apache.uima.cas.SofaFS s =
                    (fs instanceof AnnotationFS a) ? a.getView().getSofa() : fs.getCAS().getSofa();
            if (s != null) id = emptyToNull(s.getSofaID());
        } catch (Throwable ignore) {
        }

        // 2) fallback to view name
        if (id == null) {
            try {
                String vn = (fs instanceof AnnotationFS a) ? a.getView().getViewName() : fs.getCAS().getViewName();
                id = emptyToNull(vn);
            } catch (Throwable ignore) {
            }
        }

        // 3) final fallback
        return (id != null) ? id : "_InitialView";
    }

    @Override
    public void destroy() {
        // Close Hikari to stop housekeeping threads
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception ignore) {
        }

        // optional: clear caches if this AE can be reused in-JVM
        try {
            seenTsFingerprints.clear();
            typeToTable.clear();
            createdTables.clear();
        } catch (Exception ignore) {
        }

        // let GC reclaim the DSL context; nothing to close there
        dsl = null;
        dataSource = null;

        super.destroy();
    }


    // --- Minimal JSON serializer for non-primitive/array features
    static final class FeatureJsonSerializer {
        static String toJson(org.apache.uima.cas.FeatureStructure fs) {
            // Implement a conservative, dependency-free JSON: only primitive features + coveredText for Annotations
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;

            Type t = fs.getType();
            for (Feature f : t.getFeatures()) {
                String range = f.getRange().getName();
                if (range.startsWith("uima.cas.") && !UIMA_PRIMITIVE_TO_SQL.containsKey(range)) {
                    // arrays / refs → skip or mark
                    continue;
                }
                Object v = readPrimitive(fs, f);
                if (v == null) continue;
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(f.getShortName())).append("\":");
                sb.append(primitiveToJson(v));
            }
            if (fs instanceof AnnotationFS a) {
                if (!first) sb.append(",");
                // Safely get covered text with bounds checking
                String coveredText = safeCoveredTextForAnnotation(a);
                sb.append("\"coveredText\":").append(primitiveToJson(coveredText));
            }
            sb.append("}");
            return sb.toString();
        }

        private static String safeCoveredTextForAnnotation(AnnotationFS a) {
            try {
                String docText = a.getView().getDocumentText();
                if (docText == null) return null;

                int begin = a.getBegin();
                int end = a.getEnd();
                int docLength = docText.length();

                // Bounds checking: ensure indices are valid
                if (begin < 0 || end > docLength || begin > end) {
                    // Invalid span; return null
                    return null;
                }

                // Safe to extract substring
                return docText.substring(begin, end);
            } catch (StringIndexOutOfBoundsException e) {
                // Fallback: shouldn't happen with checks above, but handle gracefully
                return null;
            } catch (Exception e) {
                // Any other exception accessing the view/text
                return null;
            }
        }

        static Object readPrimitive(org.apache.uima.cas.FeatureStructure fs, Feature f) {
            String rn = f.getRange().getName();
            return switch (rn) {
                case "uima.cas.String" -> fs.getStringValue(f);
                case "uima.cas.Integer" -> fs.getIntValue(f);
                case "uima.cas.Float" -> fs.getFloatValue(f);
                case "uima.cas.Double" -> fs.getDoubleValue(f);
                case "uima.cas.Boolean" -> fs.getBooleanValue(f);
                case "uima.cas.Long" -> fs.getLongValue(f);
                case "uima.cas.Short" -> (int) fs.getShortValue(f);
                case "uima.cas.Byte" -> (int) fs.getByteValue(f);
                default -> null;
            };
        }

        private static String primitiveToJson(Object v) {
            if (v == null) return "null";
            if (v instanceof Number || v instanceof Boolean) return v.toString();
            return "\"" + escape(v.toString()) + "\"";
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\f", "\\f").replace("\t", "\\t").replace("\b", "\\b");
        }
    }


}
