package org.texttechnologylab.udav.generators.common_properties;

import lombok.Getter;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class CommonFeatureCategoryColors extends CommonProperties {
    private Map<String, Map<String, Double>> featureToCategoryCountMap;
    private Map<String, Map<String, Color>> featureToCategoryColorMap;

    public CommonFeatureCategoryColors() {
        super();
        featureToCategoryCountMap = new HashMap<>();
        featureToCategoryColorMap = new HashMap<>();
    }


    public Map<String, Color> getCategoryColorMap(String feature) {
        if (featureToCategoryColorMap.containsKey(feature)) return new HashMap<>(featureToCategoryColorMap.get(feature));
        if (!featureToCategoryCountMap.containsKey(feature)) throw new IllegalArgumentException("Unknown key \"" + feature + "\" in featureToCategoryCountMap.");
        Map<String, Color> colorMap = categoryColorMapFromCategoriesNumberMap(featureToCategoryCountMap.get(feature));
        featureToCategoryColorMap.put(feature, colorMap);
        return new HashMap<>(colorMap);
    }

    public void addFeatureToCategoryCountMap(String feature, Map<String, Double> categoryCountMap) {
        if (featureToCategoryCountMap.containsKey(feature)) {
            Map<String, Double> addCategoryCountsTogether = Stream.concat(categoryCountMap.entrySet().stream(), featureToCategoryCountMap.get(feature).entrySet().stream())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Double::sum));
            featureToCategoryCountMap.put(feature, addCategoryCountsTogether);
        } else {
            featureToCategoryCountMap.put(feature, new HashMap<>(categoryCountMap));
        }
    }

    private static Map<String, Color> categoryColorMapFromCategoriesNumberMap(Map<String, Double> categoryNumberMap) {
        List<Color> distinctColors = Arrays.asList(
                Color.RED,
                Color.BLUE,
                Color.GREEN,
                Color.MAGENTA,
                Color.ORANGE,
                Color.CYAN,
                Color.YELLOW,
                Color.PINK,
                Color.GRAY,
                new Color(0, 128, 128),
                new Color(128, 0, 128),
                new Color(128, 128, 0),
                new Color(0, 0, 128),
                new Color(255, 105, 180),
                new Color(139, 69, 19),
                new Color(0, 255, 127),
                new Color(255, 165, 0),
                new Color(0, 191, 255),
                new Color(154, 205, 50)
        );

        HashMap<String, Color> categoryColorMap = new HashMap<>();
        Iterator<Color> colorIterator = distinctColors.iterator();

        List<String> sortedCategories = categoryNumberMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        for (String category : sortedCategories) {
            Color color;
            if (colorIterator.hasNext()) {
                color = colorIterator.next();
            } else {
                // Random colors if we run out of predefined colors
                color = new Color((int) (Math.random() * 0x1000000));
            }
            categoryColorMap.put(category, color);
        }

        return categoryColorMap;
    }
}
