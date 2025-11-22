package org.texttechnologylab.udav.generators.sources;

import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.database.TypeTableResolver;
import org.texttechnologylab.udav.sources.DBAccess;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

import static org.texttechnologylab.udav.database.DBConstants.DB_SCHEMA_UIMA;

@Getter
public class SourceUIMA extends Source {

    private final String uri;
    private final String tableHash;
    private final AnnotationType annotationType;
    private final DBAccess dbAccess;

    public SourceUIMA(String uri, DBAccess dbAccess) throws SQLException {
        this.uri = uri.trim();
        this.dbAccess = dbAccess;
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(dbAccess.getDataSource().getConnection());
            TypeTableResolver resolver = new TypeTableResolver(dsl, DB_SCHEMA_UIMA);
            this.tableHash = resolver.tableForType(uri);
        }
        if (this.tableHash == null) {
            throw new IllegalArgumentException("No table registered for UIMA type: " + uri);
        }
        this.annotationType = determineAnnotationType(uri);
    }

    public Set<String> determineAllSourceFiles() throws SQLException {
        return dbAccess.getSourceFiles();
    }

    public static String sanitize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(java.util.Locale.ROOT);
    }

    public enum AnnotationType { POS, OTHER }


    private static AnnotationType determineAnnotationType(String uri) {
        uri = uri.toLowerCase(Locale.ROOT);
        if (uri.endsWith(".lexmorph.type.pos.pos") || uri.contains(".lexmorph.type.pos.")) return AnnotationType.POS;
        return AnnotationType.OTHER;
    }
}
