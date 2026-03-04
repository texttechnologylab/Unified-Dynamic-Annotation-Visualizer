package org.texttechnologylab.udav.widgets.tools;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class SvgToTikzConverter {

    public static String convertSvgToTikz(String svg) {
        StringBuilder tikz = new StringBuilder();

        // Extract SVG height
        double svgHeight = extractHeight(svg);

        // Extract translate
        double[] translate = extractTranslate(svg);
        double tx = translate[0];
        double ty = translate[1];

        tikz.append("\\documentclass[tikz,border=5pt]{standalone}\n");
        tikz.append("\\usepackage{xcolor}\n");
        tikz.append("\\usepackage{tikz}\n\n");

        // Add color definitions for non-standard color names
        String colorDefinitions = getLatexColorDefinitions(svg);
        tikz.append(colorDefinitions);

        tikz.append("\n\\begin{document}\n\n");
        tikz.append("\\begin{tikzpicture}[x=1pt,y=1pt]\n\n");

        tikz.append("% SVG height = ").append(svgHeight).append("\n");
        tikz.append("% translate(" + tx + "," + ty + ") and y-flip applied\n");
        tikz.append("\\begin{scope}[shift={(" + tx + "," +
                (svgHeight - ty) + ")}, yscale=-1]\n\n");

        // Convert paths
        convertPaths(svg, tikz);

        // Convert lines
        convertLines(svg, tikz);

        // Convert circles
        convertCircles(svg, tikz);

        tikz.append("\n\\end{scope}\n");
        tikz.append("\\end{tikzpicture}\n\n");
        tikz.append("\\end{document}\n");

        return tikz.toString();
    }

    private static double extractHeight(String svg) {
        Matcher m = Pattern.compile("height=\"([0-9.]+)\"").matcher(svg);
        if (m.find()) return Double.parseDouble(m.group(1));
        return 0;
    }

    private static double[] extractTranslate(String svg) {
        Matcher m = Pattern.compile("translate\\(([^,]+),([^\\)]+)\\)").matcher(svg);
        if (m.find()) {
            return new double[]{
                    Double.parseDouble(m.group(1)),
                    Double.parseDouble(m.group(2))
            };
        }
        return new double[]{0, 0};
    }

    private static void convertPaths(String svg, StringBuilder tikz) {
        Matcher m = Pattern.compile("<path([^>]*)>").matcher(svg);
        while (m.find()) {
            String attrs = m.group(1);
            String d = extractString(attrs, "d");
            String stroke = extractString(attrs, "stroke");
            String strokeWidth = extractString(attrs, "stroke-width");
            if (stroke == null || stroke.equals("none")) continue;
            if (strokeWidth == null) strokeWidth = "1";
            String color = normalizeColor(stroke);
            if (d != null) {
                // Parse commands and coordinates
                Pattern cmdCoordPattern = Pattern.compile("([MLZmlz])|([0-9eE.+-]+),([0-9eE.+-]+)");
                Matcher cmdMatcher = cmdCoordPattern.matcher(d);
                double[] prev = null;
                double[] start = null;
                String lastCmd = null;
                while (cmdMatcher.find()) {
                    if (cmdMatcher.group(1) != null) {
                        lastCmd = cmdMatcher.group(1).toUpperCase();
                        if (lastCmd.equals("Z")) {
                            // Close path
                            if (prev != null && start != null) {
                                tikz.append("\\draw[color=" + color + ", line width=" + strokeWidth + "pt]\n");
                                tikz.append("(" + prev[0] + "," + prev[1] + ") --\n(" + start[0] + "," + start[1] + ");\n");
                            }
                        }
                    } else if (cmdMatcher.group(2) != null && cmdMatcher.group(3) != null) {
                        double x = Double.parseDouble(cmdMatcher.group(2));
                        double y = Double.parseDouble(cmdMatcher.group(3));
                        if (lastCmd == null) continue;
                        if (lastCmd.equals("M")) {
                            prev = new double[]{x, y};
                            start = new double[]{x, y};
                        } else if (lastCmd.equals("L")) {
                            if (prev != null) {
                                tikz.append("\\draw[color=" + color + ", line width=" + strokeWidth + "pt]\n");
                                tikz.append("(" + prev[0] + "," + prev[1] + ") --\n(" + x + "," + y + ");\n");
                            }
                            prev = new double[]{x, y};
                        }
                    }
                }
                tikz.append("\n");
            }
        }
    }

    private static void convertLines(String svg, StringBuilder tikz) {
        Matcher m = Pattern.compile(
                        "<line[^>]*x1=\"([^\"]+)\"[^>]*y1=\"([^\"]+)\"[^>]*x2=\"([^\"]+)\"[^>]*y2=\"([^\"]+)\"[^>]*stroke=\"([^\"]+)\"[^>]*stroke-width=\"([^\"]+)\"")
                .matcher(svg);

        while (m.find()) {
            double x1 = Double.parseDouble(m.group(1));
            double y1 = Double.parseDouble(m.group(2));
            double x2 = Double.parseDouble(m.group(3));
            double y2 = Double.parseDouble(m.group(4));
            String color = normalizeColor(m.group(5));
            String width = m.group(6);

            tikz.append("\\draw[" + color + ", line width=" + width + "pt]\n");
            tikz.append("(" + x1 + "," + y1 + ") --\n(" + x2 + "," + y2 + ");\n\n");
        }
    }

    private static void convertCircles(String svg, StringBuilder tikz) {
        Matcher m = Pattern.compile("<circle([^>]*)>").matcher(svg);

        while (m.find()) {
            String attrs = m.group(1);

            double cx = extractDouble(attrs, "cx");
            double cy = extractDouble(attrs, "cy");
            double r = extractDouble(attrs, "r");

            String stroke = extractString(attrs, "stroke");
            String fill = extractString(attrs, "fill");
            String strokeWidth = extractString(attrs, "stroke-width");

            if (strokeWidth == null) strokeWidth = "1";

            // Convert rgb to steelblue if matching
            if ("rgb(70,130,180)".equals(stroke)) {
                stroke = "steelblue";
            }

            if (fill != null && !fill.equals("none")) {
                tikz.append("\\fill[" + normalizeColor(fill) + "]\n");
                tikz.append("(" + cx + "," + cy + ") circle (" + r + ");\n\n");
            }

            if (stroke != null && !stroke.equals("none")) {
                tikz.append("\\draw[" + normalizeColor(stroke)
                        + ", line width=" + strokeWidth + "pt]\n");
                tikz.append("(" + cx + "," + cy + ") circle (" + r + ");\n\n");
            }
        }
    }

    private static String normalizeColor(String color) {
        if (color.startsWith("rgb")) return "black";
        return color;
    }

    private static double extractDouble(String attrs, String key) {
        Matcher m = Pattern.compile(key + "=\"([^\"]+)\"").matcher(attrs);
        if (m.find()) return Double.parseDouble(m.group(1));
        return 0;
    }

    private static String extractString(String attrs, String key) {
        Matcher m = Pattern.compile(key + "=\"([^\"]+)\"").matcher(attrs);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * Scans the SVG string for color names that are not directly usable in TikZ/xcolor,
     * and returns a string of \definecolor commands for those colors.
     */
    public static String getLatexColorDefinitions(String svg) {
        // Comprehensive CSS/SVG color name to RGB map
        Map<String, int[]> colorMap = Map.ofEntries(
            Map.entry("aliceblue", new int[]{240, 248, 255}),
            Map.entry("antiquewhite", new int[]{250, 235, 215}),
            Map.entry("aqua", new int[]{0, 255, 255}),
            Map.entry("aquamarine", new int[]{127, 255, 212}),
            Map.entry("azure", new int[]{240, 255, 255}),
            Map.entry("beige", new int[]{245, 245, 220}),
            Map.entry("bisque", new int[]{255, 228, 196}),
            Map.entry("black", new int[]{0, 0, 0}),
            Map.entry("blanchedalmond", new int[]{255, 235, 205}),
            Map.entry("blue", new int[]{0, 0, 255}),
            Map.entry("blueviolet", new int[]{138, 43, 226}),
            Map.entry("brown", new int[]{165, 42, 42}),
            Map.entry("burlywood", new int[]{222, 184, 135}),
            Map.entry("cadetblue", new int[]{95, 158, 160}),
            Map.entry("chartreuse", new int[]{127, 255, 0}),
            Map.entry("chocolate", new int[]{210, 105, 30}),
            Map.entry("coral", new int[]{255, 127, 80}),
            Map.entry("cornflowerblue", new int[]{100, 149, 237}),
            Map.entry("cornsilk", new int[]{255, 248, 220}),
            Map.entry("crimson", new int[]{220, 20, 60}),
            Map.entry("cyan", new int[]{0, 255, 255}),
            Map.entry("darkblue", new int[]{0, 0, 139}),
            Map.entry("darkcyan", new int[]{0, 139, 139}),
            Map.entry("darkgoldenrod", new int[]{184, 134, 11}),
            Map.entry("darkgray", new int[]{169, 169, 169}),
            Map.entry("darkgreen", new int[]{0, 100, 0}),
            Map.entry("darkgrey", new int[]{169, 169, 169}),
            Map.entry("darkkhaki", new int[]{189, 183, 107}),
            Map.entry("darkmagenta", new int[]{139, 0, 139}),
            Map.entry("darkolivegreen", new int[]{85, 107, 47}),
            Map.entry("darkorange", new int[]{255, 140, 0}),
            Map.entry("darkorchid", new int[]{153, 50, 204}),
            Map.entry("darkred", new int[]{139, 0, 0}),
            Map.entry("darksalmon", new int[]{233, 150, 122}),
            Map.entry("darkseagreen", new int[]{143, 188, 143}),
            Map.entry("darkslateblue", new int[]{72, 61, 139}),
            Map.entry("darkslategray", new int[]{47, 79, 79}),
            Map.entry("darkslategrey", new int[]{47, 79, 79}),
            Map.entry("darkturquoise", new int[]{0, 206, 209}),
            Map.entry("darkviolet", new int[]{148, 0, 211}),
            Map.entry("deeppink", new int[]{255, 20, 147}),
            Map.entry("deepskyblue", new int[]{0, 191, 255}),
            Map.entry("dimgray", new int[]{105, 105, 105}),
            Map.entry("dimgrey", new int[]{105, 105, 105}),
            Map.entry("dodgerblue", new int[]{30, 144, 255}),
            Map.entry("firebrick", new int[]{178, 34, 34}),
            Map.entry("floralwhite", new int[]{255, 250, 240}),
            Map.entry("forestgreen", new int[]{34, 139, 34}),
            Map.entry("fuchsia", new int[]{255, 0, 255}),
            Map.entry("gainsboro", new int[]{220, 220, 220}),
            Map.entry("ghostwhite", new int[]{248, 248, 255}),
            Map.entry("gold", new int[]{255, 215, 0}),
            Map.entry("goldenrod", new int[]{218, 165, 32}),
            Map.entry("gray", new int[]{128, 128, 128}),
            Map.entry("green", new int[]{0, 128, 0}),
            Map.entry("greenyellow", new int[]{173, 255, 47}),
            Map.entry("grey", new int[]{128, 128, 128}),
            Map.entry("honeydew", new int[]{240, 255, 240}),
            Map.entry("hotpink", new int[]{255, 105, 180}),
            Map.entry("indianred", new int[]{205, 92, 92}),
            Map.entry("indigo", new int[]{75, 0, 130}),
            Map.entry("ivory", new int[]{255, 255, 240}),
            Map.entry("khaki", new int[]{240, 230, 140}),
            Map.entry("lavender", new int[]{230, 230, 250}),
            Map.entry("lavenderblush", new int[]{255, 240, 245}),
            Map.entry("lawngreen", new int[]{124, 252, 0}),
            Map.entry("lemonchiffon", new int[]{255, 250, 205}),
            Map.entry("lightblue", new int[]{173, 216, 230}),
            Map.entry("lightcoral", new int[]{240, 128, 128}),
            Map.entry("lightcyan", new int[]{224, 255, 255}),
            Map.entry("lightgoldenrodyellow", new int[]{250, 250, 210}),
            Map.entry("lightgray", new int[]{211, 211, 211}),
            Map.entry("lightgreen", new int[]{144, 238, 144}),
            Map.entry("lightgrey", new int[]{211, 211, 211}),
            Map.entry("lightpink", new int[]{255, 182, 193}),
            Map.entry("lightsalmon", new int[]{255, 160, 122}),
            Map.entry("lightseagreen", new int[]{32, 178, 170}),
            Map.entry("lightskyblue", new int[]{135, 206, 250}),
            Map.entry("lightslategray", new int[]{119, 136, 153}),
            Map.entry("lightslategrey", new int[]{119, 136, 153}),
            Map.entry("lightsteelblue", new int[]{176, 196, 222}),
            Map.entry("lightyellow", new int[]{255, 255, 224}),
            Map.entry("lime", new int[]{0, 255, 0}),
            Map.entry("limegreen", new int[]{50, 205, 50}),
            Map.entry("linen", new int[]{250, 240, 230}),
            Map.entry("magenta", new int[]{255, 0, 255}),
            Map.entry("maroon", new int[]{128, 0, 0}),
            Map.entry("mediumaquamarine", new int[]{102, 205, 170}),
            Map.entry("mediumblue", new int[]{0, 0, 205}),
            Map.entry("mediumorchid", new int[]{186, 85, 211}),
            Map.entry("mediumpurple", new int[]{147, 112, 219}),
            Map.entry("mediumseagreen", new int[]{60, 179, 113}),
            Map.entry("mediumslateblue", new int[]{123, 104, 238}),
            Map.entry("mediumspringgreen", new int[]{0, 250, 154}),
            Map.entry("mediumturquoise", new int[]{72, 209, 204}),
            Map.entry("mediumvioletred", new int[]{199, 21, 133}),
            Map.entry("midnightblue", new int[]{25, 25, 112}),
            Map.entry("mintcream", new int[]{245, 255, 250}),
            Map.entry("mistyrose", new int[]{255, 228, 225}),
            Map.entry("moccasin", new int[]{255, 228, 181}),
            Map.entry("navajowhite", new int[]{255, 222, 173}),
            Map.entry("navy", new int[]{0, 0, 128}),
            Map.entry("oldlace", new int[]{253, 245, 230}),
            Map.entry("olive", new int[]{128, 128, 0}),
            Map.entry("olivedrab", new int[]{107, 142, 35}),
            Map.entry("orange", new int[]{255, 165, 0}),
            Map.entry("orangered", new int[]{255, 69, 0}),
            Map.entry("orchid", new int[]{218, 112, 214}),
            Map.entry("palegoldenrod", new int[]{238, 232, 170}),
            Map.entry("palegreen", new int[]{152, 251, 152}),
            Map.entry("paleturquoise", new int[]{175, 238, 238}),
            Map.entry("palevioletred", new int[]{219, 112, 147}),
            Map.entry("papayawhip", new int[]{255, 239, 213}),
            Map.entry("peachpuff", new int[]{255, 218, 185}),
            Map.entry("peru", new int[]{205, 133, 63}),
            Map.entry("pink", new int[]{255, 192, 203}),
            Map.entry("plum", new int[]{221, 160, 221}),
            Map.entry("powderblue", new int[]{176, 224, 230}),
            Map.entry("purple", new int[]{128, 0, 128}),
            Map.entry("rebeccapurple", new int[]{102, 51, 153}),
            Map.entry("red", new int[]{255, 0, 0}),
            Map.entry("rosybrown", new int[]{188, 143, 143}),
            Map.entry("royalblue", new int[]{65, 105, 225}),
            Map.entry("saddlebrown", new int[]{139, 69, 19}),
            Map.entry("salmon", new int[]{250, 128, 114}),
            Map.entry("sandybrown", new int[]{244, 164, 96}),
            Map.entry("seagreen", new int[]{46, 139, 87}),
            Map.entry("seashell", new int[]{255, 245, 238}),
            Map.entry("sienna", new int[]{160, 82, 45}),
            Map.entry("silver", new int[]{192, 192, 192}),
            Map.entry("skyblue", new int[]{135, 206, 235}),
            Map.entry("slateblue", new int[]{106, 90, 205}),
            Map.entry("slategray", new int[]{112, 128, 144}),
            Map.entry("slategrey", new int[]{112, 128, 144}),
            Map.entry("snow", new int[]{255, 250, 250}),
            Map.entry("springgreen", new int[]{0, 255, 127}),
            Map.entry("steelblue", new int[]{70, 130, 180}),
            Map.entry("tan", new int[]{210, 180, 140}),
            Map.entry("teal", new int[]{0, 128, 128}),
            Map.entry("thistle", new int[]{216, 191, 216}),
            Map.entry("tomato", new int[]{255, 99, 71}),
            Map.entry("turquoise", new int[]{64, 224, 208}),
            Map.entry("violet", new int[]{238, 130, 238}),
            Map.entry("wheat", new int[]{245, 222, 179}),
            Map.entry("white", new int[]{255, 255, 255}),
            Map.entry("whitesmoke", new int[]{245, 245, 245}),
            Map.entry("yellow", new int[]{255, 255, 0}),
            Map.entry("yellowgreen", new int[]{154, 205, 50})
        );

        // Find all color usages in fill, stroke, style attributes
        Set<String> foundColors = new HashSet<>();
        Pattern colorPattern = Pattern.compile(
            "(?:fill|stroke|color)\\s*=\\s*\"([#a-zA-Z0-9]+)\"|(?:fill|stroke|color):\\s*([#a-zA-Z0-9]+)[;\\\"]"
        );
        Matcher matcher = colorPattern.matcher(svg);
        while (matcher.find()) {
            String color = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (color == null) continue;
            color = color.trim().toLowerCase();
            // Only add named colors (not #hex or rgb)
            if (!color.startsWith("#") && !color.startsWith("rgb") && colorMap.containsKey(color)) {
                foundColors.add(color);
            }
        }
        // Build \definecolor commands
        StringBuilder defs = new StringBuilder();
        for (String color : foundColors) {
            int[] rgb = colorMap.get(color);
            defs.append(String.format("\\definecolor{%s}{RGB}{%d,%d,%d}\n", color, rgb[0], rgb[1], rgb[2]));
        }
        return defs.toString();
    }
}