package org.texttechnologylab.udav.api.charts.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartHandler;

import java.util.*;
import java.util.stream.Collectors;

@Component("HighlightText")
@RequiredArgsConstructor
public class TextChartHandler implements ChartHandler {

    private final ObjectMapper mapper;
    private final GeneratorDataRepository repo;


    private static String toCss(String styleName, String color) {
        String s = styleName == null ? "" : styleName.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "bold" -> "color: " + color + "; font-weight: bold;";
            case "underline" -> "text-decoration: underline 2px " + color + ";";
            case "highlight" -> "background-color: " + color + ";";
            default -> "";
        };
    }

    @Override
    public JsonNode render(String generatorId,
                           Map<String, String> filters,
                           Set<String> files,          // not used for text yet, kept for symmetry
                           ValueMode vm,               // not used (text is not numerically transformed)
                           String schema) {

        // Optional include filters
        boolean typesProvided = filters.containsKey("types");
        boolean categoriesProvided = filters.containsKey("categories");
        boolean stylesProvided = filters.containsKey("styles");

        Set<String> includeTypes = ChartHandler.parseCsvSet(filters.get("types"));
        Set<String> includeCategories = ChartHandler.parseCsvSet(filters.get("categories"));
        Set<String> includeStyles = ChartHandler.parseCsvSet(filters.get("styles"))
                .stream().map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Load base data (schema-aware)
        String text = repo.loadText(schema, generatorId).orElse("");
        Map<String, String> typeStyles = repo.loadTypeStyles(schema, generatorId); // type -> styleName
        Map<String, Map<String, String>> typeCategoryColors = repo.loadTypeCategoryColors(schema, generatorId); // type -> (category -> color)

        // Load all segments once; we'll filter in-memory (keeps repo simple)
        var segs = repo.loadSegments(schema, generatorId, null);

        // If key existed but list is empty → no segments
        if ((typesProvided && includeTypes.isEmpty()) || (categoriesProvided && includeCategories.isEmpty())) {
            segs = Collections.emptyList();
        } else {
            // Filter by type / category if provided
            if (typesProvided && !includeTypes.isEmpty()) {
                Set<String> tset = includeTypes;
                segs = segs.stream().filter(s -> tset.contains(s.type())).collect(Collectors.toList());
            }
            if (categoriesProvided && !includeCategories.isEmpty()) {
                Set<String> cset = includeCategories;
                segs = segs.stream().filter(s -> cset.contains(s.category())).collect(Collectors.toList());
            }
            // Optional style filter
            if (stylesProvided && !includeStyles.isEmpty()) {
                Set<String> sset = includeStyles;
                segs = segs.stream()
                        .filter(s -> sset.contains(
                                Optional.ofNullable(typeStyles.get(s.type()))
                                        .orElse("")
                                        .toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        // Build event list
        final int N = text.length();
        record Ev(int idx, boolean start, String styleCss, String labelHtml) {
        }
        List<Ev> evs = new ArrayList<>(segs.size() * 2);

        for (var s : segs) {
            String styleName = typeStyles.getOrDefault(s.type(), "");
            String color = Optional.ofNullable(typeCategoryColors.get(s.type()))
                    .map(m -> m.get(s.category()))
                    .orElse("#000000");

            String css = toCss(styleName, color);
            String labelHtml = "<span style=\"color: " + color + ";\">" + s.category() + "</span>";

            int b = Math.max(0, Math.min(N, s.begin()));
            int e = Math.max(0, Math.min(N, s.end()));
            if (e < b) {
                int tmp = b;
                b = e;
                e = tmp;
            }

            evs.add(new Ev(b, true, css, labelHtml));
            evs.add(new Ev(e, false, css, labelHtml));
        }

        // Sort by index; at the same idx, end before start
        evs.sort(Comparator.<Ev>comparingInt(Ev::idx)
                .thenComparing(ev -> ev.start ? 1 : 0));

        // Sweep line to build spans
        List<String> activeCss = new ArrayList<>();
        List<String> activeLbl = new ArrayList<>();
        int last = 0;
        ArrayNode spans = mapper.createArrayNode();

        for (Ev e : evs) {
            if (last < e.idx) {
                ObjectNode span = mapper.createObjectNode();
                span.put("TEXT", text.substring(last, e.idx));
                if (!activeCss.isEmpty()) span.put("style", String.join(" ", activeCss));
                if (!activeLbl.isEmpty()) span.put("label", String.join(" ", activeLbl));
                spans.add(span);
            }
            if (e.start) {
                if (!activeCss.contains(e.styleCss)) activeCss.add(e.styleCss);
                if (!activeLbl.contains(e.labelHtml)) activeLbl.add(e.labelHtml);
            } else {
                activeCss.remove(e.styleCss);
                activeLbl.remove(e.labelHtml);
            }
            last = e.idx;
        }
        if (last < N) {
            ObjectNode span = mapper.createObjectNode();
            span.put("TEXT", text.substring(last));
            if (!activeCss.isEmpty()) span.put("style", String.join(" ", activeCss));
            if (!activeLbl.isEmpty()) span.put("label", String.join(" ", activeLbl));
            spans.add(span);
        }

        // Optional "datasets" section per type (basic skeleton)
        ArrayNode datasets = mapper.createArrayNode();
        for (String t : typeStyles.keySet()) {
            ObjectNode d = mapper.createObjectNode();
            d.put("name", t);
            datasets.add(d);
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("generatorId", generatorId);
        root.put("textLength", N);
        root.set("spans", spans);
        root.set("datasets", datasets);
        return root;
    }
}
