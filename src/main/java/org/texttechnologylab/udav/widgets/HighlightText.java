package org.texttechnologylab.udav.widgets;

import com.fasterxml.jackson.databind.JsonNode;

public class HighlightText extends Widget {

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
        // Collect all underline colors
        java.util.Set<String> underlineColors = new java.util.HashSet<>();
        java.util.Set<String> bgColors = new java.util.HashSet<>();
        JsonNode spans = dataNode.get("spans");
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
            String text = span.has("TEXT") ? span.get("TEXT").asText() : "";
            String style = span.has("style") ? span.get("style").asText() : null;
            String latexSpan;
            String underlineColor = null;
            String bgColor = null;
            if (style != null) {
                String[] styleParts = style.split(";");
                for (String part : styleParts) {
                    part = part.trim();
                    if (part.startsWith("text-decoration: underline")) {
                        int colorIdx = part.indexOf('#');
                        if (colorIdx != -1) {
                            underlineColor = part.substring(colorIdx, Math.min(colorIdx + 7, part.length())).replace("#", "").toUpperCase();
                        }
                    } else if (part.startsWith("background-color:")) {
                        int colorIdx = part.indexOf('#');
                        if (colorIdx != -1) {
                            bgColor = part.substring(colorIdx, Math.min(colorIdx + 7, part.length())).replace("#", "").toUpperCase();
                        }
                    }
                }
                // Build LaTeX for this span
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
                    latexSpan = String.format("\\colorbox{bgcolor%s}{%s}", bgColor, escapeLatex(text));
                } else {
                    latexSpan = escapeLatex(text);
                }
            } else {
                latexSpan = escapeLatex(text);
            }
            latex.append(latexSpan).append(nl);
        }
        latex.append("\\endgroup").append(nl);
        latex.append("\\end{document}").append(nl);
        return latex.toString();
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
}
