package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.texttechnologylab.udav.api.Repositories.GeneratorDataRepository;
import org.texttechnologylab.udav.api.ValueMode;
import org.texttechnologylab.udav.api.charts.ChartHandler;

import java.util.*;
import java.util.stream.Collectors;

@Component("HighlightText")
public class HighlightText extends Widget {

    public HighlightText(GeneratorDataRepository repo, ObjectMapper mapper) { super(repo, mapper); }

    /**
     * Converts the given JsonNode (with a 'data' node containing 'spans') to a LaTeX string.
     * Supports underlining and highlighting (background color) for each text span.
     *
     * @param jsonNode the input JSON node
     * @return LaTeX string with formatting
     */
    public String toTex(JsonNode jsonNode) {
        JsonNode dataNode = jsonNode.get("data");
        if (dataNode == null || !dataNode.has("spans")) {
            return null;
        }
        StringBuilder latex = new StringBuilder();
        String nl = System.lineSeparator();

        Set<String> underlineColors = new HashSet<>();
        Set<String> bgColors = new HashSet<>();
        JsonNode spans = dataNode.get("spans");

        // Collect colors
        for (JsonNode span : spans) {
            String style = span.has("style") ? span.get("style").asText() : null;
            if (style != null) {
                String[] styleParts = style.split(";");
                for (String part : styleParts) {
                    part = part.trim();
                    if (part.startsWith("text-decoration: underline")) {
                        int colorIdx = part.indexOf('#');
                        if (colorIdx != -1) {
                            String color = part.substring(colorIdx, Math.min(colorIdx + 7, part.length()));
                            underlineColors.add(color.replace("#", "").toUpperCase());
                        }
                    } else if (part.startsWith("background-color:")) {
                        int colorIdx = part.indexOf('#');
                        if (colorIdx != -1) {
                            String color = part.substring(colorIdx, Math.min(colorIdx + 7, part.length()));
                            bgColors.add(color.replace("#", "").toUpperCase());
                        }
                    }
                }
            }
        }
        // Add full LaTeX document structure
        latex.append("\\documentclass{article}").append(nl);
        latex.append("\\usepackage{soul}").append(nl);
        latex.append("\\usepackage{xcolor}").append(nl);
        // Define all underline and background colors
        for (String color : underlineColors) {
            latex.append(String.format("\\definecolor{ulcolor%s}{HTML}{%s}", color, color)).append(nl);
        }
        for (String color : bgColors) {
            latex.append(String.format("\\definecolor{bgcolor%s}{HTML}{%s}", color, color)).append(nl);
        }
        latex.append("\\begin{document}").append(nl);
        latex.append("\\begingroup").append(nl);
        for (JsonNode span : spans) {
            String text = span.has("text") ? span.get("text").asText() : "";
            String style = span.has("style") ? span.get("style").asText() : null;
            String underlineColor = null;
            String bgColor = null;
            if (style != null) {
                String[] styleParts = style.split(";");
                for (String part : styleParts) {
                    part = part.trim();
                    if (part.startsWith("text-decoration: underline")) {
                        int colorIdx = part.indexOf('#');
                        if (colorIdx != -1) {
                            underlineColor = part.substring(colorIdx,
                                            Math.min(colorIdx + 7, part.length()))
                                    .replace("#", "")
                                    .toUpperCase();
                        }
                    } else if (part.startsWith("background-color:")) {
                        int colorIdx = part.indexOf('#');
                        if (colorIdx != -1) {
                            bgColor = part.substring(colorIdx,
                                            Math.min(colorIdx + 7, part.length()))
                                    .replace("#", "")
                                    .toUpperCase();
                        }
                    }
                }
            }

            String latexSpan;

            if (underlineColor != null && bgColor != null) {
                // Highlight and colored underline (text stays black)
                latexSpan = String.format(
                        "\\colorbox{bgcolor%s}{\\setulcolor{ulcolor%s}\\ul{%s}\\setulcolor{black}}",
                        bgColor, underlineColor, escapeLatex(text)
                );
            } else if (underlineColor != null) {
                // Only colored underline (text stays black)
                latexSpan = String.format(
                        "\\setulcolor{ulcolor%s}\\ul{%s}\\setulcolor{black}",
                        underlineColor, escapeLatex(text)
                );
            } else if (bgColor != null) {
                // Only highlight
                latexSpan = String.format(
                        "\\colorbox{bgcolor%s}{%s}",
                        bgColor,
                        escapeLatex(text)
                );
            } else {
                latexSpan = escapeLatex(text);
            }
            latex.append(latexSpan).append(nl);
        }
        latex.append("\\endgroup").append(nl);
        latex.append("\\end{document}").append(nl);
        // Remove all lines that only contain spaces (or are empty)
        String filteredLatex = Arrays.stream(latex.toString().split("\\R"))
                .filter(line -> !line.trim().isEmpty())
                .reduce((a, b) -> a + nl + b)
                .orElse("");
        return filteredLatex;
    }

