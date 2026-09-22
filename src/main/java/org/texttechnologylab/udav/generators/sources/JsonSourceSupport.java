package org.texttechnologylab.udav.generators.sources;

import org.texttechnologylab.udav.generators.settings.FilterList;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.pipeline.JSONView;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Helpers shared by generators that read their data from a JSON source (a file imported into the
 * {@code json_data} table) instead of from UIMA annotation tables.
 *
 * <p>The mapping grammar is the one {@code MapCoordinates} already uses: an optional {@code keys}
 * or {@code keysMap} setting renames source fields to the generator's own field names, and
 * {@code fixedKeys} injects constants into every row. Without a mapping, rows are read as they are.
 */
public final class JsonSourceSupport {

    private JsonSourceSupport() {
    }

    /**
     * Rows of a list-root JSON document.
     *
     * <p>With a non-empty {@code keys} or {@code keysMap} setting the {@link SourceJson} mapping
     * grammar is applied exactly as for {@code MapCoordinates}; otherwise every list item is taken
     * as it is. {@code fixedKeys} are merged into every row in both cases.
     */
    public static List<Map<String, Object>> rows(SourceJson source, GeneratorSettings settings) {
        JSONView root = source.getSingleFileJSONView();
        boolean mapped = nonEmpty(settings.getMapSetting("keysMap"))
                || (nonEmpty(settings.getMapSetting("keys")) && root.isList());
        if (mapped) {
            return source.generateKeysMap(settings);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!root.isList()) return rows;
        Map<?, ?> fixed = settings.getMapSettingOrDefault("fixedKeys", null);
        for (Object item : root.asList()) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            map.forEach((k, v) -> row.put(String.valueOf(k), v));
            if (fixed != null) fixed.forEach((k, v) -> row.put(String.valueOf(k), v));
            rows.add(row);
        }
        return rows;
    }

    /** True for a map that is present and has at least one entry (the editor stores {@code {}} for "unset"). */
    public static boolean nonEmpty(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    /**
     * Label under which the generator files its data: the {@code file} setting if present, else
     * the bare source file name (without the {@code sourcefilesJSON/} prefix).
     */
    public static String defaultFileLabel(SourceJson source, GeneratorSettings settings) {
        String explicit = settings.getStringSettingOrDefault("file", null);
        if (explicit != null && !explicit.isBlank()) return explicit.trim();
        Map<?, ?> fixed = settings.getMapSettingOrDefault("fixedKeys", null);
        if (fixed != null && fixed.get("file") instanceof String s && !s.isBlank()) return s.trim();
        String name = source.getSingleFileName();
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    public static String string(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    public static Double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Integer integer(Object value) {
        Double d = number(value);
        if (d == null || d.isNaN() || d.isInfinite()) return null;
        return (int) Math.round(d);
    }

    /** Parses {@code #rrggbb}, {@code 0xrrggbb} or a plain decimal colour; null if absent or unparseable. */
    public static Color color(Object value) {
        if (!(value instanceof String s) || s.isBlank()) return null;
        try {
            return Color.decode(s.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** {@code {category: "#hex"}} from a settings map; entries that are not colours are dropped. */
    public static Map<String, Color> colorMap(Map<?, ?> map) {
        Map<String, Color> out = new HashMap<>();
        if (map == null) return out;
        map.forEach((k, v) -> {
            Color c = color(v);
            if (c != null) out.put(String.valueOf(k), c);
        });
        return out;
    }

    public static Map<String, String> stringMap(Map<?, ?> map) {
        Map<String, String> out = new HashMap<>();
        if (map == null) return out;
        map.forEach((k, v) -> {
            String s = string(v);
            if (s != null) out.put(String.valueOf(k), s);
        });
        return out;
    }

    /**
     * Applies a categories white/blacklist the way the UIMA branches do. Category filter values are
     * stored case-insensitively by {@link GeneratorSettings}, hence the upper-casing.
     */
    public static boolean categoryAllowed(FilterList<String> filter, String category) {
        if (filter == null || category == null) return true;
        String key = category.toUpperCase(Locale.ROOT);
        Set<String> whitelist = filter.getWhitelist();
        Set<String> blacklist = filter.getBlacklist();
        if (whitelist != null && !whitelist.isEmpty() && !containsIgnoreCase(whitelist, key)) return false;
        return blacklist == null || blacklist.isEmpty() || !containsIgnoreCase(blacklist, key);
    }

    private static boolean containsIgnoreCase(Set<String> set, String upperCased) {
        for (String s : set) {
            if (s != null && s.toUpperCase(Locale.ROOT).equals(upperCased)) return true;
        }
        return false;
    }
}
