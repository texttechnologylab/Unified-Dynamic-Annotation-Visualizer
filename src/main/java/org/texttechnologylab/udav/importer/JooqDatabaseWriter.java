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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.jooq.impl.DSL.*;

public class JooqDatabaseWriter extends JCasAnnotator_ImplBase {

    public static final String PARAM_JDBC_URL = "jdbcUrl";
    public static final String PARAM_DB_USER = "dbUser";
    public static final String PARAM_DB_PASS = "dbPass";
    public static final String PARAM_SCHEMA = "dbSchema";
    public static final String PARAM_BATCH_SIZE = "batchSize";
    public static final String PARAM_MAX_IDENT = "maxIdentifierLength";
    public static final String PARAM_SQL_DIALECT = "sqlDialect";

    private static final Logger LOGGER = LoggerFactory.getLogger(JooqDatabaseWriter.class);

    private static final Map<String, DataType<?>> UIMA_PRIMITIVE_TO_SQL = Map.of(
            "uima.cas.String", SQLDataType.CLOB,
            "uima.cas.Integer", SQLDataType.INTEGER,
            "uima.cas.Float", SQLDataType.REAL,
            "uima.cas.Double", SQLDataType.DOUBLE,
            "uima.cas.Boolean", SQLDataType.BOOLEAN,
            "uima.cas.Long", SQLDataType.BIGINT,
            "uima.cas.Short", SQLDataType.SMALLINT,
            "uima.cas.Byte", SQLDataType.SMALLINT
    );

    private static final int TABLE_HASH_LEN = 16;

    private static final Set<String> seenTsFingerprints = ConcurrentHashMap.newKeySet();
    private static final Object DDL_LOCK = new Object();
    private static final AtomicBoolean REGISTRY_READY = new AtomicBoolean(false);

    private final Map<String, String> typeToTable = new ConcurrentHashMap<>();
    private final Set<String> createdTables = ConcurrentHashMap.newKeySet();

    @ConfigurationParameter(name = PARAM_SCHEMA, mandatory = false, defaultValue = "public")
    private String schema;

    @ConfigurationParameter(name = PARAM_BATCH_SIZE, mandatory = false, defaultValue = "1000")
    private int batchSize;

    @ConfigurationParameter(name = PARAM_MAX_IDENT, mandatory = false, defaultValue = "63")
    private int maxIdentifierLength;

    private DSLContext dsl;
    private HikariDataSource dataSource;

    private static Iterable<Type> iterable(Iterator<Type> it) {
        List<Type> out = new ArrayList<>();
        while (it.hasNext()) out.add(it.next());
        return out;
    }

    private static DocumentMetaData getOrCreateDocumentMeta(JCas jCas) {
        try {
            return DocumentMetaData.get(jCas);
        } catch (IllegalArgumentException e) {
            DocumentMetaData md = new DocumentMetaData(jCas);
            md.setDocumentId(UUID.randomUUID().toString());
            md.setDocumentTitle("unknown");
            md.setDocumentUri(null);
            md.addToIndexes();
            return md;
        }
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
        String sqlDialectName = (String) context.getConfigParameterValue(PARAM_SQL_DIALECT);

        if (isBlank(jdbcUrl)) {
            throw new ResourceInitializationException(
                    new IllegalArgumentException("JooqDatabaseWriter: jdbcUrl missing."));
        }

        SQLDialect dialect = resolveDialect(sqlDialectName, jdbcUrl);
        this.schema = normalizeSchemaForDialect(this.schema, dialect);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(dbUser);
        cfg.setPassword(dbPass);

        // scale writers -> need enough DB connections for batching + DDL + metadata
        cfg.setMaximumPoolSize(16);
        cfg.setMinimumIdle(1);

        // one transaction per document, no auto-commit
        cfg.setAutoCommit(false);

        cfg.setPoolName("JooqWriterPool");

        try {
            this.dataSource = new HikariDataSource(cfg);
        } catch (IllegalArgumentException e) {
            throw new ResourceInitializationException(e);
        }

        // Reduce quoting overhead; still safe if you keep identifiers sane.
        Settings settings = new Settings().withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED);
        this.dsl = DSL.using(this.dataSource, dialect, settings);

