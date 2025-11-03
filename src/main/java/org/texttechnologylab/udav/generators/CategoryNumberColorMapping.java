package org.texttechnologylab.udav.generators;

import lombok.Getter;
import lombok.NonNull;
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


public class CategoryNumberColorMapping extends CategoryNumberMapping implements CategoryNumberColorMappingInterface {
    @Getter
    Map<String, Color> categoryColorMap;

    public CategoryNumberColorMapping(String id, Map<String, Map<String, Double>> categoryNumberMap, Map<String, Color> categoryColorMap) {
        super(id, categoryNumberMap);
        this.categoryColorMap = categoryColorMap;
    }

    public CategoryNumberColorMapping(String id, Map<String, Map<String, Double>> categoryNumberMap, Color fixedColor) {
        super(id, categoryNumberMap, fixedColor);
    }

    public CategoryNumberColorMapping(String id, Map<String, Map<String, Double>> categoryNumberMap) {
        super(id, categoryNumberMap);
        this.categoryColorMap = categoryColorMapFromCategoriesNumberMap(CategoryNumberMapping.calculateTotalFromCategoryCountMap(categoryNumberMap));
    }

    public CategoryNumberColorMapping(String id, CategoryNumberColorMapping copyOf) {
        super(id, copyOf);
        this.categoryColorMap = new HashMap<>();
        for (Map.Entry<String, Color> entry : copyOf.categoryColorMap.entrySet()) {
            this.categoryColorMap.put(entry.getKey(), new Color(entry.getValue().getRGB(), true));
        }
    }

    public static Map<String, Color> categoryColorMapFromCategoriesNumberMap(Map<String, Double> categoryNumberMap) {
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

    @Override
    public void multiplyByColor(@NonNull Color color) {

    }

    @Override
    public CategoryNumberColorMapping copy(String id) {
        return new CategoryNumberColorMapping(id, this);
    }

    @Override
    public void saveToDB(DBAccess dbAccess) throws SQLException {
        if (categoryNumberMap == null || categoryNumberMap.isEmpty()) return;
        if (fixedColor != null) {
            super.saveToDB(dbAccess);
        } else {
            saveCategoryNumberMapToDB(dbAccess);
            saveCategoryColorMapToDB(dbAccess);
        }
    }

    private void saveCategoryColorMapToDB(DBAccess dbAccess) throws SQLException {
        if (categoryColorMap == null || categoryColorMap.isEmpty()) return;

        final String schema = dbAccess.getSchema();

        try (Connection connection = dbAccess.getDataSource().getConnection()) {
            DSLContext dsl = DSL.using(connection);

            // Table reference
            Table<?> T = DSL.table(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR));

            // Column references
            Field<String> F_GEN = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR,
                    DBConstants.TABLEATTR_GENERATORID), String.class);
            Field<String> F_CAT = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR,
                    DBConstants.TABLEATTR_GENERATORDATA_CATEGORY), String.class);
            Field<String> F_COL = DSL.field(DSL.name(schema, DBConstants.TABLENAME_GENERATORDATA_CATEGORYCOLOR,
                    DBConstants.TABLEATTR_GENERATORDATA_COLOR), String.class);

            List<Query> batch = new ArrayList<>();

            for (Map.Entry<String, Color> entry : categoryColorMap.entrySet()) {
                String category = entry.getKey();
                Color colorObj = entry.getValue();
                String color = String.format("#%02x%02x%02x", colorObj.getRed(), colorObj.getGreen(), colorObj.getBlue());

                batch.add(
                        dsl.insertInto(T)
                                .columns(F_GEN, F_CAT, F_COL)
                                .values(id, category, color)
                );
            }

            if (!batch.isEmpty()) {
                dsl.batch(batch).execute();
            }
        }
    }
}
