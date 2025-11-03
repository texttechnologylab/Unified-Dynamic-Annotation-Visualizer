package org.texttechnologylab.udav.database;

public final class DBConstants {
    private DBConstants() {}

    public static final String TABLENAME_VISUALIZATIONJSONS = "VISUALIZATIONJSONS";
    public static final String TABLENAME_GENERATORTYPE = "GENERATORTYPE";

    // Generator: CategoryNumber(Color)Mapping
    public static final String TABLENAME_GENERATORDATA_CATEGORYNUMBER = "GENERATORDATA_CATEGORYNUMBER";
    public static final String TABLENAME_GENERATORDATA_CATEGORYCOLOR = "GENERATORDATA_CATEGORYCOLOR";

    // Generator: TextFormatting
    public static final String TABLENAME_GENERATORDATA_TYPECATEGORYCOLOR = "GENERATORDATA_TYPECATEGORYCOLOR";
    public static final String TABLENAME_GENERATORDATA_TEXT = "GENERATORDATA_TEXT";
    public static final String TABLENAME_GENERATORDATA_TYPESTYLE = "GENERATORDATA_TYPESTYLE";
    public static final String TABLENAME_GENERATORDATA_TYPESEGMENTS = "GENERATORDATA_TYPESEGMENTS";

    // General table attributes (also used in Generatordata tables)
    public static final String TABLEATTR_PIPELINEID = "PIPELINEID";
    public static final String TABLEATTR_JSONSTR = "JSONSTR";
    public static final String TABLEATTR_GENERATORID = "GENERATORID";
    public static final String TABLEATTR_FILENAME = "FILENAME";
    public static final String TABLEATTR_SOFA = "SOFA";

    // Specific Generatordata table attributes
    public static final String TABLEATTR_GENERATORDATA_CATEGORY = "CATEGORY";
    public static final String TABLEATTR_GENERATORDATA_NUMBER = "NUMBER";
    public static final String TABLEATTR_GENERATORDATA_COLOR = "COLOR";
    public static final String TABLEATTR_GENERATORDATA_TYPE = "COLUMNTYPE";
    public static final String TABLEATTR_GENERATORDATA_TEXT = "TEXT";
    public static final String TABLEATTR_GENERATORDATA_STYLE = "STYLE";
    public static final String TABLEATTR_GENERATORDATA_BEGIN = "BEGIN";
    public static final String TABLEATTR_GENERATORDATA_END = "_END";


    // Other, more technical attributes
    public static int DEFAULTSIZE_VARCHAR = 255;
}
