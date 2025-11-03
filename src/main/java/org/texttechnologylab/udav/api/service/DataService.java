package org.texttechnologylab.udav.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.texttechnologylab.udav.api.DummyDataProvider;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartRegistry;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataService {

    private final ObjectMapper mapper;
    private final DummyDataProvider provider;
    private final ChartRegistry charts;

    public DataService(ObjectMapper mapper,
                       DummyDataProvider provider,
                       ChartRegistry charts) {
        this.mapper = mapper;
        this.provider = provider;
        this.charts = charts;
    }

    public String buildArrayJson(String id, String type,
                                 Map<String, String> filters,
                                 Map<String, String> corpus,
                                 boolean pretty,
                                 String schema) {

        Set<String> files = Optional.ofNullable(corpus)
                .map(m -> m.get("files"))
                .map(Parsing::parseCsvSet)
                .orElseGet(Collections::emptySet);

        ValueMode vm = ValueMode.from(filters.get("valueMode"));
        filters.remove("valueMode");

        // Prefer handler if present
        if (charts.has(type)) {
            JsonNode node = charts.get(type).render(id, filters, files, vm, schema);
            try {
                return pretty ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
                        : mapper.writeValueAsString(node);
            } catch (Exception e) {
                return "[]";
            }
        }

        // fallback to your legacy provider paths if no handler found
        return provider.getJsonFor(id, type);
    }

    // -------- parsing utils used by legacy filters --------
    static final class Parsing {
        static Set<String> parseCsvSet(String csv) {
            if (csv == null || csv.isBlank()) return Collections.emptySet();
            return Arrays.stream(csv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}
