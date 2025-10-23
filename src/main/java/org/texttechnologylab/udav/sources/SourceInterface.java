package org.texttechnologylab.udav.sources;

import org.texttechnologylab.udav.generators.Generator;

import java.sql.SQLException;
import java.util.Map;

public interface SourceInterface {
    Map<String, Generator> createGenerators() throws SQLException;
}
