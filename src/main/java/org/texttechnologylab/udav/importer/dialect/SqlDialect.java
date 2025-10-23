package org.texttechnologylab.udav.importer.dialect;

public interface SqlDialect {
    String varcharType(int length);

    String clobType();

    String autoIncrementPrimaryKey(String columnName);
}
