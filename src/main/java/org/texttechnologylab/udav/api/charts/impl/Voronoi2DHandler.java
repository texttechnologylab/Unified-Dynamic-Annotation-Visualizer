package org.texttechnologylab.udav.api.charts.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartHandler;

import java.util.*;
import java.util.stream.Collectors;

@Component("Voronoi")
@RequiredArgsConstructor
public class Voronoi2DHandler implements ChartHandler {

    private final GeneratorDataRepository repo;
    private final ObjectMapper mapper;


    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,
                           ValueMode valueMode,
                           String schema) {

        // Which fields to emit (default: label,value,color)
        LinkedHashSet<String> attrs = ChartHandler.parseCsvSet(filters.getOrDefault("attrs", "label,value,color"))
                .stream().collect(Collectors.toCollection(LinkedHashSet::new));
        if (attrs.isEmpty()) attrs = new LinkedHashSet<>(List.of("label", "value", "color"));

        Map<String, List<GeneratorDataRepository.MapCoordinatesRow>> data = repo.loadMapCoordinatesByFile(schema, generatorId);

        ArrayNode out = mapper.createArrayNode();
        for (List<GeneratorDataRepository.MapCoordinatesRow> list : data.values()) {
            for (GeneratorDataRepository.MapCoordinatesRow d : list) {

            }
        }


        return null;
    }
}
