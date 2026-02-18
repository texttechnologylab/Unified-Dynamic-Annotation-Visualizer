package org.texttechnologylab.udav.generators;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.generators.settings.GeneratorSettings;
import org.texttechnologylab.udav.generators.sources.SourceJson;
import org.texttechnologylab.udav.pipeline.JSONView;
import org.texttechnologylab.udav.sources.DBAccess;


import java.awt.*;
import java.sql.Connection;
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

            List<Map<String, Object>> keysMap = sourceJson.generateKeysMap(settings);
            for (Map<String, Object> map : keysMap) {

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

                // FillColor
                Color fillColor = mapRGBAorStringToColor(map.get("fillColor"), Color.BLUE);

                // StrokeColor
                Color strokeColor = mapRGBAorStringToColor(map.get("strokeColor"), Color.RED);

                // OutsideColor
                Color outsideColor = Color.WHITE; // TODO: Custom color

                entries.add(new Entry(sourceJson.getSingleFileName(), label, coordinatesNumbers, scale, fillColor, strokeColor, outsideColor));
                // TODO: Allow multiple file sources for JSON
            }
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

        final String schema = dbAccess.getSchema();
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            dsl.createTableIfNotExists(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES))
                    .column(DBConstants.TABLEATTR_GENERATORID, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_FILENAME, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_LABEL, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(true))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COORDINATES, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_SCALE, org.jooq.impl.SQLDataType.DOUBLE.nullable(true))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_FILL, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_STROKE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_OUTSIDE, org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(true))
                    .execute();


            // ---------- Table ----------
            Table<?> TABLE = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES));


            // ---------- Columns (schema-qualified & quoted) ----------
            Field<String> GENERATORID = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> FILENAME = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_FILENAME), String.class);
            Field<String> LABEL = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORDATA_LABEL), String.class);
            Field<String> COORDINATES = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORDATA_COORDINATES), String.class);
            Field<Double> SCALE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORDATA_SCALE), Double.class);
            Field<String> COLOR_FILL = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORDATA_COLOR_FILL), String.class);
            Field<String> COLOR_STROKE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORDATA_COLOR_STROKE), String.class);
            Field<String> COLOR_OUTSIDE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES, DBConstants.TABLEATTR_GENERATORDATA_COLOR_OUTSIDE), String.class);

            List<Query> batch = new ArrayList<>();
            for (Entry e : entries) {
                String fillColorStr = String.format("#%02x%02x%02x", e.fillColor.getRed(), e.fillColor.getGreen(), e.fillColor.getBlue());
                String strokeColorStr = String.format("#%02x%02x%02x", e.strokeColor.getRed(), e.strokeColor.getGreen(), e.strokeColor.getBlue());
                String outsideColorStr = String.format("#%02x%02x%02x", e.outsideColor.getRed(), e.outsideColor.getGreen(), e.outsideColor.getBlue());
                batch.add(
                        dsl.insertInto(TABLE)
                                .columns(GENERATORID, FILENAME, LABEL, COORDINATES, SCALE, COLOR_FILL, COLOR_STROKE, COLOR_OUTSIDE)
                                .values(id, e.filename, e.label, coordinatesListToString(e.coordinates), e.scale.doubleValue(), fillColorStr, strokeColorStr, outsideColorStr)
                );
            }
            if (!batch.isEmpty()) dsl.batch(batch).execute();
        }

    }

    public static String coordinatesListToString(List<Number> coordinates) {
        if (coordinates == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < coordinates.size(); i++) {
            sb.append(coordinates.get(i));
            if (i < coordinates.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
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
