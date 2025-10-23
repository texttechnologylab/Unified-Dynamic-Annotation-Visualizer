// /charts/ChartHandler.java
package org.texttechnologylab.udav.api.charts;

import com.fasterxml.jackson.databind.JsonNode;
import org.texttechnologylab.udav.api.ValueMode;

import java.util.Map;
import java.util.Set;

public interface ChartHandler {
    JsonNode render(String generatorId,
                    Map<String, String> filters,
                    Set<String> files,
                    ValueMode valueMode,
                    String schema);
}
