package org.texttechnologylab.udav.api.charts;

import org.texttechnologylab.udav.api.ValueMode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ValueTransforms {
    private ValueTransforms() {
    }

    /**
     * Apply a value transform to category values.
     *
     * @param values        category -> value (input)
     * @param mode          RAW, SHARE, MAX1, ZSCORE, PER_FILE_AVG
     * @param perFileValues optional: filename -> (category -> value), required for PER_FILE_AVG (can be null otherwise)
     * @param restrictFiles optional: if non-empty, average only over these files in PER_FILE_AVG
     * @return transformed category -> value
     */
    public static Map<String, Double> apply(Map<String, Double> values,
                                            ValueMode mode,
                                            Map<String, Map<String, Double>> perFileValues,
                                            Set<String> restrictFiles) {
        if (values == null) values = Map.of();
        switch (mode) {
            case RAW -> {
                return new LinkedHashMap<>(values);
            }
            case SHARE -> {
                double sum = values.values().stream().mapToDouble(Double::doubleValue).sum();
                double denom = (sum == 0) ? 1.0 : sum;
                return scale(values, v -> v / denom);
            }
            case MAX1 -> {
                double max = values.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double denom = (max == 0) ? 1.0 : max;
                return scale(values, v -> v / denom);
            }
            case ZSCORE -> {
                int n = values.size();
                if (n == 0) return new LinkedHashMap<>(values);
                double mean = values.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double var = values.values().stream().mapToDouble(v -> {
                    double d = v - mean;
                    return d * d;
                }).sum() / Math.max(1, n - 1);
                double sd = Math.sqrt(var);
                double denom = (sd == 0) ? 1.0 : sd;
                return transform(values, v -> (v - mean) / denom);
            }
            case PER_FILE_AVG -> {
                if (perFileValues == null || perFileValues.isEmpty()) {
                    // No per-file data: fall back to RAW
                    return new LinkedHashMap<>(values);
                }
                // select files
                Map<String, Map<String, Double>> filesToUse;
                if (restrictFiles != null && !restrictFiles.isEmpty()) {
                    filesToUse = perFileValues.entrySet().stream()
                            .filter(e -> restrictFiles.contains(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                    (a, b) -> a, LinkedHashMap::new));
                } else {
                    filesToUse = perFileValues;
                }
                int fileCount = filesToUse.size();
                if (fileCount == 0) return values.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> 0.0, (a, b) -> a, LinkedHashMap::new));

                // sum per category over files
                Map<String, Double> sum = new LinkedHashMap<>();
                for (var file : filesToUse.values()) {
                    for (var e : file.entrySet()) {
                        sum.merge(e.getKey(), e.getValue(), Double::sum);
                    }
                }
                // average
                Map<String, Double> avg = new LinkedHashMap<>();
                for (var e : sum.entrySet()) {
                    avg.put(e.getKey(), e.getValue() / fileCount);
                }
                return avg;
            }
        }
        // unreachable
        return values;
    }

    public static List<Map.Entry<String, Double>> sortLimitFilter(Map<String, Double> values,
                                                                  String sortKey,
                                                                  boolean desc,
                                                                  Double min, Double max,
                                                                  Integer limit) {
        double minV = (min == null) ? Double.NEGATIVE_INFINITY : min;
        double maxV = (max == null) ? Double.POSITIVE_INFINITY : max;

        var stream = values.entrySet().stream()
                .filter(e -> e.getValue() >= minV && e.getValue() <= maxV);

        Comparator<Map.Entry<String, Double>> cmp =
                "label".equalsIgnoreCase(sortKey)
                        ? Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)
                        : Map.Entry.comparingByValue();
        if (desc) cmp = cmp.reversed();

        var sorted = stream.sorted(cmp).toList();
        if (limit != null && limit >= 0 && limit < sorted.size()) return sorted.subList(0, limit);
        return sorted;
    }

    private static Map<String, Double> scale(Map<String, Double> values, Function<Double, Double> f) {
        return transform(values, f);
    }

    private static Map<String, Double> transform(Map<String, Double> values, Function<Double, Double> f) {
        Map<String, Double> out = new LinkedHashMap<>(values.size());
        for (var e : values.entrySet()) out.put(e.getKey(), f.apply(e.getValue()));
        return out;
    }
}
