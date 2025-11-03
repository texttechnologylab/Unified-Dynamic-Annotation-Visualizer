package org.texttechnologylab.udav.importer.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.importer.EntityRecord;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for inserting batches of EntityRecord objects into database tables.
 * Groups records by their tag (table name), validates identifiers,
 * constructs batch-insert SQL statements, and executes them.
 */
@Service
public class DataInserterService {
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Construct service with a NamedParameterJdbcTemplate.
     *
     * @param jdbc the JDBC template for named-parameter access
     */
    public DataInserterService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Validate that the given identifier matches SQL naming rules.
     *
     * @param id the identifier to validate
     * @throws IllegalArgumentException if identifier is invalid
     */
    private void validate(String id) {
        if (!IDENT.matcher(id).matches()) throw new IllegalArgumentException(id);
    }

    /**
     * Insert a list of EntityRecord into their respective tables.
     * <ul>
     *   <li>Group records by tag (table name).</li>
     *   <li>Validate table names and column names against IDENT pattern.</li>
     *   <li>Generate column list and parameter list prefixed with table name.</li>
     *   <li>Execute batch update for each group.</li>
     * </ul>
     *
     * @param records list of EntityRecord instances to insert
     * @throws IllegalArgumentException if any table or column name is invalid
     */
    public void insertRecords(List<EntityRecord> records) {
        var grouped = records.stream().collect(Collectors.groupingBy(EntityRecord::tag));
        for (var entry : grouped.entrySet()) {
            String table = entry.getKey();
            List<EntityRecord> list = entry.getValue();
            if (list.isEmpty()) continue;
            validate(table);
            List<String> cols = list.stream()
                    .flatMap(r -> r.attributes().keySet().stream())
                    .distinct()
                    .toList();
            cols.forEach(this::validate);

            String prefix = table.toUpperCase();
            List<String> dbCols = cols.stream()
                    .map(c -> prefix + "_" + c.toUpperCase().replace(":", "_"))
                    .toList();

            String colCsv = String.join(",", dbCols);
            String paramCsv = dbCols.stream().map(c -> ":" + c).collect(Collectors.joining(","));
            String sql = "INSERT INTO " + table + "(" + colCsv + ") VALUES(" + paramCsv + ")";

            var params = list.stream().map(rec -> {
                var src = new MapSqlParameterSource();
                cols.forEach(col -> {
                    String dbCol = prefix + "_" + col.toUpperCase().replace(":", "_");
                    src.addValue(dbCol, rec.attributes().get(col));
                });
                return src;
            }).toArray(MapSqlParameterSource[]::new);


            jdbc.batchUpdate(sql, params);
        }
    }
}

