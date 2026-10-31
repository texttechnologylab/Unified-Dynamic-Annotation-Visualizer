package org.texttechnologylab.udav.generators.common_properties;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommonFeatureCategoryColorsTest {

    private static Map<String, Color> colorsFor(Map<String, Double> counts) {
        CommonFeatureCategoryColors colors = new CommonFeatureCategoryColors();
        colors.addFeatureToCategoryCountMap("POS", counts);
        return colors.getCategoryColorMap("POS");
    }

    @Test
    void colorsBeyondThePaletteAreTheSameInEveryRun() {
        Map<String, Double> counts = new HashMap<>();
        for (int i = 0; i < 30; i++) counts.put("CAT" + i, (double) i);

        Map<String, Color> first = colorsFor(counts);
        assertEquals(30, first.size());
        assertEquals(first, colorsFor(counts));
    }
}
