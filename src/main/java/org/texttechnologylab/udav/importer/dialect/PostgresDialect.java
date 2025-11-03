package org.texttechnologylab.udav.importer.dialect;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
public class PostgresDialect implements SqlDialect {
    @Override
    public String varcharType(int length) {
        return "VARCHAR(" + length + ")";
    }

    @Override
    public String clobType() {
        return "TEXT";
    }

    @Override
    public String autoIncrementPrimaryKey(String columnName) {
        return columnName + " SERIAL";
    }
}
