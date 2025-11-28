package org.texttechnologylab.udav.generators;

import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.SourceJson;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;


import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class MapCoordinates extends Generator {

    private List<Entry> entries;

    public MapCoordinates(String id, JSONView configGenerator, JSONView configBundle, GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
    }

    @Override
    public void setup() throws SQLException {
        if (SourceJson.class.equals(source.getClass())) {
            System.out.println("test!");
            Map<?, ?> keysMap = settings.getMapSettingOrDefault("keysMap", null);
            SourceJson.generateFlatKeysMap(null);
        }
    }

    @Override
    public void writeToDB() throws SQLException {

    }

    private static class Entry {
        private final String label;
        private final double[] coordinates;
        private final double scale;
        private final Color fillColor;
        private final Color strokeColor;
        private final Color outsideColor;

        private Entry(String label, double[] coordinates, double scale, Color fillColor, Color strokeColor, Color outsideColor) {
            this.label = label;
            this.coordinates = coordinates;
            this.scale = scale;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.outsideColor = outsideColor;
        }
    }
}
