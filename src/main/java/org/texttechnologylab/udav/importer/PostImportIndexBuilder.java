package org.texttechnologylab.udav.importer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.importer.config.DbProps;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;

import static org.jooq.impl.DSL.*;

/**
 * Builds the per-type secondary indexes (idx_*_doc_sofa[_begin]) once after all COPY
 * operations finish. Building btrees in bulk is significantly faster and produces denser
 * indexes than maintaining them incrementally during COPY.
 * Mirrors JooqDatabaseWriter's identifier scheme so the index names match what the rest
 * of the importer expects on the next run (CREATE INDEX IF NOT EXISTS).
 */
@Component
@RequiredArgsConstructor
public class PostImportIndexBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostImportIndexBuilder.class);

    private final DbProps db;
    private HikariDataSource dataSource;
    private DSLContext dsl;

    private static boolean tableExists(DSLContext ctx, String schema, String tbl) {
        return ctx.fetchExists(
                DSL.selectOne().from(table(name("information_schema", "tables")))
                        .where(field(name("table_schema"), String.class).eq(schema))
                        .and(field(name("table_name"), String.class).eq(tbl)));
    }

    private static boolean columnExists(DSLContext ctx, String schema, String tbl, String col) {
        return ctx.fetchExists(
                DSL.selectOne().from(table(name("information_schema", "columns")))
                        .where(field(name("table_schema"), String.class).eq(schema))
                        .and(field(name("table_name"), String.class).eq(tbl))
                        .and(field(name("column_name"), String.class).eq(col)));
    }

    // The two helpers below mirror JooqDatabaseWriter.sysColName / cutWithHash so the names match.
    private static String sysColName(String tableHash, String base, int maxIdent) {
        String b = base.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
        return cutWithHash(tableHash + "_" + b, maxIdent);
    }

    private static String cutWithHash(String s, int maxIdent) {
        if (s.length() <= maxIdent) return s;
        String hash = DigestUtils.sha256Hex(s).substring(0, 8);
        int keep = Math.max(1, maxIdent - hash.length() - 1);
        return s.substring(0, keep) + "_" + hash;
    }

    private static String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static SQLDialect resolveDialect(String explicit, String url) {
        if (explicit != null && !explicit.isBlank()) {
            try {
                return SQLDialect.valueOf(explicit.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignore) {
            }
        }
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.startsWith("jdbc:postgresql:")) return SQLDialect.POSTGRES;
        if (u.startsWith("jdbc:h2:")) return SQLDialect.H2;
        if (u.startsWith("jdbc:mysql:")) return SQLDialect.MYSQL;
        if (u.startsWith("jdbc:mariadb:")) return SQLDialect.MARIADB;
        if (u.startsWith("jdbc:sqlite:")) return SQLDialect.SQLITE;
        if (u.startsWith("jdbc:duckdb:")) return SQLDialect.DUCKDB;
        return SQLDialect.DEFAULT;
    }

    private static String normalizeSchemaForDialect(String schema, SQLDialect dialect) {
        String s = (schema == null || schema.isBlank()) ? "public" : schema;
        if (dialect.family() == SQLDialect.H2 && "public".equalsIgnoreCase(s)) return "PUBLIC";
        if (dialect.family() == SQLDialect.POSTGRES) return s.toLowerCase(Locale.ROOT);
        return s;
    }

    public void buildIndexes() {
        DSLContext ctx = dsl();
        String schema = normalizeSchemaForDialect(db.getSchema(), ctx.dialect());
        int maxIdent = Math.max(16, Math.min(db.getMaxIdent() <= 0 ? 63 : db.getMaxIdent(), 63));

        List<String> tables = ctx.select(field(name("table_name"), String.class))
                .from(table(name(schema, "uima_type_registry")))
                .fetch(field(name("table_name"), String.class));
        if (tables.isEmpty()) {
            LOGGER.info("No registered type tables to index.");
            return;
        }

        LOGGER.info("Building secondary indexes for {} type tables", tables.size());
        long t0 = System.nanoTime();
        int built = 0, skipped = 0;
        for (String tbl : tables) {
            if (tbl == null || tbl.isBlank()) continue;
            try {
                if (!tableExists(ctx, schema, tbl)) {
                    skipped++;
                    continue;
                }
                String colDoc = sysColName(tbl, "doc_id", maxIdent);
                String colSofa = sysColName(tbl, "sofa_id", maxIdent);
                boolean isAnno = columnExists(ctx, schema, tbl, sysColName(tbl, "fs_begin", maxIdent));
                String idxName;
                String createSql;
                if (isAnno) {
                    String colBegin = sysColName(tbl, "fs_begin", maxIdent);
                    idxName = cutWithHash("idx_" + tbl + "_doc_sofa_begin", maxIdent);
                    createSql = "CREATE INDEX IF NOT EXISTS " + q(idxName) + " ON " + q(schema) + "." + q(tbl)
                            + " (" + q(colDoc) + ", " + q(colSofa) + ", " + q(colBegin) + ")";
                } else {
                    idxName = cutWithHash("idx_" + tbl + "_doc_sofa", maxIdent);
                    createSql = "CREATE INDEX IF NOT EXISTS " + q(idxName) + " ON " + q(schema) + "." + q(tbl)
                            + " (" + q(colDoc) + ", " + q(colSofa) + ")";
                }
                ctx.execute(createSql);
                built++;
            } catch (Exception e) {
                LOGGER.warn("Failed to build index for table {}: {}", tbl, e.getMessage());
            }
        }
        LOGGER.info("Built indexes for {} tables (skipped {}) in {} ms",
                built, skipped, (System.nanoTime() - t0) / 1_000_000);
    }

    private DSLContext dsl() {
        if (dsl != null) return dsl;
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(db.getUrl());
        cfg.setUsername(db.getUser());
        cfg.setPassword(db.getPass());
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(0);
        cfg.setAutoCommit(true);
        cfg.setPoolName("PostImportIndexBuilderPool");
        this.dataSource = new HikariDataSource(cfg);
        SQLDialect dialect = resolveDialect(db.getDialect(), db.getUrl());
        this.dsl = DSL.using(this.dataSource, dialect,
                new Settings().withRenderQuotedNames(RenderQuotedNames.ALWAYS));
        return dsl;
    }

    @PreDestroy
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        dataSource = null;
        dsl = null;
    }
}