        ensureRegistryTablesOnce();
    }

    private void ensureRegistryTablesOnce() {
        if (REGISTRY_READY.get()) return;
        synchronized (DDL_LOCK) {
            if (REGISTRY_READY.get()) return;

            // Run all DDL on a single dedicated connection with autoCommit=true so that
            // each statement is immediately visible to the next one on the same connection.
            // This avoids the "relation does not exist" error that occurs when DDL and the
            // subsequent CREATE INDEX run on different pool connections with autoCommit=false.
            dsl.connection(conn -> {
                boolean prevAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(true);
                try {
                    DSLContext ddl = DSL.using(conn, dsl.dialect(), dsl.settings());

                    ddl.createSchemaIfNotExists(DSL.name(schema)).execute();

                    ddl.createTableIfNotExists(DSL.name(schema, "uima_type_registry"))
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

                    ddl.createTableIfNotExists(DSL.name(schema, "documents"))
                            .column("doc_id", SQLDataType.CLOB.nullable(false))
                            .column("uri", SQLDataType.CLOB.nullable(true))
                            .column("language", SQLDataType.CLOB.nullable(true))
                            .column("content_hash", SQLDataType.VARCHAR(64).nullable(true))
                            .column("ts_hash", SQLDataType.VARCHAR(64).nullable(true))
                            .constraints(DSL.constraint(DSL.name(cut("pk_documents"))).primaryKey("doc_id"))
                            .execute();

                    ddl.createTableIfNotExists(DSL.name(schema, "type_system_fingerprints"))
                            .column("ts_hash", SQLDataType.CLOB.nullable(false))
                            .column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(DSL.currentOffsetDateTime()))
                            .constraints(DSL.constraint(DSL.name(cut("pk_ts_fingerprint"))).primaryKey("ts_hash"))
                            .execute();

                    ddl.createTableIfNotExists(DSL.name(schema, "sofas"))
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

                    ddl.createIndexIfNotExists(DSL.name(cut("idx_sofas_doc_id")))
                            .on(DSL.table(DSL.name(schema, "sofas")), DSL.field(DSL.name("doc_id")))
                            .execute();

                    ensureRowCountColumn(ddl);
                } finally {
                    conn.setAutoCommit(prevAutoCommit);
                }
            });

            REGISTRY_READY.set(true);
        }
    }

    private void ensureRowCountColumn(DSLContext ctx) {
        try {
            ctx.select(field(name("row_count")))
                    .from(table(name(schema, "uima_type_registry")))
                    .limit(1)
                    .fetch();
        } catch (DataAccessException e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
                try {
                    String alterSql = String.format(
                            "ALTER TABLE \"%s\".\"uima_type_registry\" ADD COLUMN \"row_count\" BIGINT DEFAULT 0",
                            schema);
                    ctx.execute(alterSql);
                } catch (DataAccessException ignore) {
                }
            }
        }
    }

    @Override
    public void process(JCas jCas) {
        // one transaction per document
        dsl.transaction(conf -> {
            DSLContext tx = DSL.using(conf);

            TypeSystem ts = jCas.getTypeSystem();
            ensureTablesForTypeSystem(tx, ts);

            DocumentMetaData md = getOrCreateDocumentMeta(jCas);
            String docId = safe(md.getDocumentId(), md::getDocumentUri, md::getDocumentTitle, () -> UUID.randomUUID().toString());
            String uri = safe(md.getDocumentUri(), md::getDocumentTitle, () -> docId);
            String lang = jCas.getDocumentLanguage();

            String tsHash = computeTypeSystemHash(ts);

            // collect sofa data from the CAS in-memory — no DB writes yet.
            Map<String, SofaData> sofas = collectSofas(jCas);
            String contentHash = computeContentHashFromSofas(tsHash, sofaHashMap(sofas));

            LOGGER.debug("[skip-check] docId='{}' tsHash='{}' contentHash='{}'", docId, tsHash, contentHash);
            if (isDocumentUpToDate(tx, docId, tsHash, contentHash)) {
                LOGGER.info("[skip] Document '{}' is up-to-date, skipping.", docId);
                return;
            }
            LOGGER.info("[process] Document '{}' needs (re)import.", docId);

            // only write to the DB if the document actually needs (re)importing.
            upsertSofas(tx, docId, sofas);
            upsertDocument(tx, docId, uri, lang, tsHash, contentHash);

            for (Type t : iterable(ts.getTypeIterator())) {
                if (isSkippableType(t)) continue;

                String tableNameHash = typeToTable.get(t.getName());
                if (tableNameHash == null) continue;

                String colRowHash = sysColName(tableNameHash, "row_hash");
                String colDocId = sysColName(tableNameHash, "doc_id");
                String colSofaId = sysColName(tableNameHash, "sofa_id");
                String colBegin = sysColName(tableNameHash, "fs_begin");
                String colEnd = sysColName(tableNameHash, "fs_end");
                String colText = sysColName(tableNameHash, "covered_text");
                String colFsJson = sysColName(tableNameHash, "fs_json");

                boolean isAnno = ts.subsumes(ts.getType("uima.tcas.Annotation"), t);

                if (isAnno) {
                    var idx = jCas.getCas().getAnnotationIndex(t);
                    if (idx == null || idx.isEmpty()) continue;

                    String docText = jCas.getDocumentText();
                    int docLength = docText != null ? docText.length() : 0;

                    List<Query> batch = new ArrayList<>(Math.min(idx.size(), batchSize));
                    for (AnnotationFS fs : idx) {
                        String sofaId = sofaIdForFs(fs);

                        Map<Field<?>, Object> values = new LinkedHashMap<>();
                        values.put(field(name(colRowHash)), computeRowHash(ts, t, docId, tableNameHash, fs));
                        values.put(field(name(colDocId)), docId);
                        values.put(field(name(colSofaId)), sofaId);
                        values.put(field(name(colBegin)), fs.getBegin());
                        values.put(field(name(colEnd)), fs.getEnd());
                        values.put(field(name(colText)), safeCoveredText(docText, docLength, fs.getBegin(), fs.getEnd()));

                        for (Feature f : t.getFeatures()) {
                            if (!isPrimitive(f)) continue;
                            values.putIfAbsent(field(name(featColName(tableNameHash, f))),
                                    FeatureJsonSerializer.readPrimitive(fs, f));
                        }

                        values.put(field(name(colFsJson)), FeatureJsonSerializer.toJsonPrimitivesOnly(fs));

                        batch.add(insertIgnore(tx, tableNameHash, values, colRowHash));
                        if (batch.size() >= batchSize) {
                            tx.batch(batch).execute();
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) tx.batch(batch).execute();

                } else {
                    var it = jCas.getCas().getIndexRepository().getAllIndexedFS(t);
                    if (it == null) continue;

                    List<Query> batch = new ArrayList<>(batchSize);
                    it.forEachRemaining(fs -> {
                        String sofaId = sofaIdForFs(fs);

                        Map<Field<?>, Object> values = new LinkedHashMap<>();
                        values.put(field(name(colRowHash)), computeRowHash(ts, t, docId, tableNameHash, fs));
                        values.put(field(name(colDocId)), docId);
                        values.put(field(name(colSofaId)), sofaId);

                        for (Feature f : t.getFeatures()) {
                            if (!isPrimitive(f)) continue;
                            values.put(field(name(featColName(tableNameHash, f))),
                                    FeatureJsonSerializer.readPrimitive(fs, f));
                        }
                        values.put(field(name(colFsJson)), FeatureJsonSerializer.toJsonPrimitivesOnly(fs));

                        batch.add(insertIgnore(tx, tableNameHash, values, colRowHash));
                        if (batch.size() >= batchSize) {
                            tx.batch(batch).execute();
                            batch.clear();
                        }
                    });
                    if (!batch.isEmpty()) tx.batch(batch).execute();
                }
            }
        });
    }

    private void ensureTablesForTypeSystem(DSLContext ctx, TypeSystem ts) {
        String tsHash = computeTypeSystemHash(ts);
        if (seenTsFingerprints.contains(tsHash)) return;

        synchronized (DDL_LOCK) {
            if (seenTsFingerprints.contains(tsHash)) return;

            if (fingerprintExists(ctx, tsHash)) {
                preloadTypeToTableFromRegistry(ctx, ts);
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
                String colSofa = sysColName(tableNameHash, "sofa_id");
                String colBegin = sysColName(tableNameHash, "fs_begin");
                String colEnd = sysColName(tableNameHash, "fs_end");
                String colText = sysColName(tableNameHash, "covered_text");
                String colFsJs = sysColName(tableNameHash, "fs_json");

                List<Field<?>> cols = new ArrayList<>();
                cols.add(field(name(pkCol), SQLDataType.BIGINT.identity(true)));
                cols.add(field(name(colRowH), SQLDataType.VARCHAR(64).nullable(false)));
                cols.add(field(name(colDoc), SQLDataType.CLOB.nullable(false)));
                cols.add(field(name(colSofa), SQLDataType.VARCHAR(128).nullable(false)));

                boolean isAnno = ts.subsumes(ts.getType("uima.tcas.Annotation"), t);
                if (isAnno) {
                    cols.add(field(name(colBegin), SQLDataType.INTEGER.nullable(false)));
                    cols.add(field(name(colEnd), SQLDataType.INTEGER.nullable(false)));
                    cols.add(field(name(colText), SQLDataType.CLOB.nullable(true)));
                }

                for (Feature f : t.getFeatures()) {
                    if (!isPrimitive(f)) continue;

                    DataType<?> dt = mapPrimitiveType(f.getRange().getName()).nullable(true);
                    cols.add(field(name(featColName(tableNameHash, f)), dt));
                }

                cols.add(field(name(colFsJs), SQLDataType.JSON.nullable(true)));

                ctx.createTableIfNotExists(name(schema, tableNameHash))
                        .columns(cols)
                        .constraints(
                                constraint(name(cut("pk_" + tableNameHash))).primaryKey(field(name(pkCol))),
                                constraint(name(cut("uq_" + tableNameHash + "_rowhash"))).unique(field(name(colRowH)))
                        )
                        .execute();

                if (isAnno) {
                    ctx.createIndexIfNotExists(name(cut("idx_" + tableNameHash + "_doc_sofa_begin")))
                            .on(table(name(schema, tableNameHash)),
                                    field(name(colDoc)), field(name(colSofa)), field(name(colBegin)))
                            .execute();
                } else {
                    ctx.createIndexIfNotExists(name(cut("idx_" + tableNameHash + "_doc_sofa")))
                            .on(table(name(schema, tableNameHash)),
                                    field(name(colDoc)), field(name(colSofa)))
                            .execute();
                }

                upsertTypeRegistry(ctx, uimaType, tableNameHash);
                createdTables.add(tableNameHash);
            }

            insertFingerprint(ctx, tsHash);
            seenTsFingerprints.add(tsHash);
        }
    }

    private boolean isSkippableType(Type t) {
        String n = t.getName();
        return n.startsWith("uima.cas.") && !n.equals("uima.tcas.Annotation");
    }

    private boolean isPrimitive(Feature f) {
        return UIMA_PRIMITIVE_TO_SQL.containsKey(f.getRange().getName());
    }

    private DataType<?> mapPrimitiveType(String rangeName) {
        return UIMA_PRIMITIVE_TO_SQL.getOrDefault(rangeName, SQLDataType.CLOB);
    }

    private String toSafeTableName(String uimaTypeName) {
        return tableHash(uimaTypeName);
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

    private String cut(String s) {
        return s.length() <= maxIdentifierLength ? s : s.substring(0, maxIdentifierLength);
    }

    private String tableHash(String uimaTypeName) {
        String h = DigestUtils.sha256Hex(uimaTypeName).toLowerCase(Locale.ROOT);
        return cut(h.substring(0, Math.min(TABLE_HASH_LEN, h.length())));
    }

    private String pkColName(String tableHash) {
        return cut(tableHash + "_row_id");
    }

    private String sysColName(String tableHash, String base) {
        return cut(tableHash + "_" + sanitizeIdent(base));
    }

    private String featColName(String tableHash, Feature f) {
        String base = sanitizeIdent(f.getShortName() != null ? f.getShortName() : f.getName());
        return cut(tableHash + "_f_" + base);
    }

    private String computeTypeSystemHash(TypeSystem ts) {
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
        return DigestUtils.sha256Hex(String.join("\n", parts));
    }

    private boolean fingerprintExists(DSLContext ctx, String tsHash) {
        Integer cnt = ctx.selectCount()
                .from(table(name(schema, "type_system_fingerprints")))
                .where(field("ts_hash").eq(tsHash))
                .fetchOne(0, Integer.class);
        return cnt != null && cnt > 0;
    }

    private void insertFingerprint(DSLContext ctx, String tsHash) {
        if (ctx.dialect().family() == SQLDialect.POSTGRES) {
            ctx.insertInto(table(name(schema, "type_system_fingerprints")))
                    .columns(field("ts_hash"))
                    .values(tsHash)
                    .onConflict(field("ts_hash")).doNothing()
                    .execute();
        } else {
            try {
                ctx.insertInto(table(name(schema, "type_system_fingerprints")))
                        .columns(field("ts_hash"))
                        .values(tsHash)
                        .execute();
            } catch (DataAccessException ignore) {
            }
        }
    }

    private void preloadTypeToTableFromRegistry(DSLContext ctx, TypeSystem ts) {
        List<String> typeNames = new ArrayList<>();
        for (Type t : iterable(ts.getTypeIterator())) if (!isSkippableType(t)) typeNames.add(t.getName());
        if (typeNames.isEmpty()) return;

        var r = ctx.select(field("uima_type_uri", String.class), field("table_name", String.class))
                .from(table(name(schema, "uima_type_registry")))
                .where(field("uima_type_uri").in(typeNames))
                .fetch();

        for (var rec : r) {
            String uri = rec.get(0, String.class);
            String tbl = rec.get(1, String.class);
            if (uri != null && tbl != null) typeToTable.put(uri, tbl);
        }
    }

    private boolean isDocumentUpToDate(DSLContext ctx, String docId, String tsHash, String contentHash) {
        var rec = ctx.select(field("ts_hash", String.class), field("content_hash", String.class))
                .from(table(name(schema, "documents")))
                .where(field("doc_id").eq(docId))
                .fetchOne();
        if (rec == null) {
            LOGGER.debug("[skip-check] docId='{}' → not found in documents table.", docId);
            return false;
        }
        String storedTs = rec.get(0, String.class);
        String storedContent = rec.get(1, String.class);
        boolean match = Objects.equals(tsHash, storedTs) && Objects.equals(contentHash, storedContent);
        LOGGER.debug("[skip-check] docId='{}' stored ts='{}' content='{}' → match={}",
                docId, storedTs, storedContent, match);
        return match;
    }

    private void upsertDocument(DSLContext ctx, String docId, String uri, String lang, String tsHash, String contentHash) {
        switch (ctx.dialect().family()) {
            case POSTGRES -> ctx.insertInto(table(name(schema, "documents")))
                    .columns(field("doc_id"), field("uri"), field("language"), field("ts_hash"), field("content_hash"))
                    .values(docId, uri, lang, tsHash, contentHash)
                    .onConflict(field("doc_id")).doUpdate()
                    .set(field("uri"), uri)
                    .set(field("language"), lang)
                    .set(field("ts_hash"), tsHash)
                    .set(field("content_hash"), contentHash)
                    .execute();
            default -> {
                try {
                    ctx.insertInto(table(name(schema, "documents")))
                            .columns(field("doc_id"), field("uri"), field("language"), field("ts_hash"), field("content_hash"))
                            .values(docId, uri, lang, tsHash, contentHash)
                            .execute();
                } catch (DataAccessException e) {
                    ctx.update(table(name(schema, "documents")))
                            .set(field("uri"), uri)
                            .set(field("language"), lang)
                            .set(field("ts_hash"), tsHash)
                            .set(field("content_hash"), contentHash)
                            .where(field("doc_id").eq(docId))
                            .execute();
                }
            }
        }
    }

    private void upsertTypeRegistry(DSLContext ctx, String uimaType, String tableNameHash) {
        if (ctx.dialect().family() == SQLDialect.POSTGRES) {
            ctx.insertInto(table(name(schema, "uima_type_registry")))
                    .columns(field("uima_type_uri"), field("table_name"))
                    .values(uimaType, tableNameHash)
                    .onConflict(field("uima_type_uri")).doUpdate()
                    .set(field("table_name"), tableNameHash)
                    .execute();
        } else {
            try {
                ctx.insertInto(table(name(schema, "uima_type_registry")))
                        .columns(field("uima_type_uri"), field("table_name"))
                        .values(uimaType, tableNameHash)
                        .execute();
            } catch (DataAccessException e) {
                ctx.update(table(name(schema, "uima_type_registry")))
                        .set(field("table_name"), tableNameHash)
                        .where(field("uima_type_uri").eq(uimaType))
                        .execute();
            }
        }
        typeToTable.put(uimaType, tableNameHash);
    }

    private Query insertIgnore(DSLContext ctx, String tableNameHash, Map<Field<?>, Object> values, String colRowHash) {
        return switch (ctx.dialect().family()) {
            case POSTGRES, SQLITE -> ctx.insertInto(table(name(schema, tableNameHash)))
                    .set(values)
                    .onConflict(field(name(colRowHash))).doNothing();
            case MARIADB, MYSQL -> ctx.insertInto(table(name(schema, tableNameHash)))
                    .set(values)
                    .onDuplicateKeyIgnore();
            default -> ctx.insertInto(table(name(schema, tableNameHash))).set(values);
        };
    }

    private record SofaData(String sofaId, Integer sofaNum, String mime, String uri, String text, String textHash) {}

    private Map<String, SofaData> collectSofas(JCas jCas) {
        Map<String, SofaData> result = new TreeMap<>();
        org.apache.uima.cas.CAS base = jCas.getCas();

        for (Iterator<org.apache.uima.cas.CAS> it = base.getViewIterator(); it.hasNext(); ) {
            org.apache.uima.cas.CAS view = it.next();

            String sofaId = null;
            Integer sofaNum = null;
            String mime = null, uri = null, text = null;

            try {
                var sfs = view.getSofa();
                if (sfs != null) {
                    sofaId = emptyToNull(sfs.getSofaID());
                    mime = sfs.getSofaMime();
                    uri = sfs.getSofaURI();
                    try { sofaNum = sfs.getSofaNum(); } catch (Throwable ignore) {}
                }
            } catch (Throwable ignore) {}

            if (sofaId == null) {
                try { sofaId = emptyToNull(view.getViewName()); } catch (Throwable ignore) {}
            }
            if (sofaId == null) sofaId = "_InitialView";

            try { text = view.getDocumentText(); } catch (Throwable ignore) {}
            String textHash = DigestUtils.sha256Hex(text == null ? "" : text);

            result.put(sofaId, new SofaData(sofaId, sofaNum, mime, uri, text, textHash));
        }
        return result;
    }

    // Write collected SOFAs to the DB
    private void upsertSofas(DSLContext ctx, String docId, Map<String, SofaData> sofas) {
        for (SofaData s : sofas.values()) {
            upsertSofa(ctx, docId, s.sofaId(), s.sofaNum(), s.mime(), s.uri(), s.text(), s.textHash());
        }
    }

    // Convenience: sofa_id -> hash map from collected data (used for content hash)
    private Map<String, String> sofaHashMap(Map<String, SofaData> sofas) {
        Map<String, String> hashes = new TreeMap<>();
        sofas.forEach((id, s) -> hashes.put(id, s.textHash()));
        return hashes;
    }

    private void upsertSofa(DSLContext ctx, String docId, String sofaId, Integer sofaNum, String mime, String uri, String text, String textHash) {
        Table<?> tbl = table(name(schema, "sofas"));
        Field<Object> fDoc = field(name("doc_id"));
        Field<Object> fId = field(name("sofa_id"));
        Field<Object> fNum = field(name("sofa_num"));
        Field<Object> fMime = field(name("mime_type"));
        Field<Object> fUri = field(name("sofa_uri"));
        Field<Object> fStr = field(name("sofa_string"));
        Field<Object> fHash = field(name("sofa_hash"));

        if (ctx.dialect().family() == SQLDialect.POSTGRES) {
            ctx.insertInto(tbl)
                    .columns(fDoc, fId, fNum, fMime, fUri, fStr, fHash)
                    .values(docId, sofaId, sofaNum, mime, uri, text, textHash)
                    .onConflict(fDoc, fId).doUpdate()
                    .set(fNum, sofaNum)
                    .set(fMime, mime)
                    .set(fUri, uri)
                    .set(fStr, text)
                    .set(fHash, textHash)
                    .execute();
        } else {
            try {
                ctx.insertInto(tbl)
                        .columns(fDoc, fId, fNum, fMime, fUri, fStr, fHash)
                        .values(docId, sofaId, sofaNum, mime, uri, text, textHash)
                        .execute();
            } catch (DataAccessException e) {
                ctx.update(tbl)
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

    private String computeContentHashFromSofas(String tsHash, Map<String, String> sofaHashes) {
        MessageDigest md = DigestUtils.getSha256Digest();
        md.update(("ts=" + tsHash + "\n").getBytes(StandardCharsets.UTF_8));
        for (var e : sofaHashes.entrySet()) {
            md.update((e.getKey() + "=" + e.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return DigestUtils.sha256Hex(md.digest());
    }

    private String safeCoveredText(String docText, int docLength, int begin, int end) {
        if (docText == null) return null;
        if (begin < 0 || end > docLength || begin > end) return null;
        return docText.substring(begin, end);
    }

    private String sofaIdForFs(org.apache.uima.cas.FeatureStructure fs) {
        String id = null;
        try {
            org.apache.uima.cas.SofaFS s =
                    (fs instanceof AnnotationFS a) ? a.getView().getSofa() : fs.getCAS().getSofa();
            if (s != null) id = emptyToNull(s.getSofaID());
        } catch (Throwable ignore) {
        }
        if (id == null) {
            try {
                String vn = (fs instanceof AnnotationFS a) ? a.getView().getViewName() : fs.getCAS().getViewName();
                id = emptyToNull(vn);
            } catch (Throwable ignore) {
            }
        }
        return id != null ? id : "_InitialView";
    }

    // Cheaper per-row hash: no covered-text hashing (avoid substring allocations)
    private String computeRowHash(TypeSystem ts, Type t, String docId, String tableNameHash, org.apache.uima.cas.FeatureStructure fs) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return DigestUtils.sha256Hex(t.getName() + "|" + docId + "|" + tableNameHash);
        }

        md.update(("tbl=" + tableNameHash + "|").getBytes(StandardCharsets.UTF_8));
        md.update(("type=" + t.getName() + "|").getBytes(StandardCharsets.UTF_8));
        md.update(("doc=" + (docId == null ? "" : docId) + "|").getBytes(StandardCharsets.UTF_8));

        try {
            String viewName = (fs instanceof AnnotationFS a) ? a.getView().getViewName() : fs.getCAS().getViewName();
            if (viewName != null) md.update(("view=" + viewName + "|").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignore) {
        }

        if (ts.subsumes(ts.getType("uima.tcas.Annotation"), t) && fs instanceof AnnotationFS a) {
            md.update(("b=" + a.getBegin() + "|e=" + a.getEnd() + "|").getBytes(StandardCharsets.UTF_8));
        }

        List<Feature> feats = new ArrayList<>();
        for (Feature f : t.getFeatures()) if (isPrimitive(f)) feats.add(f);
        feats.sort(Comparator.comparing(f -> {
            String s = f.getShortName();
            return s != null ? s : f.getName();
        }));

        for (Feature f : feats) {
            String fname = f.getShortName() != null ? f.getShortName() : f.getName();
            Object v = FeatureJsonSerializer.readPrimitive(fs, f);
            if (v == null) continue;
            md.update(("f=" + fname + "=" + v + "|").getBytes(StandardCharsets.UTF_8));
        }

        return DigestUtils.sha256Hex(md.digest());
    }

    @Override
    public void destroy() {
        try {
            if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        } catch (Exception ignore) {
        }
        super.destroy();
    }

    static final class FeatureJsonSerializer {

        static String toJsonPrimitivesOnly(org.apache.uima.cas.FeatureStructure fs) {
            Type t = fs.getType();
            StringBuilder sb = new StringBuilder(128);
            sb.append("{");
            boolean first = true;

            for (Feature f : t.getFeatures()) {
                Object v = readPrimitive(fs, f);
                if (v == null) continue;

                if (!first) sb.append(",");
                first = false;

                String k = f.getShortName() != null ? f.getShortName() : f.getName();
                sb.append("\"").append(escape(k)).append("\":").append(primitiveToJson(v));
            }

            sb.append("}");
            return sb.toString();
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
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
