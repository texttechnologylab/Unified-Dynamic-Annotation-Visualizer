package org.texttechnologylab.udav.generators;

import lombok.Getter;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.texttechnologylab.udav.database.DBConstants;
import org.texttechnologylab.udav.sources.DBAccess;

import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

@Getter
public class TextFormatting extends Generator implements TextFormattingInterface {

    public static final String DEFAULT_STYLE = "underline";

    private final String filename;
    private final String sofaID;
    private final String text;
    private final Collection<Dataset> datasets;


    public TextFormatting(String id, String filename, String sofaID, String text, Collection<Dataset> datasets) {
        super(id);
        this.filename = filename;
        this.sofaID = sofaID;
        this.text = text;
        this.datasets = datasets;
    }

    public TextFormatting(String id, TextFormatting copyOf) {
        super(id);
        this.filename = copyOf.filename;
        this.sofaID = copyOf.sofaID;
        this.text = copyOf.text;
        this.datasets = new ArrayList<>();
        for (Dataset dataset : copyOf.datasets) {
            this.datasets.add(new Dataset(dataset));
        }
    }

    @Override
    public TextFormatting copy(String id) {
        return new TextFormatting(id, this);
    }

    @Override
    public void saveToDB(DBAccess dbAccess) throws SQLException {
        final String schema = dbAccess.getSchema(); // or dbAccess.getSchema() if you expose it

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // ---------- Tables ----------
            Table<?> T_TEXT = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT));
            Table<?> T_STYLE = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE));
            Table<?> T_COLOR = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR));
            Table<?> T_SEGS = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS));

            // ---------- Columns (schema-qualified & quoted) ----------
            // TEXT
            Field<String> GID_TEXT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TXT_TEXT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TEXT, DBConstants.TABLEATTR_GENERATORDATA_TEXT), String.class);

            // TYPESTYLE
            Field<String> GID_STYLE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TYP_STYLE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE, DBConstants.TABLEATTR_GENERATORDATA_TYPE), String.class);
            Field<String> STY_STYLE = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESTYLE, DBConstants.TABLEATTR_GENERATORDATA_STYLE), String.class);

            // TYPECATEGORYCOLOR
            Field<String> GID_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TYP_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORDATA_TYPE), String.class);
            Field<String> CAT_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);
            Field<String> COL_COLOR = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR, DBConstants.TABLEATTR_GENERATORDATA_COLOR), String.class);

            // TYPESEGMENTS
            Field<String> GID_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> TYP_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_TYPE), String.class);
            Field<Integer> BEG_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_BEGIN), Integer.class);
            Field<Integer> END_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_END), Integer.class);
            Field<String> CAT_SEGS = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_TYPESEGMENTS, DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);

            // ---------- Insert text ----------
            dsl.insertInto(T_TEXT)
                    .columns(GID_TEXT, TXT_TEXT)
                    .values(id, text)
                    .execute();

            if (datasets == null || datasets.isEmpty()) return;

            for (Dataset ds : datasets) {
                // STYLE row
                dsl.insertInto(T_STYLE)
                        .columns(GID_STYLE, TYP_STYLE, STY_STYLE)
                        .values(id, ds.columnType, ds.style)
                        .execute();

                // COLORS batch
                List<Query> batch = new ArrayList<>();
                for (Map.Entry<String, Color> entry : ds.categoryColorMap.entrySet()) {
                    String category = entry.getKey();
                    Color c = entry.getValue();
                    String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                    batch.add(
                            dsl.insertInto(T_COLOR)
                                    .columns(GID_COLOR, TYP_COLOR, CAT_COLOR, COL_COLOR)
                                    .values(id, ds.columnType, category, hex)
                    );
                }
                if (!batch.isEmpty()) dsl.batch(batch).execute();
                batch.clear();

                // SEGMENTS batch
                for (Dataset.Segment s : ds.segments) {
                    batch.add(
                            dsl.insertInto(T_SEGS)
                                    .columns(GID_SEGS, TYP_SEGS, BEG_SEGS, END_SEGS, CAT_SEGS)
                                    .values(id, ds.columnType, s.begin, s.end, s.category)
                    );
                }
                if (!batch.isEmpty()) dsl.batch(batch).execute();
            }
        }
    }

    public static class Dataset {
        private final String columnType;
        private final String style;
        private final Map<String, Color> categoryColorMap;
        private final List<Segment> segments;

        public Dataset(String categoryName, String style, Map<String, Color> categoryColorMap, List<Segment> segments) {
            this.columnType = categoryName;
            this.style = style;
            this.categoryColorMap = categoryColorMap;
            this.segments = segments;
        }

        public Dataset(Dataset copyOf) {
            this.columnType = copyOf.columnType;
            this.style = copyOf.style;
            this.categoryColorMap = new HashMap<>(copyOf.categoryColorMap);
            this.segments = new ArrayList<>(copyOf.segments);
        }

        public record Segment(int begin, int end, String category) {
        }


    }
}
