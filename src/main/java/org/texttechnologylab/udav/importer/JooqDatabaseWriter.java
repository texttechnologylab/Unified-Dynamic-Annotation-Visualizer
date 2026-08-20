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
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
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
    public static final String PARAM_PIPELINE_HASH = "pipelineHash";
    public static final String PARAM_STORE_COVERED_TEXT = "storeCoveredText";
    public static final String PARAM_ALLOW_DDL = "allowDdl";
    public static final String PARAM_PREPARE_SCHEMA_ONLY = "prepareSchemaOnly";
    private static final Logger LOGGER = LoggerFactory.getLogger(JooqDatabaseWriter.class);
    private static final Map<String, DataType<?>> UIMA_PRIMITIVE_TO_SQL = Map.of("uima.cas.String", SQLDataType.CLOB, "uima.cas.Integer", SQLDataType.INTEGER, "uima.cas.Float", SQLDataType.REAL, "uima.cas.Double", SQLDataType.DOUBLE, "uima.cas.Boolean", SQLDataType.BOOLEAN, "uima.cas.Long", SQLDataType.BIGINT, "uima.cas.Short", SQLDataType.SMALLINT, "uima.cas.Byte", SQLDataType.SMALLINT);
    private static final int TABLE_HASH_LEN = 8;
    private static final int COPY_FLUSH_CHARS = 16 * 1024 * 1024;
    private static final Map<RegistryKey, AtomicBoolean> REGISTRY_READY = new ConcurrentHashMap<>();
    private static final Map<RegistryKey, Object> DDL_LOCKS = new ConcurrentHashMap<>();
    private static final Map<RegistryKey, Set<String>> SEEN_TS_FINGERPRINTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    });
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final Map<String, String> typeToTable = new ConcurrentHashMap<>();
    private final Set<String> createdTables = ConcurrentHashMap.newKeySet();
    @ConfigurationParameter(name = PARAM_JDBC_URL, mandatory = true)
    private String jdbcUrl;
    @ConfigurationParameter(name = PARAM_DB_USER, mandatory = false)
    private String dbUser;
    @ConfigurationParameter(name = PARAM_DB_PASS, mandatory = false)
    private String dbPass;
    @ConfigurationParameter(name = PARAM_SQL_DIALECT, mandatory = false)
    private String sqlDialectName;
    @ConfigurationParameter(name = PARAM_SCHEMA, mandatory = false, defaultValue = "public")
    private String schema = "public";
    @ConfigurationParameter(name = PARAM_BATCH_SIZE, mandatory = false, defaultValue = "10000")
    private int batchSize = 10000;
    @ConfigurationParameter(name = PARAM_MAX_IDENT, mandatory = false, defaultValue = "63")
    private int maxIdentifierLength = 63;
    @ConfigurationParameter(name = PARAM_PIPELINE_HASH, mandatory = false, defaultValue = "unknown")
    private String pipelineHash = "unknown";
    @ConfigurationParameter(name = PARAM_STORE_COVERED_TEXT, mandatory = false, defaultValue = "false")
    private boolean storeCoveredText = false;
    @ConfigurationParameter(name = PARAM_ALLOW_DDL, mandatory = false, defaultValue = "true")
    private boolean allowDdl = true;
    @ConfigurationParameter(name = PARAM_PREPARE_SCHEMA_ONLY, mandatory = false, defaultValue = "false")
    private boolean prepareSchemaOnly = false;
    private DSLContext dsl;
    private HikariDataSource dataSource;
    private TypeSystem cachedTs;
    private TsCache tsCache;

    private static void updateHash(MessageDigest md, String key, String value) {
        md.update(key.getBytes(StandardCharsets.UTF_8));
        md.update((byte) '=');
        md.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        md.update((byte) 0);
    }

    private static String featSortName(Feature f) {
        String s = f.getShortName();
        return s != null ? s : f.getName();
    }

    private static DocumentMetaData getOrCreateDocumentMeta(JCas jCas) {
        try {
            return DocumentMetaData.get(jCas);
        } catch (IllegalArgumentException e) {
            DocumentMetaData md = new DocumentMetaData(jCas);
            md.setDocumentId(deterministicTextId(jCas));
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
            if (t != null && (!(t instanceof String) || !((String) t).isBlank())) {
                return t;
            }
        }
        return null;
    }

    private static String deterministicDocumentId(JCas jCas, DocumentMetaData md) {
        if (md != null) {
            if (!isBlank(md.getDocumentId())) return stripXmiSuffix(md.getDocumentId());
            if (!isBlank(md.getDocumentUri())) return DigestUtils.sha256Hex("uri:" + md.getDocumentUri());
            if (!isBlank(md.getDocumentTitle())) return DigestUtils.sha256Hex("title:" + md.getDocumentTitle());
        }
        return deterministicTextId(jCas);
    }

    // ".xmi" is a serialization-format suffix, not part of a logical document identity.
    // Stripping it here keeps doc_id consistent across docs whose upstream readers happen
    // to set DocumentMetaData.documentId with or without the extension.
    private static String stripXmiSuffix(String id) {
        String s = id.trim();
        if (s.length() > 4 && s.regionMatches(true, s.length() - 4, ".xmi", 0, 4)) {
            return s.substring(0, s.length() - 4);
        }
        return s;
    }

    private static String deterministicTextId(JCas jCas) {
        String text = null;
        try {
            text = jCas.getDocumentText();
        } catch (Throwable ignored) {
        }
        return DigestUtils.sha256Hex("text:" + (text == null ? "" : text));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
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

    private static int hardIdentifierLimitForDialect(SQLDialect dialect) {
        return switch (dialect.family()) {
            case POSTGRES -> 63;
            case MYSQL, MARIADB -> 64;
            default -> 63;
        };
    }

    private static int normalizeIdentifierLimit(Integer configured, SQLDialect dialect) {
        int hardLimit = hardIdentifierLimitForDialect(dialect);
        int requested = configured == null || configured <= 0 ? hardLimit : configured;
        return Math.max(16, Math.min(requested, hardLimit));
    }

    private static boolean isPgTypeCreateRace(DataAccessException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("pg_type_typname_nsp_index");
    }

    private static String rootMsg(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage();
    }

    private static String bytesToHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            out[j++] = HEX[b >>> 4];
            out[j++] = HEX[b & 0x0f];
        }
        return new String(out);
    }

    private static long advisoryLockKey(String key) {
        MessageDigest md = SHA256.get();
        md.reset();
        byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(digest).getLong();
    }

    private static <T> Set<T> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static String stringParam(UimaContext context, String name, String fallback) {
        Object v = context.getConfigParameterValue(name);
        if (v == null) return fallback;
        String s = String.valueOf(v);
        return s.isBlank() ? fallback : s;
    }

    private static int intParam(UimaContext context, String name, int fallback) {
        Object v = context.getConfigParameterValue(name);
        if (v == null) return fallback;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private static boolean booleanParam(UimaContext context, String name, boolean fallback) {
        Object v = context.getConfigParameterValue(name);
        if (v == null) return fallback;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);
        this.jdbcUrl = stringParam(context, PARAM_JDBC_URL, this.jdbcUrl);
        this.dbUser = stringParam(context, PARAM_DB_USER, this.dbUser);
        this.dbPass = stringParam(context, PARAM_DB_PASS, this.dbPass);
        this.schema = stringParam(context, PARAM_SCHEMA, this.schema);
        this.sqlDialectName = stringParam(context, PARAM_SQL_DIALECT, this.sqlDialectName);
        this.pipelineHash = stringParam(context, PARAM_PIPELINE_HASH, this.pipelineHash);
        this.batchSize = intParam(context, PARAM_BATCH_SIZE, this.batchSize);
        this.maxIdentifierLength = intParam(context, PARAM_MAX_IDENT, this.maxIdentifierLength);
        this.storeCoveredText = booleanParam(context, PARAM_STORE_COVERED_TEXT, this.storeCoveredText);
        this.allowDdl = booleanParam(context, PARAM_ALLOW_DDL, this.allowDdl);
        this.prepareSchemaOnly = booleanParam(context, PARAM_PREPARE_SCHEMA_ONLY, this.prepareSchemaOnly);
        if (isBlank(jdbcUrl)) {
            throw new ResourceInitializationException(new IllegalArgumentException("JooqDatabaseWriter: jdbcUrl missing."));
        }
        if (batchSize <= 0) {
            throw new ResourceInitializationException(new IllegalArgumentException("batchSize must be > 0."));
        }
        if (prepareSchemaOnly && !allowDdl) {
            throw new ResourceInitializationException(new IllegalArgumentException("prepareSchemaOnly=true requires allowDdl=true."));
        }
        SQLDialect dialect = resolveDialect(sqlDialectName, jdbcUrl);
        if (dialect.family() != SQLDialect.POSTGRES) {
            throw new ResourceInitializationException(new IllegalArgumentException("JooqDatabaseWriter currently supports PostgreSQL only. Detected: " + dialect));
        }
        this.maxIdentifierLength = normalizeIdentifierLimit(this.maxIdentifierLength, dialect);
        this.schema = normalizeSchemaForDialect(this.schema, dialect);
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        if (!isBlank(dbUser)) {
            cfg.setUsername(dbUser);
        }
        if (dbPass != null) {
            cfg.setPassword(dbPass);
        }
        cfg.setMaximumPoolSize(1);
        cfg.setMinimumIdle(0);
        cfg.setAutoCommit(false);
        cfg.setPoolName("JooqWriterPool-" + Integer.toHexString(System.identityHashCode(this)));
        cfg.addDataSourceProperty("reWriteBatchedInserts", "true");
        cfg.addDataSourceProperty("ApplicationName", "udav-duui-importer");
        try {
            this.dataSource = new HikariDataSource(cfg);
        } catch (IllegalArgumentException e) {
            throw new ResourceInitializationException(e);
        }
        Settings settings = new Settings().withRenderQuotedNames(RenderQuotedNames.EXPLICIT_DEFAULT_QUOTED);
        this.dsl = DSL.using(this.dataSource, dialect, settings);
        if (allowDdl) {
            ensureRegistryTablesOnce();
        }
    }

    @Override
    public void process(JCas jCas) {
        long t0 = System.nanoTime();
        TypeSystem ts = jCas.getTypeSystem();
        TsCache cache = getOrBuildTsCache(ts);
        ensureTablesForTypeSystem(cache);
        long t1 = System.nanoTime();
        if (prepareSchemaOnly) {
            LOGGER.debug("[schema-prep] TypeSystem prepared. tsHash={} ddlMs={}", cache.tsHash, (t1 - t0) / 1_000_000);
            return;
        }
        DocumentMetaData md = getOrCreateDocumentMeta(jCas);
        final String docId = deterministicDocumentId(jCas, md);
        final String uri = safe(md.getDocumentUri(), md::getDocumentTitle, () -> docId);
        final String lang = jCas.getDocumentLanguage();
        final Map<String, SofaData> sofas = collectSofas(jCas);
        final String contentHash = computeContentHashFromSofas(sofas);
        long t2 = System.nanoTime();
        DocumentState documentState = getDocumentState(dsl, docId, cache.tsHash, contentHash, pipelineHash);
        if (documentState.upToDate()) {
            LOGGER.info("[skip] Document '{}' is up-to-date, skipping.", docId);
            return;
        }
        long t3 = System.nanoTime();
        LOGGER.info("[process] Document '{}' needs (re)import. ddlMs={} extractMs={} skipCheckMs={}", docId, (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000, (t3 - t2) / 1_000_000);
        final Map<String, SofaData> sofasBySofaId = sofas;
        final List<TypeMeta> typeMetas = cache.types;
        dsl.transaction(conf -> {
            DSLContext tx = DSL.using(conf);
            acquireDocumentLock(tx, docId);
            DocumentState lockedState = getDocumentState(tx, docId, cache.tsHash, contentHash, pipelineHash);
            if (lockedState.upToDate()) {
                LOGGER.info("[skip] Document '{}' became up-to-date after lock, skipping.", docId);
                return;
            }
            if (lockedState.exists()) {
                deleteExistingRowsForDocument(tx, docId);
            }
            upsertSofas(tx, docId, sofas);
            upsertDocument(tx, docId, uri, lang, cache.tsHash, contentHash, pipelineHash);
            org.apache.uima.cas.CAS base = jCas.getCas();
            List<org.apache.uima.cas.CAS> views = new ArrayList<>();
            for (Iterator<org.apache.uima.cas.CAS> vit = base.getViewIterator(); vit.hasNext(); ) {
                views.add(vit.next());
            }
            for (TypeMeta meta : typeMetas) {
                if (meta.tableNameHash == null) continue;
                long typeStart = System.nanoTime();
                int rowsForType = 0;
                CopyBatch copy = new CopyBatch(tx, meta.tableNameHash, copyColumnNames(meta.tableNameHash, meta.isAnno, meta.primFeats));
                Object[] row = new Object[meta.bindCount];
                Object[] featValues = new Object[meta.primFeats.size()];
                Set<AnnotationFS> seenAnnoFs = meta.isAnno ? newIdentitySet() : null;
                Set<org.apache.uima.cas.FeatureStructure> seenGenericFs = meta.isAnno ? null : newIdentitySet();
                for (org.apache.uima.cas.CAS view : views) {
                    if (meta.isAnno) {
                        var idx = view.getAnnotationIndex(meta.type);
                        if (idx == null || idx.size() == 0) continue;
                        SofaData sd = sofaDataForView(sofasBySofaId, view);
                        String docText = sd != null ? sd.text() : view.getDocumentText();
                        int docLength = docText != null ? docText.length() : 0;
                        for (AnnotationFS fs : idx) {
                            if (!fs.getType().getName().equals(meta.typeName)) continue;
                            if (!seenAnnoFs.add(fs)) continue;
                            for (int i = 0; i < meta.primFeats.size(); i++) {
                                featValues[i] = FeatureJsonSerializer.readPrimitive(fs, meta.primFeats.get(i));
                            }
                            String fsViewName = sofaIdForFs(fs);
                            int begin = fs.getBegin();
                            int end = fs.getEnd();
                            row[0] = docId;
                            row[1] = fsViewName;
                            row[2] = begin;
                            row[3] = end;
                            int offset;
                            if (storeCoveredText) {
                                row[4] = safeCoveredText(docText, docLength, begin, end);
                                offset = 5;
                            } else {
                                offset = 4;
                            }
                            for (int i = 0; i < meta.primFeats.size(); i++) {
                                row[offset + i] = featValues[i];
                            }
                            copy.add(row);
                            rowsForType++;
                        }
                    } else {
                        var it = view.getIndexRepository().getAllIndexedFS(meta.type);
                        if (it == null) continue;
                        while (it.hasNext()) {
                            org.apache.uima.cas.FeatureStructure fs = it.next();
                            if (!fs.getType().getName().equals(meta.typeName)) continue;
                            if (!seenGenericFs.add(fs)) continue;
                            for (int i = 0; i < meta.primFeats.size(); i++) {
                                featValues[i] = FeatureJsonSerializer.readPrimitive(fs, meta.primFeats.get(i));
                            }
                            String fsViewName = sofaIdForFs(fs);
                            row[0] = docId;
                            row[1] = fsViewName;
                            for (int i = 0; i < meta.primFeats.size(); i++) {
                                row[2 + i] = featValues[i];
                            }
                            copy.add(row);
                            rowsForType++;
                        }
                    }
                }
                copy.flush();
                if (rowsForType > 0) {
                    LOGGER.debug("[type-import-copy] doc={} type={} table={} rows={} ms={}", docId, meta.typeName, meta.tableNameHash, rowsForType, (System.nanoTime() - typeStart) / 1_000_000);
                }
            }
        });
        LOGGER.info("[done] Document '{}' imported in {}ms.", docId, (System.nanoTime() - t0) / 1_000_000);
    }

    private void appendCopyTextValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("\\N");
            return;
        }
        String s;
        if (value instanceof Boolean b) {
            s = b ? "true" : "false";
        } else {
            s = String.valueOf(value);
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\0' -> {
                }
                default -> sb.append(c);
            }
        }
    }

    private List<String> copyColumnNames(String tableNameHash, boolean isAnno, List<Feature> primFeats) {
        List<String> cols = new ArrayList<>();
        cols.add(sysColName(tableNameHash, "doc_id"));
        cols.add(sysColName(tableNameHash, "sofa_id"));
        if (isAnno) {
            cols.add(sysColName(tableNameHash, "fs_begin"));
            cols.add(sysColName(tableNameHash, "fs_end"));
            if (storeCoveredText) {
                cols.add(sysColName(tableNameHash, "covered_text"));
            }
        }
        for (Feature f : primFeats) {
            cols.add(featColName(tableNameHash, f));
        }
        return cols;
    }

    private void acquireDocumentLock(DSLContext tx, String docId) {
        tx.execute("SELECT pg_advisory_xact_lock(?)", advisoryLockKey("uima-doc:" + docId));
    }

    private void deleteExistingRowsForDocument(DSLContext tx, String docId) {
        for (String tableName : existingRegisteredTypeTables(tx)) {
            Field<Object> docField = field(name(sysColName(tableName, "doc_id")));
            tx.deleteFrom(table(name(schema, tableName))).where(docField.eq(docId)).execute();
        }
        tx.deleteFrom(table(name(schema, "sofas"))).where(field(name("doc_id")).eq(docId)).execute();
    }

    private Set<String> existingRegisteredTypeTables(DSLContext ctx) {
        Set<String> registered = new TreeSet<>();
        try {
            List<String> registryTables = ctx.select(field(name("table_name"), String.class)).from(table(name(schema, "uima_type_registry"))).fetch(field(name("table_name"), String.class));
            registered.addAll(registryTables);
        } catch (DataAccessException e) {
            if (!allowDdl) {
                throw new IllegalStateException("DDL is disabled and uima_type_registry cannot be read.", e);
            }
            throw e;
        }
        registered.addAll(typeToTable.values());
        if (registered.isEmpty()) {
            return registered;
        }
        return new TreeSet<>(ctx.select(field(name("table_name"), String.class)).from(table(name("information_schema", "tables"))).where(field(name("table_schema"), String.class).eq(schema)).and(field(name("table_name"), String.class).in(registered)).fetch(field(name("table_name"), String.class)));
    }

    private RegistryKey registryKey() {
        return new RegistryKey(jdbcUrl, schema);
    }

    private Object ddlLock() {
        return DDL_LOCKS.computeIfAbsent(registryKey(), ignored -> new Object());
    }

    private Set<String> seenTsFingerprints() {
        return SEEN_TS_FINGERPRINTS.computeIfAbsent(registryKey(), ignored -> ConcurrentHashMap.newKeySet());
    }

    private AtomicBoolean registryReadyFlag() {
        return REGISTRY_READY.computeIfAbsent(registryKey(), ignored -> new AtomicBoolean(false));
    }

    private void ensureRegistryTablesOnce() {
        AtomicBoolean ready = registryReadyFlag();
        if (ready.get()) return;
        synchronized (ddlLock()) {
            if (ready.get()) return;
            dsl.connection(conn -> {
                boolean prevAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(true);
                try {
                    DSLContext ddl = DSL.using(conn, dsl.dialect(), dsl.settings());
                    ddl.createSchemaIfNotExists(DSL.name(schema)).execute();
                    ddl.createTableIfNotExists(DSL.name(schema, "uima_type_registry")).column("id", SQLDataType.BIGINT.identity(true)).column("uima_type_uri", SQLDataType.CLOB.nullable(false)).column("supertype_uri", SQLDataType.CLOB.nullable(true)).column("table_name", SQLDataType.VARCHAR(maxIdentifierLength).nullable(false)).column("row_count", SQLDataType.BIGINT.defaultValue(0L)).column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(currentOffsetDateTime())).constraints(constraint(DSL.name(cutWithHash("pk_uima_type_registry"))).primaryKey("id"), constraint(DSL.name(cutWithHash("uq_type_uri"))).unique("uima_type_uri"), constraint(DSL.name(cutWithHash("uq_table_name"))).unique("table_name")).execute();
                    ddl.createTableIfNotExists(DSL.name(schema, "documents")).column("doc_id", SQLDataType.VARCHAR(512).nullable(false)).column("uri", SQLDataType.CLOB.nullable(true)).column("language", SQLDataType.VARCHAR(32).nullable(true)).column("content_hash", SQLDataType.VARCHAR(64).nullable(true)).column("ts_hash", SQLDataType.VARCHAR(64).nullable(true)).column("pipeline_hash", SQLDataType.VARCHAR(64).nullable(true)).constraints(constraint(DSL.name(cutWithHash("pk_documents"))).primaryKey("doc_id")).execute();
                    ddl.createTableIfNotExists(DSL.name(schema, "type_system_fingerprints")).column("ts_hash", SQLDataType.VARCHAR(64).nullable(false)).column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(currentOffsetDateTime())).constraints(constraint(DSL.name(cutWithHash("pk_ts_fingerprint"))).primaryKey("ts_hash")).execute();
                    ddl.createTableIfNotExists(DSL.name(schema, "sofas")).column("doc_id", SQLDataType.VARCHAR(512).nullable(false)).column("sofa_id", SQLDataType.VARCHAR(128).nullable(false)).column("sofa_num", SQLDataType.INTEGER.nullable(true)).column("mime_type", SQLDataType.CLOB.nullable(true)).column("sofa_uri", SQLDataType.CLOB.nullable(true)).column("sofa_string", SQLDataType.CLOB.nullable(true)).column("sofa_hash", SQLDataType.VARCHAR(64).nullable(true)).column("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE.defaultValue(currentOffsetDateTime())).constraints(constraint(DSL.name(cutWithHash("pk_sofas"))).primaryKey("doc_id", "sofa_id")).execute();
                    ddl.createIndexIfNotExists(DSL.name(cutWithHash("idx_sofas_doc_id"))).on(DSL.table(DSL.name(schema, "sofas")), DSL.field(DSL.name("doc_id"))).execute();
                    ensureCompatibilityColumns(ddl);
                } finally {
                    conn.setAutoCommit(prevAutoCommit);
                }
            });
            ready.set(true);
        }
    }

    private void ensureCompatibilityColumns(DSLContext ctx) {
        ctx.execute("ALTER TABLE " + q(schema) + "." + q("uima_type_registry") + " ADD COLUMN IF NOT EXISTS " + q("row_count") + " BIGINT DEFAULT 0");
        ctx.execute("ALTER TABLE " + q(schema) + "." + q("uima_type_registry") + " ADD COLUMN IF NOT EXISTS " + q("supertype_uri") + " TEXT");
        ctx.execute("ALTER TABLE " + q(schema) + "." + q("documents") + " ADD COLUMN IF NOT EXISTS " + q("pipeline_hash") + " VARCHAR(64)");
    }

    private void ensureTablesForTypeSystem(TsCache cache) {
        Set<String> seenTs = seenTsFingerprints();
        if (!allowDdl) {
            preloadTypeToTableFromRegistry(dsl, cache);
            seenTs.add(cache.tsHash);
            resolveTableHashesIntoCache(cache, true);
            return;
        }
        if (seenTs.contains(cache.tsHash) && hasTableMappingsForAllTypes(cache)) {
            resolveTableHashesIntoCache(cache, false);
            return;
        }
        synchronized (ddlLock()) {
            if (seenTs.contains(cache.tsHash) && hasTableMappingsForAllTypes(cache)) {
                resolveTableHashesIntoCache(cache, false);
                return;
            }
            dsl.connection(conn -> {
                boolean prevAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(true);
                try {
                    DSLContext ctx = DSL.using(conn, dsl.dialect(), dsl.settings());
                    acquireSchemaDdlLock(ctx);
                    try {
                        runTypeSystemDDL(ctx, cache);
                    } finally {
                        releaseSchemaDdlLock(ctx);
                    }
                } finally {
                    conn.setAutoCommit(prevAutoCommit);
                }
            });
            resolveTableHashesIntoCache(cache, true);
        }
    }

    private boolean hasTableMappingsForAllTypes(TsCache cache) {
        for (TypeMeta meta : cache.types) {
            if (meta.tableNameHash == null && !typeToTable.containsKey(meta.typeName)) {
                return false;
            }
        }
        return true;
    }

    private void acquireSchemaDdlLock(DSLContext ctx) {
        ctx.execute("SELECT pg_advisory_lock(?)", advisoryLockKey("uima-ddl:" + schema));
    }

    private void releaseSchemaDdlLock(DSLContext ctx) {
        ctx.execute("SELECT pg_advisory_unlock(?)", advisoryLockKey("uima-ddl:" + schema));
    }

    private void resolveTableHashesIntoCache(TsCache cache, boolean requireAll) {
        if (cache.types.isEmpty()) return;
        List<TypeMeta> resolved = new ArrayList<>(cache.types.size());
        List<String> missing = new ArrayList<>();
        for (TypeMeta meta : cache.types) {
            String hash = meta.tableNameHash != null ? meta.tableNameHash : typeToTable.get(meta.typeName);
            if (hash == null) {
                if (requireAll) missing.add(meta.typeName);
                continue;
            }
            if (Objects.equals(meta.tableNameHash, hash)) {
                resolved.add(meta);
            } else {
                resolved.add(new TypeMeta(meta.type, meta.supertypeName, meta.isAnno, meta.primFeats, hash, storeCoveredText));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("DDL is disabled or schema registry is incomplete. Missing DB table mappings for UIMA types. Examples: " + missing.stream().limit(10).toList());
        }
        cache.types.clear();
        cache.types.addAll(resolved);
    }

    private void runTypeSystemDDL(DSLContext ctx, TsCache cache) {
        Set<String> seenTs = seenTsFingerprints();
        if (fingerprintExists(ctx, cache.tsHash)) {
            preloadTypeToTableFromRegistry(ctx, cache);
            updateTypeRegistryHierarchy(ctx, cache);
            seenTs.add(cache.tsHash);
            return;
        }
        for (TypeMeta meta : cache.types) {
            String uimaType = meta.typeName;
            String tableNameHash = typeToTable.computeIfAbsent(uimaType, this::toSafeTableName);
            String colDoc = sysColName(tableNameHash, "doc_id");
            String colSofa = sysColName(tableNameHash, "sofa_id");
            String colBegin = sysColName(tableNameHash, "fs_begin");
            String colEnd = sysColName(tableNameHash, "fs_end");
            String colText = sysColName(tableNameHash, "covered_text");
            if (!createdTables.contains(tableNameHash)) {
                List<Field<?>> cols = new ArrayList<>();
                cols.add(field(name(colDoc), SQLDataType.VARCHAR(512).nullable(false)));
                cols.add(field(name(colSofa), SQLDataType.VARCHAR(128).nullable(false)));
                if (meta.isAnno) {
                    cols.add(field(name(colBegin), SQLDataType.INTEGER.nullable(false)));
                    cols.add(field(name(colEnd), SQLDataType.INTEGER.nullable(false)));
                    if (storeCoveredText) {
                        cols.add(field(name(colText), SQLDataType.CLOB.nullable(true)));
                    }
                }
                for (Feature f : meta.primFeats) {
                    DataType<?> dt = mapPrimitiveType(f.getRange().getName()).nullable(true);
                    cols.add(field(name(featColName(tableNameHash, f)), dt));
                }
                try {
                    ctx.createTableIfNotExists(name(schema, tableNameHash)).columns(cols).execute();
                } catch (DataAccessException e) {
                    if (!isPgTypeCreateRace(e)) throw e;
                    LOGGER.debug("Ignoring PostgreSQL CREATE TABLE type race for table {}: {}", tableNameHash, rootMsg(e));
                }
            }
            ensureTypeCompatibilityColumns(ctx, tableNameHash, meta);
            // Secondary indexes (idx_*_doc_sofa[_begin]) are intentionally NOT created here.
            // PostImportIndexBuilder builds them once after all COPY work finishes — bulk-built
            // btrees are denser and faster than maintaining them incrementally during COPY.
            upsertTypeRegistry(ctx, uimaType, meta.supertypeName, tableNameHash);
            createdTables.add(tableNameHash);
        }
        insertFingerprint(ctx, cache.tsHash);
        seenTs.add(cache.tsHash);
    }

    private void ensureTypeCompatibilityColumns(DSLContext ctx, String tableNameHash, TypeMeta meta) {
        ctx.execute("ALTER TABLE " + q(schema) + "." + q(tableNameHash) + " ADD COLUMN IF NOT EXISTS " + q(sysColName(tableNameHash, "doc_id")) + " VARCHAR(512)");
        ctx.execute("ALTER TABLE " + q(schema) + "." + q(tableNameHash) + " ADD COLUMN IF NOT EXISTS " + q(sysColName(tableNameHash, "sofa_id")) + " VARCHAR(128)");
        if (meta.isAnno) {
            ctx.execute("ALTER TABLE " + q(schema) + "." + q(tableNameHash) + " ADD COLUMN IF NOT EXISTS " + q(sysColName(tableNameHash, "fs_begin")) + " INTEGER");
            ctx.execute("ALTER TABLE " + q(schema) + "." + q(tableNameHash) + " ADD COLUMN IF NOT EXISTS " + q(sysColName(tableNameHash, "fs_end")) + " INTEGER");
            if (storeCoveredText) {
                ctx.execute("ALTER TABLE " + q(schema) + "." + q(tableNameHash) + " ADD COLUMN IF NOT EXISTS " + q(sysColName(tableNameHash, "covered_text")) + " TEXT");
            }
        }
        for (Feature f : meta.primFeats) {
            ctx.execute("ALTER TABLE " + q(schema) + "." + q(tableNameHash) + " ADD COLUMN IF NOT EXISTS " + q(featColName(tableNameHash, f)) + " " + postgresTypeSql(f.getRange().getName()));
        }
    }

    private String postgresTypeSql(String rangeName) {
        return switch (rangeName) {
            case "uima.cas.String" -> "TEXT";
            case "uima.cas.Integer" -> "INTEGER";
            case "uima.cas.Float" -> "REAL";
            case "uima.cas.Double" -> "DOUBLE PRECISION";
            case "uima.cas.Boolean" -> "BOOLEAN";
            case "uima.cas.Long" -> "BIGINT";
            case "uima.cas.Short", "uima.cas.Byte" -> "SMALLINT";
            default -> "TEXT";
        };
    }

    private DataType<?> mapPrimitiveType(String rangeName) {
        return UIMA_PRIMITIVE_TO_SQL.getOrDefault(rangeName, SQLDataType.CLOB);
    }

    private boolean fingerprintExists(DSLContext ctx, String tsHash) {
        Integer cnt = ctx.selectCount().from(table(name(schema, "type_system_fingerprints"))).where(field(name("ts_hash")).eq(tsHash)).fetchOne(0, Integer.class);
        return cnt != null && cnt > 0;
    }

    private void insertFingerprint(DSLContext ctx, String tsHash) {
        ctx.insertInto(table(name(schema, "type_system_fingerprints"))).columns(field(name("ts_hash"))).values(tsHash).onConflict(field(name("ts_hash"))).doNothing().execute();
    }

    private void preloadTypeToTableFromRegistry(DSLContext ctx, TsCache cache) {
        if (cache.types.isEmpty()) return;
        List<String> typeNames = new ArrayList<>(cache.types.size());
        for (TypeMeta meta : cache.types) {
            typeNames.add(meta.typeName);
        }
        List<Record2<String, String>> records = ctx.select(field(name("uima_type_uri"), String.class), field(name("table_name"), String.class)).from(table(name(schema, "uima_type_registry"))).where(field(name("uima_type_uri")).in(typeNames)).fetch();
        for (Record2<String, String> rec : records) {
            String uri = rec.value1();
            String tbl = rec.value2();
            if (uri != null && tbl != null) {
                typeToTable.put(uri, tbl);
            }
        }
    }

    private void upsertTypeRegistry(DSLContext ctx, String uimaType, String supertypeName, String tableNameHash) {
        Table<?> tbl = table(name(schema, "uima_type_registry"));
        ctx.insertInto(tbl)
                .columns(field(name("uima_type_uri")), field(name("supertype_uri")), field(name("table_name")))
                .values(uimaType, supertypeName, tableNameHash)
                .onConflict(field(name("uima_type_uri")))
                .doUpdate()
                .set(field(name("supertype_uri")), supertypeName)
                .set(field(name("table_name")), tableNameHash)
                .execute();
        typeToTable.put(uimaType, tableNameHash);
    }

    private void updateTypeRegistryHierarchy(DSLContext ctx, TsCache cache) {
        Table<?> tbl = table(name(schema, "uima_type_registry"));
        for (TypeMeta meta : cache.types) {
            ctx.update(tbl)
                    .set(field(name("supertype_uri")), meta.supertypeName)
                    .where(field(name("uima_type_uri")).eq(meta.typeName))
                    .execute();
        }
    }

    private DocumentState getDocumentState(DSLContext ctx, String docId, String tsHash, String contentHash, String pipelineHash) {
        var rec = ctx.select(field(name("ts_hash"), String.class), field(name("content_hash"), String.class), field(name("pipeline_hash"), String.class)).from(table(name(schema, "documents"))).where(field(name("doc_id")).eq(docId)).fetchOne();
        if (rec == null) {
            return new DocumentState(false, false);
        }
        boolean upToDate = Objects.equals(tsHash, rec.get(0, String.class)) && Objects.equals(contentHash, rec.get(1, String.class)) && Objects.equals(pipelineHash, rec.get(2, String.class));
        return new DocumentState(true, upToDate);
    }

    private void upsertDocument(DSLContext ctx, String docId, String uri, String lang, String tsHash, String contentHash, String pipelineHash) {
        Table<?> tbl = table(name(schema, "documents"));
        ctx.insertInto(tbl).columns(field(name("doc_id")), field(name("uri")), field(name("language")), field(name("ts_hash")), field(name("content_hash")), field(name("pipeline_hash"))).values(docId, uri, lang, tsHash, contentHash, pipelineHash).onConflict(field(name("doc_id"))).doUpdate().set(field(name("uri")), uri).set(field(name("language")), lang).set(field(name("ts_hash")), tsHash).set(field(name("content_hash")), contentHash).set(field(name("pipeline_hash")), pipelineHash).execute();
    }

    private Map<String, SofaData> collectSofas(JCas jCas) {
        Map<String, SofaData> result = new TreeMap<>();
        org.apache.uima.cas.CAS base = jCas.getCas();
        for (Iterator<org.apache.uima.cas.CAS> it = base.getViewIterator(); it.hasNext(); ) {
            org.apache.uima.cas.CAS view = it.next();
            String sofaId = null;
            Integer sofaNum = null;
            String mime = null;
            String uri = null;
            String text = null;
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
            if (sofaId == null) {
                try {
                    sofaId = emptyToNull(view.getViewName());
                } catch (Throwable ignore) {
                }
            }
            if (sofaId == null) sofaId = "_InitialView";
            try {
                text = view.getDocumentText();
            } catch (Throwable ignore) {
            }
            String textHash = DigestUtils.sha256Hex(text == null ? "" : text);
            result.put(sofaId, new SofaData(sofaId, sofaNum, mime, uri, text, textHash));
        }
        return result;
    }

    private SofaData sofaDataForView(Map<String, SofaData> sofasBySofaId, org.apache.uima.cas.CAS view) {
        String sofaId = null;
        try {
            var sofa = view.getSofa();
            if (sofa != null) sofaId = emptyToNull(sofa.getSofaID());
        } catch (Throwable ignore) {
        }
        if (sofaId != null && sofasBySofaId.containsKey(sofaId)) {
            return sofasBySofaId.get(sofaId);
        }
        String viewName = null;
        try {
            viewName = emptyToNull(view.getViewName());
        } catch (Throwable ignore) {
        }
        if (viewName != null) {
            return sofasBySofaId.get(viewName);
        }
        return sofasBySofaId.get("_InitialView");
    }

    private void upsertSofas(DSLContext ctx, String docId, Map<String, SofaData> sofas) {
        for (SofaData s : sofas.values()) {
            upsertSofa(ctx, docId, s.sofaId(), s.sofaNum(), s.mime(), s.uri(), s.text(), s.textHash());
        }
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
        ctx.insertInto(tbl).columns(fDoc, fId, fNum, fMime, fUri, fStr, fHash).values(docId, sofaId, sofaNum, mime, uri, text, textHash).onConflict(fDoc, fId).doUpdate().set(fNum, sofaNum).set(fMime, mime).set(fUri, uri).set(fStr, text).set(fHash, textHash).execute();
    }

    private String computeContentHashFromSofas(Map<String, SofaData> sofas) {
        MessageDigest md = DigestUtils.getSha256Digest();
        for (var e : sofas.entrySet()) {
            SofaData s = e.getValue();
            updateHash(md, "sofa_id", s.sofaId());
            updateHash(md, "sofa_num", s.sofaNum() == null ? null : String.valueOf(s.sofaNum()));
            updateHash(md, "mime", s.mime());
            updateHash(md, "uri", s.uri());
            updateHash(md, "text_hash", s.textHash());
            md.update((byte) '\n');
        }
        return bytesToHex(md.digest());
    }

    private String safeCoveredText(String docText, int docLength, int begin, int end) {
        if (docText == null) return null;
        if (begin < 0 || end > docLength || begin > end) return null;
        return docText.substring(begin, end);
    }

    private String sofaIdForFs(org.apache.uima.cas.FeatureStructure fs) {
        String id = null;
        try {
            org.apache.uima.cas.SofaFS s = (fs instanceof AnnotationFS a) ? a.getView().getSofa() : fs.getCAS().getSofa();
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

    private TsCache getOrBuildTsCache(TypeSystem ts) {
        if (cachedTs == ts && tsCache != null) return tsCache;
        Type annoSuper = ts.getType("uima.tcas.Annotation");
        List<TypeMeta> metas = new ArrayList<>();
        List<String> hashParts = new ArrayList<>();
        hashParts.add("__writer_storeCoveredText=" + storeCoveredText);
        for (Iterator<Type> it = ts.getTypeIterator(); it.hasNext(); ) {
            Type t = it.next();
            if (isSkippableType(t)) continue;
            Type parent = ts.getParent(t);
            String supertypeName = parent == null || isSkippableType(parent) ? null : parent.getName();
            boolean isAnno = annoSuper != null && ts.subsumes(annoSuper, t);
            List<Feature> primFeats = new ArrayList<>();
            List<String> featParts = new ArrayList<>();
            for (Feature f : t.getFeatures()) {
                String shortName = f.getShortName();
                if (isAnno && ("begin".equals(shortName) || "end".equals(shortName))) {
                    continue;
                }
                String rn = f.getRange().getName();
                if (!UIMA_PRIMITIVE_TO_SQL.containsKey(rn)) continue;
                primFeats.add(f);
                featParts.add(featSortName(f) + ":" + rn);
            }
            primFeats.sort(Comparator.comparing(JooqDatabaseWriter::featSortName));
            Collections.sort(featParts);
            String tableNameHash = typeToTable.get(t.getName());
            metas.add(new TypeMeta(t, supertypeName, isAnno, primFeats, tableNameHash, storeCoveredText));
            hashParts.add(t.getName() + "|" + (supertypeName == null ? "" : supertypeName) + "|" + (isAnno ? "A" : "F") + "|" + String.join(",", featParts));
        }
        Collections.sort(hashParts);
        String tsHash = DigestUtils.sha256Hex(String.join("\n", hashParts));
        TsCache c = new TsCache(tsHash, metas);
        this.cachedTs = ts;
        this.tsCache = c;
        return c;
    }

    private boolean isSkippableType(Type t) {
        String n = t.getName();
        if (n.startsWith("uima.cas.")) return true;
        return n.equals("uima.tcas.Annotation");
    }

    private String toSafeTableName(String uimaTypeName) {
        String sanitized = sanitizeIdent(uimaTypeName).replace("org_texttechnologylab_", "").replace("de_tudarmstadt_ukp_dkpro_core_api_", "").replace("type_", "");
        if (sanitized.length() > Math.max(12, maxIdentifierLength - TABLE_HASH_LEN - 1)) {
            sanitized = sanitized.substring(0, Math.max(12, maxIdentifierLength - TABLE_HASH_LEN - 1));
        }
        String hash = DigestUtils.sha256Hex(uimaTypeName).substring(0, TABLE_HASH_LEN);
        return cutWithHash(sanitized + "_" + hash);
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

    private String cutWithHash(String s) {
        if (s == null) return "";
        if (s.length() <= maxIdentifierLength) return s;
        String hash = DigestUtils.sha256Hex(s).substring(0, 8);
        int keep = Math.max(1, maxIdentifierLength - hash.length() - 1);
        return s.substring(0, keep) + "_" + hash;
    }

    private String sysColName(String tableHash, String base) {
        return cutWithHash(tableHash + "_" + sanitizeIdent(base));
    }

    private String featColName(String tableHash, Feature f) {
        String base = sanitizeIdent(f.getShortName() != null ? f.getShortName() : f.getName());
        String hash = DigestUtils.sha256Hex(f.getName()).substring(0, 8);
        return cutWithHash(tableHash + "_f_" + base + "_" + hash);
    }

    private String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void destroy() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        } catch (Exception ignore) {
        }
        super.destroy();
    }

    private record RegistryKey(String jdbcUrl, String schema) {
    }

    private static final class TypeMeta {
        final Type type;
        final String typeName;
        final String supertypeName;
        final boolean isAnno;
        final List<Feature> primFeats;
        final String tableNameHash;
        final int bindCount;

        TypeMeta(Type type, String supertypeName, boolean isAnno, List<Feature> primFeats, String tableNameHash, boolean storeCoveredText) {
            this.type = type;
            this.typeName = type.getName();
            this.supertypeName = supertypeName;
            this.isAnno = isAnno;
            this.primFeats = primFeats;
            this.tableNameHash = tableNameHash;
            this.bindCount = (isAnno ? (storeCoveredText ? 5 : 4) : 2) + primFeats.size();
        }
    }

    private static final class TsCache {
        final String tsHash;
        final List<TypeMeta> types;

        TsCache(String tsHash, List<TypeMeta> types) {
            this.tsHash = tsHash;
            this.types = types;
        }
    }

    private record SofaData(String sofaId, Integer sofaNum, String mime, String uri, String text, String textHash) {
    }

    private record DocumentState(boolean exists, boolean upToDate) {
    }

    static final class FeatureJsonSerializer {
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
    }

    private final class CopyBatch {
        private final DSLContext tx;
        private final String tableName;
        private final List<String> columns;
        private final StringBuilder buffer = new StringBuilder(1024 * 1024);
        private int pending = 0;

        private CopyBatch(DSLContext tx, String tableName, List<String> columns) {
            this.tx = tx;
            this.tableName = tableName;
            this.columns = columns;
        }

        void add(Object[] row) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) buffer.append('\t');
                appendCopyTextValue(buffer, row[i]);
            }
            buffer.append('\n');
            pending++;
            if (pending >= batchSize || buffer.length() >= COPY_FLUSH_CHARS) {
                flush();
            }
        }

        void flush() {
            if (pending == 0) return;
            final String data = buffer.toString();
            buffer.setLength(0);
            pending = 0;
            try {
                tx.connection(conn -> {
                    try {
                        PGConnection pg = conn.unwrap(PGConnection.class);
                        CopyManager copyManager = pg.getCopyAPI();
                        try (StringReader reader = new StringReader(data)) {
                            copyManager.copyIn(copySql(), reader);
                        }
                    } catch (IOException e) {
                        throw new SQLException(e);
                    }
                });
            } catch (DataAccessException e) {
                throw new DataAccessException("COPY failed for table " + q(schema) + "." + q(tableName) + ": " + rootMsg(e), e);
            }
        }

        private String copySql() {
            StringJoiner joiner = new StringJoiner(", ");
            for (String column : columns) {
                joiner.add(q(column));
            }
            return "COPY " + q(schema) + "." + q(tableName) + " (" + joiner + ")" + " FROM STDIN WITH (FORMAT text, DELIMITER E'\\t', NULL '\\N')";
        }
    }
}
