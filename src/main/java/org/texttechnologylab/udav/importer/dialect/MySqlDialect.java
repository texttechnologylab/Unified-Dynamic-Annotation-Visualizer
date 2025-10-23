package org.texttechnologylab.udav.importer.dialect;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql")
public class MySqlDialect implements SqlDialect {
    @Override
    public String varcharType(int length) {
        return "VARCHAR(" + length + ")";
    }

    @Override
    public String clobType() {
        return "LONGTEXT";
    }

    @Override
    public String autoIncrementPrimaryKey(String columnName) {
        return columnName + " BIGINT AUTO_INCREMENT";
    }
}
