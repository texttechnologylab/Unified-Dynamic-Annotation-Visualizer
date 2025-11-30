package org.texttechnologylab.udav.generators;

import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.SourceJson;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;


import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapCoordinates extends Generator {

    private List<Entry> entries;

    public MapCoordinates(String id, JSONView configGenerator, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
    }

    @Override
    public void setup() throws SQLException {
        entries = new ArrayList<>();
        if (SourceJson.class.equals(source.getClass())) {
            SourceJson sourceJson = (SourceJson) source;

            Map<String, Object> keysMap = (Map<String, Object>) settings.getMapSettingOrDefault("keysMap", null);
            List<Map<String, Object>> flatKeysMap = sourceJson.generateFlatKeysMap(keysMap);
            for (Map<String, Object> map : flatKeysMap) {

                // Coordinates (mandatory field)
                Map<String, Number> coordinates = (Map<String, Number>) map.get("coordinates");
                if (coordinates == null) continue;
                ArrayList<Number> coordinatesNumbers = new ArrayList<>();
                for (int c = 0; true; c++) {
                    String coordinateString = Integer.toString(c);
                    Number coordinateNumber = coordinates.get(coordinateString);
                    if (coordinateNumber == null) break;
                    coordinatesNumbers.add(coordinateNumber);
                }
                if (coordinatesNumbers.isEmpty()) continue;

                // Label
                String label = (String) map.get("label");

                // Scale
                Number scale = (Number) map.get("scale");
                if (scale == null) scale = 1.0;

                // FillColor
                Color fillColor = mapRGBAorStringToColor(map.get("fillColor"), Color.BLUE);

                // StrokeColor
                Color strokeColor = mapRGBAorStringToColor(map.get("strokeColor"), Color.RED);

                // OutsideColor
                Color outsideColor = Color.WHITE; // TODO: Custom color

                entries.add(new Entry(sourceJson.getSingleFileName(), label, coordinatesNumbers, scale, fillColor, strokeColor, outsideColor));
                // TODO: Allow multiple file sources for JSON
            }
            System.out.println("test!");
        }
    }

    private Color mapRGBAorStringToColor(Object colorObj, Color defaultColor) {
        try {
            if (colorObj == null) {
                return defaultColor;
            } else if (String.class.equals(colorObj.getClass())) {
                return Color.decode((String) colorObj);
            } else if (colorObj instanceof Map<?,?>) {
                Map<String, Number> colorObjMap = (Map<String, Number>) colorObj;
                Number red = colorObjMap.getOrDefault("Red", 0.0);
                Number green = colorObjMap.getOrDefault("Green", 0.0);
                Number blue = colorObjMap.getOrDefault("Blue", 0.0);
                Number alpha = colorObjMap.getOrDefault("Alpha", 0.0);
                return new Color(red.floatValue(), green.floatValue(), blue.floatValue(), alpha.floatValue());
            }
        } catch (Exception ignored) {}
        return defaultColor;
    }

    @Override
    public void writeToDB() throws SQLException {

    }

    private static class Entry {
        private final String filename;
        private final String label;
        private final List<Number> coordinates;
        private final Number scale;
        private final Color fillColor;
        private final Color strokeColor;
        private final Color outsideColor;

        private Entry(String filename, String label, List<Number> coordinates, Number scale, Color fillColor, Color strokeColor, Color outsideColor) {
            this.filename = filename;
            this.label = label;
            this.coordinates = coordinates;
            this.scale = scale;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.outsideColor = outsideColor;
        }
    }
}
