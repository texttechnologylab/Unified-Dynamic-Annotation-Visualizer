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
import java.util.Objects;

public class MapCoordinates extends Generator {

    private List<Entry> entries;
    private List<Edge>  edges;   // optional — may be empty but never null after setup()

    public MapCoordinates(String id, JSONView configGenerator, JSONView configBundle,
                          GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
    }


    @Override
    public void setup() throws SQLException {
        entries = new ArrayList<>();
        edges   = new ArrayList<>();
        double coordinateScale = readCoordinateScaleSetting();

        if (SourceJson.class.equals(source.getClass())) {
            SourceJson sourceJson = (SourceJson) source;

            String inputFormat = readStringSetting("inputFormat");
            if ("edgePairs".equalsIgnoreCase(inputFormat)) {
                setupFromEdgePairs(sourceJson, coordinateScale);
                return;
            }

            List<Map<String, Object>> keysMap = sourceJson.generateKeysMap(settings);
            for (Map<String, Object> map : keysMap) {

                // ── Vertex entries ────────────────────────────────────────────

                // Coordinates (mandatory field)
                Map<String, Number> coordinates = (Map<String, Number>) map.get("coordinates");
                if (coordinates == null) continue;
                ArrayList<Number> coordinatesNumbers = new ArrayList<>();
                for (int c = 0; true; c++) {
                    Number coordinateNumber = coordinates.get(Integer.toString(c));
                    if (coordinateNumber == null) break;
                    coordinatesNumbers.add(coordinateNumber.doubleValue() * coordinateScale);
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

                entries.add(new Entry(sourceJson.getSingleFileName(), label,
                        coordinatesNumbers, scale, fillColor, strokeColor, outsideColor));

                // ── Edges (optional, nested under each vertex entry) ──────────
                // Expected JSON shape (example):
                //   "edges": [
                //     { "to": 2, "number": 1.5, "color": "#ff0000", "label": "route A" },
                //     { "to": 5, "number": 0.8, "color": { "Red":1.0,"Green":0.5,"Blue":0.0,"Alpha":1.0 } }
                //   ]
                // "from" is implicitly the index of the current entry (entries.size() - 1 after the add above).
                List<Map<String, Object>> edgeList =
                        (List<Map<String, Object>>) map.get("edges");
                if (edgeList != null) {
                    int fromIndex = entries.size() - 1; // just added above
                    for (Map<String, Object> edgeMap : edgeList) {
                        Number toIndex = (Number) edgeMap.get("to");
                        if (toIndex == null) continue; // "to" is mandatory for an edge

                        Number  edgeNumber = (Number) edgeMap.get("number");
                        String  edgeLabel  = (String) edgeMap.get("label");
                        Color   edgeColor  = mapRGBAorStringToColor(edgeMap.get("color"), Color.GRAY);

                        edges.add(new Edge(sourceJson.getSingleFileName(),
                                fromIndex, toIndex.intValue(),
                                edgeNumber, edgeLabel, edgeColor));
                    }
                }
            }
        }
    }

    private void setupFromEdgePairs(SourceJson sourceJson, double coordinateScale) {
        double epsilon = readDoubleSetting("epsilon", 1e-6d);
        String filename = sourceJson.getSingleFileName();

        // Preferred path: same settings grammar as other generators (keysMap/keys/fixedKeys).
        List<Map<String, Object>> mappedEdges = sourceJson.generateKeysMapRowWise(settings);
        if (!mappedEdges.isEmpty()) {
            setupFromMappedEdgePairs(filename, mappedEdges, epsilon, coordinateScale);
            return;
        }

        // Backward-compatible fallback for raw edge arrays: [[{x,y},{x,y}], ...]
        Object node = sourceJson.getSingleFileJSONView().getNode();
        if (!(node instanceof List<?> edgePairs)) {
            return;
        }

        for (Object edgeObj : edgePairs) {
            if (!(edgeObj instanceof List<?> edgePair) || edgePair.size() < 2) {
                continue;
            }

            PointData fromPoint = scalePoint(readPoint(edgePair.get(0)), coordinateScale);
            PointData toPoint = scalePoint(readPoint(edgePair.get(1)), coordinateScale);
            if (fromPoint == null || toPoint == null) {
                continue;
            }

            int fromIndex = findOrAddVertex(filename, fromPoint, epsilon);
            int toIndex = findOrAddVertex(filename, toPoint, epsilon);
            if (fromIndex == toIndex) {
                continue;
            }

            edges.add(new Edge(filename, fromIndex, toIndex, null, null, Color.GRAY));
        }
    }

    private void setupFromMappedEdgePairs(String filename, List<Map<String, Object>> mappedEdges, double epsilon, double coordinateScale) {
        for (Map<String, Object> edgeMap : mappedEdges) {
            PointData fromPoint = scalePoint(readPointFromMapped(edgeMap.get("from")), coordinateScale);
            PointData toPoint = scalePoint(readPointFromMapped(edgeMap.get("to")), coordinateScale);
            if (fromPoint == null || toPoint == null) {
                continue;
            }

            int fromIndex = findOrAddVertex(filename, fromPoint, epsilon);
            int toIndex = findOrAddVertex(filename, toPoint, epsilon);
            if (fromIndex == toIndex) {
                continue;
            }

            Number edgeNumber = edgeMap.get("number") instanceof Number n ? n : null;
            String edgeLabel = edgeMap.get("label") instanceof String s ? s : null;
            Color edgeColor = mapRGBAorStringToColor(edgeMap.get("color"), Color.GRAY);
            edges.add(new Edge(filename, fromIndex, toIndex, edgeNumber, edgeLabel, edgeColor));
        }
    }

    private PointData readPointFromMapped(Object mappedPoint) {
        if (!(mappedPoint instanceof Map<?, ?> pointMap)) {
            return null;
        }
        Number xNumber = getNumber(pointMap, "0", "x", "X");
        Number yNumber = getNumber(pointMap, "1", "y", "Y");
        if (xNumber == null || yNumber == null) {
            return null;
        }
        return new PointData(xNumber.doubleValue(), yNumber.doubleValue());
    }

    private PointData scalePoint(PointData pointData, double coordinateScale) {
        if (pointData == null) return null;
        if (coordinateScale == 1.0d) return pointData;
        return new PointData(pointData.x * coordinateScale, pointData.y * coordinateScale);
    }

    private double readCoordinateScaleSetting() {
        try {
            Object value = configGenerator.get("settings").get("scale").getNode();
            if (value instanceof Number number) {
                double parsed = number.doubleValue();
                return Double.isFinite(parsed) ? parsed : 1.0d;
            }
            if (value instanceof String s) {
                double parsed = Double.parseDouble(s.trim());
                return Double.isFinite(parsed) ? parsed : 1.0d;
            }
        } catch (Exception ignored) {
            // Fall through to default.
        }
        return 1.0d;
    }

    private Number getNumber(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number number) {
                return number;
            }
        }
        return null;
    }

    private int findOrAddVertex(String filename, PointData point, double epsilon) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.coordinates == null || entry.coordinates.size() < 2) {
                continue;
            }
            double x = entry.coordinates.get(0).doubleValue();
            double y = entry.coordinates.get(1).doubleValue();
            if (Math.abs(x - point.x) <= epsilon && Math.abs(y - point.y) <= epsilon) {
                return i;
            }
        }

        List<Number> coordinates = List.of(point.x, point.y);
        entries.add(new Entry(filename, null, coordinates, null, Color.BLUE, Color.RED, Color.WHITE));
        return entries.size() - 1;
    }

    private PointData readPoint(Object rawPoint) {
        if (!(rawPoint instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Object xObj = rawMap.get("x");
        if (xObj == null) {
            xObj = rawMap.get("X");
        }
        Object yObj = rawMap.get("y");
        if (yObj == null) {
            yObj = rawMap.get("Y");
        }
        if (!(xObj instanceof Number xNumber) || !(yObj instanceof Number yNumber)) {
            return null;
        }
        return new PointData(xNumber.doubleValue(), yNumber.doubleValue());
    }

    private String readStringSetting(String key) {
        try {
            Object value = configGenerator.get("settings").get(key).getNode();
            return value == null ? null : Objects.toString(value, null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double readDoubleSetting(String key, double defaultValue) {
        try {
            Object value = configGenerator.get("settings").get(key).getNode();
            if (value instanceof Number number) {
                double parsed = number.doubleValue();
                return parsed > 0 ? parsed : defaultValue;
            }
            if (value instanceof String s) {
                double parsed = Double.parseDouble(s.trim());
                return parsed > 0 ? parsed : defaultValue;
            }
        } catch (Exception ignored) {
            // Fall through to default.
        }
        return defaultValue;
    }


    @Override
    public void writeToDB() throws SQLException {

        final String schema = dbAccess.getSchema();
        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // ── Vertex table (unchanged) ──────────────────────────────────────
            dsl.createTableIfNotExists(
                            DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES))
                    .column(DBConstants.TABLEATTR_GENERATORID,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_FILENAME,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_LABEL,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(true))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COORDINATES,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_SCALE,
                            org.jooq.impl.SQLDataType.DOUBLE.nullable(true))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_FILL,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_STROKE,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_OUTSIDE,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(true))
                    .execute();

            Table<?> VERTEX_TABLE =
                    DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES));

            Field<String> GENERATORID = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> FILENAME = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_FILENAME), String.class);
            Field<String> LABEL = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORDATA_LABEL), String.class);
            Field<String> COORDINATES = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORDATA_COORDINATES), String.class);
            Field<Double> SCALE = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORDATA_SCALE), Double.class);
            Field<String> COLOR_FILL = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORDATA_COLOR_FILL), String.class);
            Field<String> COLOR_STROKE = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORDATA_COLOR_STROKE), String.class);
            Field<String> COLOR_OUTSIDE = DSL.field(DSL.name(schema,
                    DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES,
                    DBConstants.TABLEATTR_GENERATORDATA_COLOR_OUTSIDE), String.class);

            List<Query> vertexBatch = new ArrayList<>();
            for (Entry e : entries) {
                String fillColorStr    = colorToHex(e.fillColor);
                String strokeColorStr  = colorToHex(e.strokeColor);
                String outsideColorStr = colorToHex(e.outsideColor);
                vertexBatch.add(
                        dsl.insertInto(VERTEX_TABLE)
                                .columns(GENERATORID, FILENAME, LABEL, COORDINATES,
                                        SCALE, COLOR_FILL, COLOR_STROKE, COLOR_OUTSIDE)
                                .values(id, e.filename, e.label,
                                        coordinatesListToString(e.coordinates),
                                        e.scale != null ? e.scale.doubleValue() : null,
                                        fillColorStr, strokeColorStr, outsideColorStr)
                );
            }
            if (!vertexBatch.isEmpty()) dsl.batch(vertexBatch).execute();

            // ── Edge table (always created; rows may be empty for point-only generators) ──
            dsl.createTableIfNotExists(
                            DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES))
                    .column(DBConstants.TABLEATTR_GENERATORID,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_FILENAME,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_EDGE_FROM,
                            org.jooq.impl.SQLDataType.INTEGER.nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_EDGE_TO,
                            org.jooq.impl.SQLDataType.INTEGER.nullable(false))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_EDGE_NUMBER,
                            org.jooq.impl.SQLDataType.DOUBLE.nullable(true))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_LABEL,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(true))
                    .column(DBConstants.TABLEATTR_GENERATORDATA_COLOR_FILL,
                            org.jooq.impl.SQLDataType.VARCHAR.length(DBConstants.DEFAULTSIZE_VARCHAR).nullable(false))
                    .execute();

            if (!edges.isEmpty()) {
                Table<?> EDGE_TABLE =
                        DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES));

                Field<String>  E_GENERATORID = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_GENERATORID), String.class);
                Field<String>  E_FILENAME    = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_FILENAME), String.class);
                Field<Integer> E_FROM        = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_GENERATORDATA_EDGE_FROM), Integer.class);
                Field<Integer> E_TO          = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_GENERATORDATA_EDGE_TO), Integer.class);
                Field<Double>  E_NUMBER      = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_GENERATORDATA_EDGE_NUMBER), Double.class);
                Field<String>  E_LABEL       = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_GENERATORDATA_LABEL), String.class);
                Field<String>  E_COLOR       = DSL.field(DSL.name(schema,
                        DBConstants.TABLENAME_GENERATORDATA_MAPCOORDINATES_EDGES,
                        DBConstants.TABLEATTR_GENERATORDATA_COLOR_FILL), String.class);

                List<Query> edgeBatch = new ArrayList<>();
                for (Edge e : edges) {
                    edgeBatch.add(
                            dsl.insertInto(EDGE_TABLE)
                                    .columns(E_GENERATORID, E_FILENAME, E_FROM, E_TO,
                                            E_NUMBER, E_LABEL, E_COLOR)
                                    .values(id, e.filename, e.fromIndex, e.toIndex,
                                            e.number != null ? e.number.doubleValue() : null,
                                            e.label, colorToHex(e.color))
                    );
                }
                dsl.batch(edgeBatch).execute();
            }
        }
    }


    private Color mapRGBAorStringToColor(Object colorObj, Color defaultColor) {
        try {
            if (colorObj == null) {
                return defaultColor;
            } else if (String.class.equals(colorObj.getClass())) {
                return Color.decode((String) colorObj);
            } else if (colorObj instanceof Map<?, ?>) {
                Map<String, Number> colorObjMap = (Map<String, Number>) colorObj;
                Number red   = colorObjMap.getOrDefault("Red",   0.0);
                Number green = colorObjMap.getOrDefault("Green", 0.0);
                Number blue  = colorObjMap.getOrDefault("Blue",  0.0);
                Number alpha = colorObjMap.getOrDefault("Alpha", 0.0);
                return new Color(red.floatValue(), green.floatValue(),
                        blue.floatValue(), alpha.floatValue());
            }
        } catch (Exception ignored) {}
        return defaultColor;
    }

    /** Converts a {@link Color} to a lowercase CSS hex string, e.g. {@code #1a2b3c}. */
    private static String colorToHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static String coordinatesListToString(List<Number> coordinates) {
        if (coordinates == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coordinates.size(); i++) {
            sb.append(coordinates.get(i));
            if (i < coordinates.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inner classes
    // ─────────────────────────────────────────────────────────────────────────

    private static class Entry {
        private final String       filename;
        private final String       label;
        private final List<Number> coordinates;
        private final Number       scale;
        private final Color        fillColor;
        private final Color        strokeColor;
        private final Color        outsideColor;

        private Entry(String filename, String label, List<Number> coordinates,
                      Number scale, Color fillColor, Color strokeColor, Color outsideColor) {
            this.filename     = filename;
            this.label        = label;
            this.coordinates  = coordinates;
            this.scale        = scale;
            this.fillColor    = fillColor;
            this.strokeColor  = strokeColor;
            this.outsideColor = outsideColor;
        }
    }

    /**
     * Describes a directed edge between two vertices identified by their
     * zero-based index in the {@code entries} list.
     *
     * <p>All fields except {@code fromIndex} and {@code toIndex} are optional.
     * If no edges are provided in the source, the edge list stays empty
     * and the edge DB table is never created, keeping full backward compatibility.
     */
    private static class Edge {
        /** Source vertex — zero-based index into the {@code entries} list. */
        private final int    fromIndex;
        /** Target vertex — zero-based index into the {@code entries} list. */
        private final int    toIndex;
        /** Optional numeric weight / distance / cost associated with the edge. */
        private final Number number;
        /** Optional human-readable label shown alongside the edge. */
        private final String label;
        /** Display color of the edge line; defaults to {@link Color#GRAY}. */
        private final Color  color;
        /** Source filename — kept for traceability in the DB. */
        private final String filename;

        private Edge(String filename, int fromIndex, int toIndex,
                     Number number, String label, Color color) {
            this.filename  = filename;
            this.fromIndex = fromIndex;
            this.toIndex   = toIndex;
            this.number    = number;
            this.label     = label;
            this.color     = color;
        }
    }

    private record PointData(double x, double y) {}
}