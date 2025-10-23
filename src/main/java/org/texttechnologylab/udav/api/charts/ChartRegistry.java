// /charts/ChartRegistry.java
package org.texttechnologylab.udav.api.charts;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

// ChartRegistry.java
@Component
@RequiredArgsConstructor
public class ChartRegistry {

    private final Map<String, ChartHandler> handlers;

    public boolean has(String type) {
        return handlers.containsKey(type);
    }

    public ChartHandler get(String type) {
        ChartHandler h = handlers.get(type);
        if (h == null) throw new IllegalArgumentException("Unknown chart type: " + type);
        return h;
    }
}

