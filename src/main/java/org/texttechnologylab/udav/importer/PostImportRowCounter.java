package org.texttechnologylab.udav.importer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.importer.config.DbProps;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.jooq.impl.DSL.*;

@Component
@RequiredArgsConstructor
public class PostImportRowCounter {

    private final DbProps db;
    private HikariDataSource dataSource;
    private DSLContext dsl;
    private static final Logger LOGGER = LoggerFactory.getLogger(PostImportRowCounter.class);

    private DSLContext dsl() {
        if (dsl != null) return dsl;

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(db.getUrl());
        cfg.setUsername(db.getUser());
        cfg.setPassword(db.getPass());
        cfg.setMaximumPoolSize(4);
        cfg.setMinimumIdle(1);
        cfg.setAutoCommit(true);
        cfg.setPoolName("PostImportRowCounterPool");
        this.dataSource = new HikariDataSource(cfg);

        SQLDialect dialect = resolveDialect(db.getDialect(), db.getUrl());
        this.dsl = DSL.using(this.dataSource, dialect, new Settings().withRenderQuotedNames(RenderQuotedNames.ALWAYS));
        return dsl;
    }

    public void updateRowCounts() {
        LOGGER.info("Updating row counts");
        final String schema = normalizeSchemaForDialect(db.getSchema(), dsl().dialect());

        Table<?> REG = table(name(schema, "uima_type_registry"));
        Field<Long> F_ID = field(name("id"), Long.class);
        Field<String> F_URI = field(name("uima_type_uri"), String.class);
        Field<String> F_TBL = field(name("table_name"), String.class);
        Field<Long> F_CNT = field(name("row_count"), Long.class);

        List<Record3<Long, String, String>> rows = dsl()
                .select(F_ID, F_URI, F_TBL)
                .from(REG)
                .fetch();

        List<org.jooq.Query> batch = new ArrayList<>(rows.size());
        for (Record3<Long, String, String> r : rows) {
            Long id = r.value1();
            String tableName = r.value3();
            if (tableName == null || tableName.isBlank()) continue;

            Long cnt;
            try {
                cnt = dsl().select(count())
                        .from(table(name(schema, tableName)))
                        .fetchOne(0, Long.class);
            } catch (Exception e) {
                // table might not exist yet (e.g., pruned types) → treat as 0
                cnt = 0L;
            }

            batch.add(
                    dsl().update(REG)
                            .set(F_CNT, cnt)
                            .where(F_ID.eq(id))
            );
        }

        if (!batch.isEmpty()) dsl().batch(batch).execute();
        LOGGER.info("Finished updating row counts");
    }

    private static SQLDialect resolveDialect(String explicit, String url) {
        if (explicit != null && !explicit.isBlank()) {
            try { return SQLDialect.valueOf(explicit.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignore) {}
        }
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.startsWith("jdbc:postgresql:")) return SQLDialect.POSTGRES;
        if (u.startsWith("jdbc:h2:"))         return SQLDialect.H2;
        if (u.startsWith("jdbc:mysql:"))      return SQLDialect.MYSQL;
        if (u.startsWith("jdbc:mariadb:"))    return SQLDialect.MARIADB;
        if (u.startsWith("jdbc:sqlite:"))     return SQLDialect.SQLITE;
        if (u.startsWith("jdbc:duckdb:"))     return SQLDialect.DUCKDB;
        return SQLDialect.DEFAULT;
    }

    private static String normalizeSchemaForDialect(String schema, SQLDialect dialect) {
        String s = (schema == null || schema.isBlank()) ? "public" : schema;
        if (dialect.family() == SQLDialect.H2 && "public".equalsIgnoreCase(s)) return "PUBLIC";
        if (dialect.family() == SQLDialect.POSTGRES) return s.toLowerCase(Locale.ROOT);
        return s;
    }

    @PreDestroy
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        dataSource = null;
        dsl = null;
    }
}