    /**
     * Escapes LaTeX special characters in the input string.
     */
    private String escapeLatex(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\textbackslash{}")
                .replace("$", "\\$")
                .replace("&", "\\&")
                .replace("%", "\\%")
                .replace("#", "\\#")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}")
                .replace("<", "\\textless{}")
                .replace(">", "\\textgreater{}")
                ;
    }

    private static String styletoCss(String styleName, String color) {
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
                           Set<String> files,
                           ValueMode vm,
                           String schema) {

        boolean typesProvided = filters.containsKey("types");
        boolean categoriesProvided = filters.containsKey("categories");
        boolean stylesProvided = filters.containsKey("styles");
        boolean hideProvided = filters.containsKey("hide");

        List<String> hide = null;
        if (hideProvided) {
            String hideStr = filters.get("hide");
            if (hideStr != null && !hideStr.isEmpty()) {
                hide = Arrays.asList(hideStr.split(","));
            }
        }

        Set<String> includeTypes = ChartHandler.parseCsvSet(filters.get("types"));
        Set<String> includeCategories = ChartHandler.parseCsvSet(filters.get("categories"));
        Set<String> includeStyles = ChartHandler.parseCsvSet(filters.get("styles"))
                .stream().map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Load base data (schema-aware)
        String text = repo.loadText(schema, generatorId).orElse("");
        Map<String, String> typeStyles = repo.loadTypeStyles(schema, generatorId);
        Map<String, Map<String, String>> typeCategoryColors = repo.loadTypeCategoryColors(schema, generatorId);

        if (hide != null) for (String h : hide) { typeStyles.remove(h); typeCategoryColors.remove(h); }

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

        record Label(String text, String style) {}
        record Ev(int idx, boolean start, String styleCss, Label label) {}

        List<Ev> evs = new ArrayList<>(segs.size() * 2);

        for (var s : segs) {

            String styleName = typeStyles.getOrDefault(s.type(), "");
            String color = Optional.ofNullable(typeCategoryColors.get(s.type()))
                    .map(m -> m.get(s.category()))
                    .orElse("#000000");

            String css = styletoCss(styleName, color);
            Label label = typeStyles.containsKey(s.type())
                    ? new Label(s.category(), "color: " + color + ";")
                    : null;

            int b = Math.max(0, Math.min(N, s.begin()));
            int e = Math.max(0, Math.min(N, s.end()));
            if (e < b) { int tmp = b; b = e; e = tmp; }

            evs.add(new Ev(b, true, css, label));
            evs.add(new Ev(e, false, css, label));
        }

        evs.sort(Comparator.<Ev>comparingInt(Ev::idx)
                .thenComparing(ev -> ev.start ? 1 : 0));

        // Track active labels by their text for O(1) add/remove
        Map<String, Label> activeLblMap = new LinkedHashMap<>();
        List<String> activeCss = new ArrayList<>();
        ArrayNode spans = mapper.createArrayNode();
        int last = 0;

        java.util.function.BiConsumer<Integer, Integer> addSpan = (startIdx, endIdx) -> {
            if (startIdx >= endIdx) return;
            ObjectNode span = mapper.createObjectNode();
            span.put("text", text.substring(startIdx, endIdx));

            if (!activeCss.isEmpty()) span.put("style", String.join(" ", activeCss));

            if (!activeLblMap.isEmpty()) {
                ArrayNode labelArr = mapper.createArrayNode();
                for (Label l : activeLblMap.values()) {
                    ObjectNode lnode = mapper.createObjectNode();
                    lnode.put("text", l.text());
                    lnode.put("style", l.style());
                    labelArr.add(lnode);
                }
                span.set("label", labelArr);
            }

            spans.add(span);
        };

        for (Ev e : evs) {
            addSpan.accept(last, e.idx);

            if (e.start) {
                if (!activeCss.contains(e.styleCss)) activeCss.add(e.styleCss);
                if (e.label != null) activeLblMap.putIfAbsent(e.label.text(), e.label);
            } else {
                activeCss.remove(e.styleCss);
                if (e.label != null) activeLblMap.remove(e.label.text());
            }

            last = e.idx;
        }

        addSpan.accept(last, N);

        // Datasets section
        ArrayNode datasets = mapper.createArrayNode();
        for (String t : typeStyles.keySet()) {
            ObjectNode d = mapper.createObjectNode();
            d.put("name", t);
            datasets.add(d);
        }

        ObjectNode root = mapper.createObjectNode();
        root.set("spans", spans);
        root.set("datasets", datasets);

        return root;
    }
}
