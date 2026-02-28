package org.texttechnologylab.udav.importer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.db")
public class DbProps {
    private String url = "jdbc:postgresql://localhost:5432/postgres";
    private String user = "postgres";
    private String pass = "postgres";
    private String schema = "public";
    private int batchSize = 5000;
    private int maxIdent = 255;
    private String dialect = "POSTGRES";
}
