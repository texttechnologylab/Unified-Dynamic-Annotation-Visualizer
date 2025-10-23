package org.texttechnologylab.udav.importer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.db")
public class DbProps {
    private String url;
    private String user;
    private String pass;
    private String schema = "public";
    private int batchSize = 2000;
    private int maxIdent = 255;
    private String dialect = "POSTGRES";
}
