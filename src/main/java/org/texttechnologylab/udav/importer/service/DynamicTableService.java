package org.texttechnologylab.udav.importer.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

//@Service
//@ConditionalOnProperty(name = "app.database-generator.enabled", havingValue = "true", matchIfMissing = true)
//@Deprecated
public class DynamicTableService {
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;

    public DynamicTableService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> listTables() {
        return jdbc.queryForList(
                "SELECT table_name FROM TableNames",
                String.class
        );
    }

}
