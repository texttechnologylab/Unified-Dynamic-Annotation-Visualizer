package org.texttechnologylab.udav.importer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.importer.dialect.SqlDialect;

import java.util.Map;

/**
 * Service for generating database schema from entity attribute length metadata.
 */
@Service
public class SchemaGeneratorService {
    private final JdbcTemplate jdbcTemplate;
    private final SqlDialect dialect;

    /**
     * Construct service with JDBC template and SQL dialect.
     *
     * @param jdbcTemplate template for executing SQL statements
     * @param dialect      database-specific SQL dialect
     */
    public SchemaGeneratorService(JdbcTemplate jdbcTemplate, SqlDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    /**
     * Generate tables for each entity with columns sized according to provided maximum lengths.
     * <ul>
     *   <li>Table name is the entity key.</li>
     *   <li>Primary key column name: {@code entity_pk_id}, auto-increment per dialect.</li>
     *   <li>For each attribute:
     *     <ul>
     *       <li>Column name: {@code ENTITY_ATTRNAME}, colons in attribute names replaced with underscores.</li>
     *       <li>Type: CLOB if max length &gt; 255, otherwise VARCHAR(max length or 1 minimum).</li>
     *     </ul>
     *   </li>
     *   <li>Define primary key constraint on the auto-increment column.</li>
     *   <li>DDL executed with {@link JdbcTemplate#execute(String)}.</li>
     * </ul>
     *
     * @param maxLengths map of entity names to maps of attribute names and their maximum lengths
     */
    public void generateSchema(Map<String, Map<String, Integer>> maxLengths) {
        maxLengths.forEach((entity, lengths) -> {
            String pk = entity.toLowerCase() + "_pk_id";
            StringBuilder ddl = new StringBuilder()
                    .append("CREATE TABLE IF NOT EXISTS ").append(entity).append(" (\n")
                    .append("  ").append(dialect.autoIncrementPrimaryKey(pk));
            lengths.forEach((col, max) -> {
                String columnName = entity.toUpperCase() + "_" + col.toUpperCase().replace(":", "_");
                String type = max > 255 ? dialect.clobType() : dialect.varcharType(Math.max(max, 1));
                ddl.append(",\n  ").append(columnName).append(" ").append(type);
            });
            ddl.append(",\n  PRIMARY KEY(").append(pk).append(")\n)");
            jdbcTemplate.execute(ddl.toString());
        });
    }
}
