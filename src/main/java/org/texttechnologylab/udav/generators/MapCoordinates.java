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
    private List<Edge>  edges;   // optional — may be empty but never null after setup()

    public MapCoordinates(String id, JSONView configGenerator, JSONView configBundle,
                          GeneratorSettings settingsBundle, DBAccess dbAccess) {
        super(id, configGenerator, configBundle, settingsBundle, dbAccess);
    }


    @Override
    public void setup() throws SQLException {
        entries = new ArrayList<>();
        edges   = new ArrayList<>();

        if (SourceJson.class.equals(source.getClass())) {
            SourceJson sourceJson = (SourceJson) source;

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

            // ── Edge table (new, created only when edges exist) ───────────────
            if (!edges.isEmpty()) {
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
}